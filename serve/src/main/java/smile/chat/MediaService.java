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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import io.vertx.ext.web.RoutingContext;
import smile.auth.AuthConfig;
import smile.auth.AuthContext;
import smile.chat.blob.BlobAuditEvent;
import smile.chat.blob.BlobAuditListener;
import smile.chat.blob.BlobContentScanner;
import smile.chat.blob.BlobKeys;
import smile.chat.blob.BlobNamespaceResolver;
import smile.chat.blob.BlobStore;
import smile.chat.blob.ClientIdentity;
import smile.chat.blob.ImageSafetyProcessor;
import smile.chat.blob.MediaTypeValidator;
import smile.chat.blob.MediaUrls;
import smile.chat.blob.UploadRateLimiter;
import smile.llm.ContentPart;
import smile.llm.ImageUrlPart;
import smile.llm.Message;
import smile.llm.VideoUrlPart;

/**
 * Upload, fetch, link, and resolve conversation multimedia blobs with
 * per-user OAuth namespace isolation, UTC date-grouped shared space,
 * magic-byte MIME validation, and ownership access control.
 *
 * @author Haifeng Li
 */
@ApplicationScoped
public class MediaService {
    private static final Logger logger = Logger.getLogger(MediaService.class);

    private final BlobStore blobStore;
    private final BlobStorageConfig config;
    private final AuthConfig authConfig;
    private final AuthContext authContext;
    private final ConversationService conversationService;
    private final BlobNamespaceResolver namespaceResolver;
    private final UploadRateLimiter uploadRateLimiter;
    private final List<BlobContentScanner> contentScanners;
    private final List<BlobAuditListener> auditListeners;
    private final Set<String> allowedMimeTypes;

    /**
     * @param blobStore           object storage backend.
     * @param config              upload limits, allowlist, quotas, and backend settings.
     * @param authConfig          authentication configuration (forwarded header trust).
     * @param authContext         request-scoped authenticated identity.
     * @param conversationService conversation ownership access checker.
     * @param namespaceResolver   storage namespace resolver (OAuth user vs. shared/date).
     * @param uploadRateLimiter   per-IP sliding-window rate limiter for guest uploads.
     * @param contentScanners     pluggable pre-storage malware/DLP scanners.
     * @param auditListeners      pluggable attachment security audit listeners.
     */
    @Inject
    public MediaService(BlobStore blobStore,
                        BlobStorageConfig config,
                        AuthConfig authConfig,
                        AuthContext authContext,
                        ConversationService conversationService,
                        BlobNamespaceResolver namespaceResolver,
                        UploadRateLimiter uploadRateLimiter,
                        @All List<BlobContentScanner> contentScanners,
                        @All List<BlobAuditListener> auditListeners) {
        this.blobStore = blobStore;
        this.config = config;
        this.authConfig = authConfig;
        this.authContext = authContext;
        this.conversationService = conversationService;
        this.namespaceResolver = namespaceResolver;
        this.uploadRateLimiter = uploadRateLimiter;
        this.contentScanners = contentScanners != null ? List.copyOf(contentScanners) : List.of();
        this.auditListeners = auditListeners != null ? List.copyOf(auditListeners) : List.of();
        this.allowedMimeTypes = MediaTypeValidator.parseAllowlist(config.allowedMimeTypes());
    }

    /**
     * Stores an uploaded file under the conversation and returns metadata.
     *
     * <p>OAuth-authenticated users (Google / Microsoft) are isolated into
     * {@code users/<provider>-<24hex>/<conversationId>/...}. Unauthenticated guests
     * and the localhost {@code me} account are grouped by UTC date under
     * {@code shared/<YYYY>/<MM>/<DD>/<conversationId>/...}.
     *
     * @param conversation   parent conversation (access already verified by caller).
     * @param upload         multipart file.
     * @param role           {@code user} or {@code assistant}.
     * @param routingContext request context (retained for API compatibility).
     * @param headers        HTTP headers (retained for API compatibility).
     * @return content API object.
     */
    @Transactional
    public ContentObject upload(Conversation conversation,
                                FileUpload upload,
                                String role,
                                RoutingContext routingContext,
                                HttpHeaders headers) {
        if (upload == null) {
            throw new BadRequestException("file is required");
        }
        String namespace = namespaceResolver.resolve(authContext);
        if (!authContext.isLoggedIn() && uploadRateLimiter != null) {
            boolean trustForwarded = authConfig != null && authConfig.trustForwardedHeaders();
            String clientIp = ClientIdentity.normalizeIp(
                    ClientIdentity.resolveClientIp(routingContext, headers, trustForwarded));
            UploadRateLimiter.Decision decision = uploadRateLimiter.tryAcquire(clientIp);
            if (!decision.allowed()) {
                emitAudit(BlobAuditEvent.Action.RATE_LIMITED, namespace, conversation.userId,
                        conversation.id, null, upload.contentType(), Math.max(0L, upload.size()),
                        null, false, "guest upload rate limit exceeded");
                throw new WebApplicationException(
                        Response.status(429)
                                .header("Retry-After", Long.toString(decision.retryAfterSeconds()))
                                .type(MediaType.APPLICATION_JSON)
                                .entity("{\"error\":\"Guest upload rate limit exceeded; retry later\"}")
                                .build());
            }
        }
        long maxBytes = config.maxUploadBytes();
        if (maxBytes <= 0) {
            throw new BadRequestException("Media uploads are disabled");
        }
        long declaredSize = upload.size();
        if (declaredSize > maxBytes) {
            throw new BadRequestException(
                    "file exceeds max upload size of " + maxBytes + " bytes");
        }

        byte[] data;
        try {
            Path path = upload.uploadedFile();
            if (path == null || !Files.isRegularFile(path)) {
                throw new BadRequestException("empty upload");
            }
            data = readBounded(path, maxBytes);
        } catch (BadRequestException e) {
            throw e;
        } catch (IOException e) {
            throw new BadRequestException("Failed to read upload", e);
        }

        String contentId = UUID.randomUUID().toString();
        String verifiedMime;
        try {
            verifiedMime = MediaTypeValidator.validate(upload.contentType(), data, allowedMimeTypes);
        } catch (IllegalArgumentException e) {
            emitAudit(BlobAuditEvent.Action.SCAN_REJECTED, namespace, conversation.userId,
                    conversation.id, contentId, upload.contentType(), data.length,
                    null, false, e.getMessage());
            throw new BadRequestException(e.getMessage());
        }

        try {
            data = ImageSafetyProcessor.validateAndSanitize(
                    verifiedMime,
                    data,
                    config.maxImagePixels(),
                    config.maxImageDimension(),
                    config.stripImageMetadata());
        } catch (IllegalArgumentException e) {
            emitAudit(BlobAuditEvent.Action.SCAN_REJECTED, namespace, conversation.userId,
                    conversation.id, contentId, verifiedMime, data.length,
                    null, false, e.getMessage());
            throw new BadRequestException(e.getMessage());
        }

        String safeFilename = MediaTypeValidator.sanitizeFilename(upload.fileName(), contentId);
        enforceQuota(namespace, conversation.userId, conversation.id, contentId, verifiedMime, data.length);
        runContentScanners(namespace, conversation.userId, conversation.id, contentId, safeFilename, verifiedMime, data);

        String storageKey = BlobKeys.of(namespace, conversation.id, null, contentId);

        String sha256 = sha256Hex(data);
        try {
            blobStore.put(storageKey, data, verifiedMime);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store blob", e);
        }

        ConversationContent content = new ConversationContent();
        content.id = contentId;
        content.conversationId = conversation.id;
        content.messageId = null;
        content.userId = namespace;
        content.ownerUserId = conversation.userId;
        content.role = (role == null || role.isBlank()) ? "user" : role.trim();
        content.mimeType = verifiedMime;
        content.filename = safeFilename;
        content.sizeBytes = data.length;
        content.sha256 = sha256;
        content.storageKey = storageKey;
        content.persist();

        emitAudit(BlobAuditEvent.Action.UPLOAD, namespace, conversation.userId,
                conversation.id, contentId, verifiedMime, data.length, sha256, true, null);
        logger.infof("Stored media contentId=%s conversationId=%d namespace=%s size=%d mime=%s",
                contentId, conversation.id, namespace, data.length, verifiedMime);
        return ContentObject.from(content);
    }

    /**
     * Loads blob bytes and metadata after verifying that the current request
     * caller owns the parent conversation.
     *
     * @param contentId opaque UUID.
     * @param clientIp  resolved request client IP (for guest conversations).
     * @return metadata and bytes.
     * @throws NotFoundException when the attachment does not exist or the caller is not authorized.
     */
    public MediaBytes getBytes(String contentId, String clientIp) {
        return getBytes(contentId, authContext.userId(), clientIp);
    }

    /**
     * Loads blob bytes and metadata after verifying that {@code callerUserId} /
     * {@code clientIp} owns the parent conversation.
     *
     * @param contentId    opaque UUID.
     * @param callerUserId authenticated user id, or {@code null} for guests.
     * @param clientIp     resolved request client IP (for guest conversations).
     * @return metadata and bytes.
     * @throws NotFoundException when the attachment does not exist or the caller is not authorized.
     */
    public MediaBytes getBytes(String contentId, Long callerUserId, String clientIp) {
        if (contentId == null || contentId.isBlank()) {
            throw new NotFoundException("Media not found");
        }
        ConversationContent meta = ConversationContent.findById(contentId);
        if (meta == null) {
            throw new NotFoundException("Media not found: " + contentId);
        }
        Conversation conversation = Conversation.findById(meta.conversationId);
        if (conversation == null) {
            throw new NotFoundException("Media not found: " + contentId);
        }
        try {
            conversationService.ensureAccess(conversation, callerUserId, clientIp);
        } catch (NotFoundException e) {
            emitAudit(BlobAuditEvent.Action.ACCESS_DENIED, meta.userId, callerUserId,
                    meta.conversationId, contentId, meta.mimeType, meta.sizeBytes, meta.sha256,
                    false, "caller does not own parent conversation");
            throw e;
        }
        try {
            Optional<byte[]> bytes = blobStore.get(meta.storageKey);
            if (bytes.isEmpty()) {
                throw new NotFoundException("Media blob missing: " + contentId);
            }
            byte[] payload = bytes.get();
            if (config.verifyChecksumOnRead() && meta.sha256 != null && !meta.sha256.isBlank()) {
                String actualSha256 = sha256Hex(payload);
                if (!MessageDigest.isEqual(
                        actualSha256.getBytes(StandardCharsets.US_ASCII),
                        meta.sha256.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII))) {
                    logger.errorf("SECURITY ALERT: Blob SHA-256 mismatch contentId=%s key=%s expected=%s actual=%s",
                            contentId, meta.storageKey, meta.sha256, actualSha256);
                    emitAudit(BlobAuditEvent.Action.INTEGRITY_FAILURE, meta.userId, callerUserId,
                            meta.conversationId, contentId, meta.mimeType, payload.length, actualSha256,
                            false, "SHA-256 checksum mismatch");
                    throw new IllegalStateException("Blob integrity verification failed for " + contentId);
                }
            }
            emitAudit(BlobAuditEvent.Action.DOWNLOAD, meta.userId, callerUserId,
                    meta.conversationId, contentId, meta.mimeType, payload.length, meta.sha256,
                    true, null);
            return new MediaBytes(meta, payload);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read blob " + contentId, e);
        }
    }

    /**
     * Links previously uploaded content rows to a persisted message.
     *
     * @param conversationId conversation primary key.
     * @param messageId      message primary key.
     * @param contentIds     content ids referenced by the message.
     */
    @Transactional
    public void linkToMessage(long conversationId, long messageId, List<String> contentIds) {
        if (contentIds == null || contentIds.isEmpty()) {
            return;
        }
        for (String contentId : contentIds) {
            ConversationContent content = ConversationContent.findById(contentId);
            if (content == null) {
                continue;
            }
            if (!conversationIdEquals(content.conversationId, conversationId)) {
                logger.warnf("Skipping contentId=%s: conversation mismatch", contentId);
                continue;
            }
            content.messageId = messageId;
        }
    }

    /**
     * Deletes all content metadata and blobs for a conversation across all
     * storage namespaces (e.g., if the conversation spanned multiple UTC days or
     * was merged from guest to OAuth).
     *
     * @param conversationId conversation primary key.
     */
    @Transactional
    public void deleteConversationMedia(long conversationId) {
        List<ConversationContent> rows = ConversationContent
                .list("conversationId", conversationId);
        Set<String> prefixes = new LinkedHashSet<>();
        for (ConversationContent row : rows) {
            if (row.userId != null && !row.userId.isBlank()) {
                prefixes.add(BlobKeys.conversationPrefix(row.userId, conversationId));
            }
            try {
                blobStore.delete(row.storageKey);
            } catch (IOException e) {
                logger.warnf(e, "Failed to delete blob key=%s", row.storageKey);
            }
            emitAudit(BlobAuditEvent.Action.DELETE, row.userId, row.ownerUserId,
                    row.conversationId, row.id, row.mimeType, row.sizeBytes, row.sha256,
                    true, null);
            row.delete();
        }
        for (String prefix : prefixes) {
            try {
                blobStore.deletePrefix(prefix);
            } catch (IOException e) {
                logger.warnf(e, "Failed to delete blob prefix=%s for conversation %d",
                        prefix, conversationId);
            }
        }
    }

    /**
     * Rewrites internal {@code /api/v1/media/...} URLs to {@code data:} URLs
     * after verifying that the caller owns every referenced attachment.
     *
     * @param messages     request messages.
     * @param callerUserId authenticated user id captured on the HTTP thread, or {@code null}.
     * @param clientIp     client IP captured on the HTTP thread.
     * @return messages with resolved media parts (same array when unchanged).
     */
    public Message[] resolveInternalMedia(Message[] messages, Long callerUserId, String clientIp) {
        if (messages == null || messages.length == 0) {
            return messages;
        }
        Message[] out = new Message[messages.length];
        boolean changed = false;
        for (int i = 0; i < messages.length; i++) {
            Message m = messages[i];
            if (m == null || !m.hasMedia()) {
                out[i] = m;
                continue;
            }
            List<ContentPart> parts = new java.util.ArrayList<>(m.parts().size());
            boolean partChanged = false;
            for (ContentPart part : m.parts()) {
                if (part instanceof ImageUrlPart image) {
                    Optional<String> dataUrl = toDataUrl(image.url(), callerUserId, clientIp);
                    if (dataUrl.isPresent()) {
                        parts.add(new ImageUrlPart(dataUrl.get()));
                        partChanged = true;
                    } else {
                        parts.add(part);
                    }
                } else if (part instanceof VideoUrlPart video) {
                    Optional<String> dataUrl = toDataUrl(video.url(), callerUserId, clientIp);
                    if (dataUrl.isPresent()) {
                        parts.add(new VideoUrlPart(dataUrl.get(), video.fps()));
                        partChanged = true;
                    } else {
                        parts.add(part);
                    }
                } else {
                    parts.add(part);
                }
            }
            if (partChanged) {
                out[i] = m.withParts(parts);
                changed = true;
            } else {
                out[i] = m;
            }
        }
        return changed ? out : messages;
    }

    /**
     * Enforces per-OAuth-user and daily shared-space storage quotas prior to
     * writing bytes to the underlying {@link BlobStore}.
     *
     * @param namespace     resolved storage namespace.
     * @param ownerUserId   owning user primary key (may be {@code null} for guests).
     * @param incomingBytes size of the incoming attachment in bytes.
     */
    void enforceQuota(String namespace, Long ownerUserId, long incomingBytes) {
        enforceQuota(namespace, ownerUserId, null, null, null, incomingBytes);
    }

    private void enforceQuota(String namespace,
                              Long ownerUserId,
                              Long conversationId,
                              String contentId,
                              String mimeType,
                              long incomingBytes) {
        if (namespace.startsWith(BlobNamespaceResolver.USERS_PREFIX) && ownerUserId != null) {
            long limit = config.maxUserBytes();
            if (limit > 0) {
                long used = userStorageBytes(ownerUserId);
                if (used + incomingBytes > limit) {
                    emitAudit(BlobAuditEvent.Action.QUOTA_EXCEEDED, namespace, ownerUserId,
                            conversationId, contentId, mimeType, incomingBytes, null, false,
                            "Per-user attachment storage quota exceeded (" + limit + " bytes)");
                    throw new WebApplicationException(
                            Response.status(413)
                                    .type(MediaType.APPLICATION_JSON)
                                    .entity("{\"error\":\"Per-user attachment storage quota exceeded ("
                                            + limit + " bytes)\"}")
                                    .build());
                }
            }
        } else if (namespace.startsWith(BlobNamespaceResolver.SHARED_PREFIX)) {
            long limit = config.sharedMaxDailyBytes();
            if (limit > 0) {
                long used = namespaceStorageBytes(namespace);
                if (used + incomingBytes > limit) {
                    emitAudit(BlobAuditEvent.Action.QUOTA_EXCEEDED, namespace, ownerUserId,
                            conversationId, contentId, mimeType, incomingBytes, null, false,
                            "Daily shared attachment storage quota exceeded (" + limit + " bytes)");
                    throw new WebApplicationException(
                            Response.status(413)
                                    .type(MediaType.APPLICATION_JSON)
                                    .entity("{\"error\":\"Daily shared attachment storage quota exceeded ("
                                            + limit + " bytes)\"}")
                                    .build());
                }
            }
        }
    }

    private void runContentScanners(String namespace,
                                    Long ownerUserId,
                                    long conversationId,
                                    String contentId,
                                    String filename,
                                    String mimeType,
                                    byte[] data) {
        if (contentScanners == null || contentScanners.isEmpty()) {
            return;
        }
        BlobContentScanner.ScanContext ctx = new BlobContentScanner.ScanContext(
                namespace, ownerUserId, conversationId, filename, mimeType, data);
        for (BlobContentScanner scanner : contentScanners) {
            if (scanner == null) {
                continue;
            }
            BlobContentScanner.ScanResult result;
            try {
                result = scanner.scan(ctx);
            } catch (IOException e) {
                logger.warnf(e, "Content scanner %s unavailable; failing closed for contentId=%s",
                        scanner.getClass().getSimpleName(), contentId);
                emitAudit(BlobAuditEvent.Action.SCAN_REJECTED, namespace, ownerUserId,
                        conversationId, contentId, mimeType, data.length, null, false,
                        "SCANNER_UNAVAILABLE: " + scanner.getClass().getSimpleName());
                throw new WebApplicationException(
                        Response.status(503)
                                .type(MediaType.APPLICATION_JSON)
                                .entity("{\"error\":\"Attachment security scanner unavailable\"}")
                                .build());
            }
            if (result == null || !result.clean()) {
                String verdict = result != null ? result.verdict() : "REJECTED";
                String reason = (result != null && result.reason() != null && !result.reason().isBlank())
                        ? result.reason()
                        : "Attachment rejected by security scanner";
                logger.warnf("Content scanner rejected upload contentId=%s verdict=%s reason=%s",
                        contentId, verdict, reason);
                emitAudit(BlobAuditEvent.Action.SCAN_REJECTED, namespace, ownerUserId,
                        conversationId, contentId, mimeType, data.length, null, false,
                        verdict + ": " + reason);
                throw new WebApplicationException(
                        Response.status(422)
                                .type(MediaType.APPLICATION_JSON)
                                .entity("{\"error\":\"" + escapeJson(reason)
                                        + "\",\"verdict\":\"" + escapeJson(verdict) + "\"}")
                                .build());
            }
        }
    }

    private void emitAudit(BlobAuditEvent.Action action,
                           String namespace,
                           Long ownerUserId,
                           Long conversationId,
                           String contentId,
                           String mimeType,
                           long sizeBytes,
                           String sha256,
                           boolean success,
                           String reason) {
        if (auditListeners == null || auditListeners.isEmpty()) {
            return;
        }
        BlobAuditEvent event = new BlobAuditEvent(
                Instant.now(),
                action,
                namespace,
                ownerUserId,
                conversationId,
                contentId,
                mimeType,
                Math.max(0L, sizeBytes),
                sha256,
                success,
                reason);
        for (BlobAuditListener listener : auditListeners) {
            if (listener == null) {
                continue;
            }
            try {
                listener.onBlobEvent(event);
            } catch (Throwable t) {
                logger.warnf(t, "BlobAuditListener %s threw exception", listener.getClass().getSimpleName());
            }
        }
    }

    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", " ")
                .replace("\n", " ");
    }

    /**
     * Returns total stored attachment bytes currently owned by {@code ownerUserId}.
     *
     * @param ownerUserId user primary key.
     * @return sum of {@code sizeBytes}.
     */
    public long userStorageBytes(long ownerUserId) {
        Number sum = ConversationContent.getEntityManager()
                .createQuery(
                        "SELECT COALESCE(SUM(c.sizeBytes), 0) FROM ConversationContent c WHERE c.ownerUserId = :uid",
                        Number.class)
                .setParameter("uid", ownerUserId)
                .getSingleResult();
        return sum != null ? sum.longValue() : 0L;
    }

    /**
     * Returns total stored attachment bytes currently stored under {@code namespace}.
     *
     * @param namespace storage namespace (e.g. {@code shared/2026/11/19}).
     * @return sum of {@code sizeBytes}.
     */
    public long namespaceStorageBytes(String namespace) {
        Number sum = ConversationContent.getEntityManager()
                .createQuery(
                        "SELECT COALESCE(SUM(c.sizeBytes), 0) FROM ConversationContent c WHERE c.userId = :ns",
                        Number.class)
                .setParameter("ns", namespace)
                .getSingleResult();
        return sum != null ? sum.longValue() : 0L;
    }

    private Optional<String> toDataUrl(String url, Long callerUserId, String clientIp) {
        Optional<String> contentId = MediaUrls.parseContentId(url);
        if (contentId.isEmpty()) {
            return Optional.empty();
        }
        MediaBytes media = getBytes(contentId.get(), callerUserId, clientIp);
        String mime = media.meta().mimeType;
        if (mime == null || mime.isBlank()) {
            mime = "application/octet-stream";
        }
        String encoded = Base64.getEncoder().encodeToString(media.bytes());
        return Optional.of("data:" + mime + ";base64," + encoded);
    }

    private static byte[] readBounded(Path path, long maxBytes) throws IOException {
        long size = Files.size(path);
        if (size > maxBytes) {
            throw new BadRequestException(
                    "file exceeds max upload size of " + maxBytes + " bytes");
        }
        try (InputStream in = Files.newInputStream(path)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.min(Math.max(size, 0), 65536));
            byte[] buf = new byte[8192];
            long total = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > maxBytes) {
                    throw new BadRequestException(
                            "file exceeds max upload size of " + maxBytes + " bytes");
                }
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    private static boolean conversationIdEquals(Long a, long b) {
        return a != null && a == b;
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Loaded media payload.
     *
     * @param meta  metadata row.
     * @param bytes object bytes.
     */
    public record MediaBytes(ConversationContent meta, byte[] bytes) {}
}
