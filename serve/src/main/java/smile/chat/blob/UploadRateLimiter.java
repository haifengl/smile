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
package smile.chat.blob;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.arc.profile.UnlessBuildProfile;
import smile.chat.BlobStorageConfig;

/**
 * In-memory per-IP sliding-window rate limiter for unauthenticated guest
 * attachment uploads.
 *
 * <p>Bounded to {@value #DEFAULT_MAX_TRACKED_KEYS} distinct client keys so IP
 * rotation cannot exhaust JVM heap.
 *
 * @author Haifeng Li
 */
@UnlessBuildProfile("micro")
@ApplicationScoped
public class UploadRateLimiter {
    static final long WINDOW_MILLIS = 60_000L;
    static final int DEFAULT_MAX_TRACKED_KEYS = 10_000;

    private final int maxPerMinute;
    private final Clock clock;
    private final int maxTrackedKeys;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    /**
     * @param config blob storage configuration.
     */
    @Inject
    public UploadRateLimiter(BlobStorageConfig config) {
        this(config.guestUploadsPerMinute(), Clock.systemUTC(), DEFAULT_MAX_TRACKED_KEYS);
    }

    /**
     * Testable constructor with injectable limit, clock, and map bound.
     *
     * @param maxPerMinute   maximum uploads per 60-second window ({@code <= 0} disables).
     * @param clock          time source.
     * @param maxTrackedKeys maximum distinct client keys held concurrently.
     */
    public UploadRateLimiter(int maxPerMinute, Clock clock, int maxTrackedKeys) {
        this.maxPerMinute = maxPerMinute;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.maxTrackedKeys = Math.max(1, maxTrackedKeys);
    }

    /**
     * Outcome of a rate-limit admission check.
     *
     * @param allowed           {@code true} when the upload is permitted.
     * @param retryAfterSeconds whole seconds until the next slot opens ({@code 0} when allowed).
     */
    public record Decision(boolean allowed, long retryAfterSeconds) {
        public static final Decision ALLOWED = new Decision(true, 0L);
    }

    /**
     * Attempts to consume one upload slot for {@code clientKey}.
     *
     * @param clientKey normalized client IP or key.
     * @return admission decision.
     */
    public Decision tryAcquire(String clientKey) {
        if (maxPerMinute <= 0) {
            return Decision.ALLOWED;
        }
        String key = (clientKey == null || clientKey.isBlank()) ? "unknown" : clientKey;
        long now = clock.millis();

        if (!windows.containsKey(key) && windows.size() >= maxTrackedKeys) {
            evictStaleOrOldest(now);
        }

        Window window = windows.computeIfAbsent(key, k -> new Window());
        synchronized (window) {
            long cutoff = now - WINDOW_MILLIS;
            while (!window.timestamps.isEmpty() && window.timestamps.peekFirst() <= cutoff) {
                window.timestamps.pollFirst();
            }
            if (window.timestamps.size() >= maxPerMinute) {
                long oldest = window.timestamps.peekFirst();
                long waitMs = Math.max(1_000L, (oldest + WINDOW_MILLIS) - now);
                long retryAfter = (waitMs + 999L) / 1_000L;
                return new Decision(false, retryAfter);
            }
            window.timestamps.addLast(now);
            window.lastSeenMillis = now;
            return Decision.ALLOWED;
        }
    }

    /** Clears all tracked rate-limit state (intended for tests). */
    public void reset() {
        windows.clear();
    }

    /** @return number of currently tracked client keys. */
    int trackedKeyCount() {
        return windows.size();
    }

    private void evictStaleOrOldest(long now) {
        long cutoff = now - WINDOW_MILLIS;
        String oldestKey = null;
        long oldestSeen = Long.MAX_VALUE;
        for (Map.Entry<String, Window> entry : windows.entrySet()) {
            Window w = entry.getValue();
            long seen;
            boolean empty;
            synchronized (w) {
                while (!w.timestamps.isEmpty() && w.timestamps.peekFirst() <= cutoff) {
                    w.timestamps.pollFirst();
                }
                empty = w.timestamps.isEmpty();
                seen = w.lastSeenMillis;
            }
            if (empty) {
                windows.remove(entry.getKey(), w);
            } else if (seen < oldestSeen) {
                oldestSeen = seen;
                oldestKey = entry.getKey();
            }
        }
        if (windows.size() >= maxTrackedKeys && oldestKey != null) {
            windows.remove(oldestKey);
        }
    }

    private static final class Window {
        final Deque<Long> timestamps = new ArrayDeque<>();
        long lastSeenMillis;
    }
}
