/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * SMILE Serve is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with SMILE Serve. If not, see
 * <https://www.gnu.org/licenses/>.
 */
package smile.chat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import io.quarkus.arc.profile.UnlessBuildProfile;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.StartupEvent;
import org.jboss.logging.Logger;
import smile.chat.blob.BlobNamespaceResolver;
import smile.chat.blob.BlobStore;
import smile.chat.blob.ConfigurableBlobStore;
import smile.chat.blob.EncryptingBlobStore;
import smile.chat.blob.LocalBlobStore;

/**
 * Scheduled retention and orphan-upload garbage collector for multimedia attachments.
 *
 * <ul>
 *   <li>Purges unlinked {@code pending/} uploads ({@code messageId IS NULL}) older than
 *       {@link BlobStorageConfig#pendingTtlHours()} (default 24 hours).</li>
 *   <li>Purges {@code shared/YYYY/MM/DD/} attachments (unauthenticated guests and
 *       localhost {@code me}) older than {@link BlobStorageConfig#sharedRetentionDays()}
 *       (default 7 days), including empty or orphaned local date folders.</li>
 * </ul>
 *
 * <p>OAuth-authenticated user attachments ({@code users/<provider>-<24hex>/...}) are
 * never deleted by the shared retention rule (only unlinked {@code pending/} uploads
 * that were never attached to a sent message are eligible for orphan cleanup).
 *
 * @author Haifeng Li
 */
@UnlessBuildProfile("micro")
@ApplicationScoped
public class BlobRetentionSweeper {
    private static final Logger logger = Logger.getLogger(BlobRetentionSweeper.class);

    private final BlobStore blobStore;
    private final BlobStorageConfig config;
    private volatile ScheduledExecutorService scheduler;

    /**
     * Summary of a single retention sweep execution.
     *
     * @param pendingDeleted              unlinked pending uploads deleted.
     * @param sharedDeleted               expired shared-space attachment rows deleted.
     * @param expiredSharedPrefixesCleaned expired {@code shared/YYYY/MM/DD/} prefixes purged.
     * @param bytesReclaimed              total DB-tracked attachment bytes reclaimed.
     */
    public record SweepResult(
            int pendingDeleted,
            int sharedDeleted,
            int expiredSharedPrefixesCleaned,
            long bytesReclaimed
    ) {}

    /**
     * @param blobStore active blob storage backend.
     * @param config    retention and sweep schedule settings.
     */
    @Inject
    public BlobRetentionSweeper(BlobStore blobStore, BlobStorageConfig config) {
        this.blobStore = blobStore;
        this.config = config;
    }

    void onStart(@Observes StartupEvent event) {
        int intervalMinutes = config.sweepIntervalMinutes();
        if (intervalMinutes <= 0) {
            logger.info("Blob retention background sweeper disabled (smile.blob.sweep-interval-minutes <= 0)");
            return;
        }
        if (config.pendingTtlHours() <= 0 && config.sharedRetentionDays() <= 0) {
            logger.info("Blob retention background sweeper disabled (both pending TTL and shared retention <= 0)");
            return;
        }
        ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "smile-blob-sweeper");
            t.setDaemon(true);
            return t;
        });
        this.scheduler = exec;
        exec.scheduleWithFixedDelay(this::runScheduledSweep, intervalMinutes, intervalMinutes, TimeUnit.MINUTES);
        logger.infof("Blob retention sweeper scheduled every %d min (pendingTtl=%dh, sharedRetention=%dd)",
                intervalMinutes, config.pendingTtlHours(), config.sharedRetentionDays());
    }

    @PreDestroy
    void onStop() {
        ScheduledExecutorService exec = this.scheduler;
        this.scheduler = null;
        if (exec != null) {
            exec.shutdownNow();
        }
    }

    private void runScheduledSweep() {
        try {
            SweepResult result = sweepNow(Instant.now());
            if (result.pendingDeleted() > 0 || result.sharedDeleted() > 0
                    || result.expiredSharedPrefixesCleaned() > 0) {
                logger.infof("Blob retention sweep completed: pendingDeleted=%d sharedDeleted=%d "
                                + "expiredPrefixes=%d bytesReclaimed=%d",
                        result.pendingDeleted(), result.sharedDeleted(),
                        result.expiredSharedPrefixesCleaned(), result.bytesReclaimed());
            }
        } catch (Throwable t) {
            logger.warn("Blob retention sweep failed", t);
        }
    }

    /**
     * Executes a single retention and orphan-cleanup pass as of {@code now}.
     *
     * @param now reference UTC timestamp.
     * @return summary of reclaimed objects and bytes.
     */
    public SweepResult sweepNow(Instant now) {
        Instant ref = now != null ? now : Instant.now();
        return QuarkusTransaction.requiringNew().call(() -> sweepInTransaction(ref));
    }

    private SweepResult sweepInTransaction(Instant now) {
        int pendingDeleted = 0;
        int sharedDeleted = 0;
        int prefixesCleaned = 0;
        long bytesReclaimed = 0L;

        int pendingTtlHours = config.pendingTtlHours();
        if (pendingTtlHours > 0) {
            Instant pendingCutoff = now.minus(pendingTtlHours, ChronoUnit.HOURS);
            List<ConversationContent> orphans = ConversationContent.list(
                    "messageId is null and createdAt < ?1", pendingCutoff);
            for (ConversationContent row : orphans) {
                deleteBlobQuietly(row.storageKey);
                bytesReclaimed += Math.max(0L, row.sizeBytes);
                row.delete();
                pendingDeleted++;
            }
        }

        int sharedRetentionDays = config.sharedRetentionDays();
        if (sharedRetentionDays > 0) {
            Instant sharedCutoff = now.minus(sharedRetentionDays, ChronoUnit.DAYS);
            Set<String> expiredDateNamespaces = new LinkedHashSet<>();

            List<ConversationContent> expiredShared = ConversationContent.list(
                    "userId like ?1 and createdAt < ?2",
                    BlobNamespaceResolver.SHARED_PREFIX + "%", sharedCutoff);
            for (ConversationContent row : expiredShared) {
                deleteBlobQuietly(row.storageKey);
                bytesReclaimed += Math.max(0L, row.sizeBytes);
                parseSharedDateNamespace(row.userId).ifPresent(date -> {
                    if (isWholeUtcDayBefore(date, sharedCutoff)) {
                        expiredDateNamespaces.add(row.userId);
                    }
                });
                row.delete();
                sharedDeleted++;
            }

            // Also discover any orphaned local shared/YYYY/MM/DD directories older than cutoff
            discoverExpiredLocalSharedNamespaces(sharedCutoff, expiredDateNamespaces);

            // Flush deletions so count queries reflect removed rows accurately
            ConversationContent.flush();

            for (String ns : expiredDateNamespaces) {
                long remaining = ConversationContent.count("userId = ?1", ns);
                if (remaining == 0L) {
                    try {
                        blobStore.deletePrefix(ns + "/");
                        prefixesCleaned++;
                    } catch (IOException e) {
                        logger.warnf(e, "Failed to purge expired shared prefix %s/", ns);
                    }
                }
            }
        }

        return new SweepResult(pendingDeleted, sharedDeleted, prefixesCleaned, bytesReclaimed);
    }

    private void deleteBlobQuietly(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        try {
            blobStore.delete(storageKey);
        } catch (IOException e) {
            logger.warnf(e, "Failed to delete expired blob key=%s", storageKey);
        }
    }

    private void discoverExpiredLocalSharedNamespaces(Instant cutoff, Set<String> out) {
        LocalBlobStore local = unwrapLocal(blobStore);
        if (local == null) {
            return;
        }
        Path sharedRoot = local.root().resolve("shared");
        if (!Files.isDirectory(sharedRoot, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (Stream<Path> years = Files.list(sharedRoot)) {
            for (Path yearDir : years.toList()) {
                if (!Files.isDirectory(yearDir, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                String y = yearDir.getFileName().toString();
                if (!y.matches("\\d{4}")) {
                    continue;
                }
                try (Stream<Path> months = Files.list(yearDir)) {
                    for (Path monthDir : months.toList()) {
                        if (!Files.isDirectory(monthDir, LinkOption.NOFOLLOW_LINKS)) {
                            continue;
                        }
                        String m = monthDir.getFileName().toString();
                        if (!m.matches("\\d{2}")) {
                            continue;
                        }
                        try (Stream<Path> days = Files.list(monthDir)) {
                            for (Path dayDir : days.toList()) {
                                if (!Files.isDirectory(dayDir, LinkOption.NOFOLLOW_LINKS)) {
                                    continue;
                                }
                                String d = dayDir.getFileName().toString();
                                String ns = "shared/" + y + "/" + m + "/" + d;
                                parseSharedDateNamespace(ns).ifPresent(date -> {
                                    if (isWholeUtcDayBefore(date, cutoff)) {
                                        out.add(ns);
                                    }
                                });
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            logger.warn("Failed scanning local shared blob directories for expiration", e);
        }
    }

    private static LocalBlobStore unwrapLocal(BlobStore store) {
        BlobStore current = store;
        for (int depth = 0; depth < 8 && current != null; depth++) {
            if (current instanceof LocalBlobStore local) {
                return local;
            }
            if (current instanceof ConfigurableBlobStore configurable) {
                current = configurable.delegate();
            } else if (current instanceof EncryptingBlobStore encrypting) {
                current = encrypting.delegate();
            } else {
                return null;
            }
        }
        return null;
    }

    /**
     * Parses a {@code shared/YYYY/MM/DD} namespace string into a UTC {@link LocalDate}.
     */
    static Optional<LocalDate> parseSharedDateNamespace(String namespace) {
        if (namespace == null || !namespace.startsWith(BlobNamespaceResolver.SHARED_PREFIX)) {
            return Optional.empty();
        }
        String[] parts = namespace.split("/");
        if (parts.length != 4 || !"shared".equals(parts[0])) {
            return Optional.empty();
        }
        try {
            int year = Integer.parseInt(parts[1]);
            int month = Integer.parseInt(parts[2]);
            int day = Integer.parseInt(parts[3]);
            return Optional.of(LocalDate.of(year, month, day));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    static boolean isWholeUtcDayBefore(LocalDate utcDate, Instant cutoff) {
        Instant endOfDay = utcDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return !endOfDay.isAfter(cutoff);
    }
}
