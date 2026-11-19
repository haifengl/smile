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
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.HttpHeaders;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import io.vertx.ext.web.RoutingContext;
import smile.auth.AuthContext;
import smile.chat.blob.BlobKeys;
import smile.chat.blob.BlobNamespaceResolver;
import smile.chat.blob.BlobStore;
import smile.chat.blob.MediaTypeValidator;
import smile.chat.blob.MediaUrls;
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
    private final AuthContext authContext;
    private final ConversationService conversationService;
    private final BlobNamespaceResolver namespaceResolver;
    private final Set<String> allowedMimeTypes;

    /**
     * @param blobStore           object storage backend.
     * @param config              upload limits, allowlist, and backend settings.
     * @param authContext         request-scoped authenticated identity.
     * @param conversationService conversation ownership access checker.
     * @param namespaceResolver   storage namespace resolver (OAuth user vs. shared/date).
     */
    @Inject
    public MediaService(BlobStore blobStore,
                        BlobStorageConfig config,
                        AuthContext authContext,
                        ConversationService conversationService,
                        BlobNamespaceResolver namespaceResolver) {
        this.blobStore = blobStore;
        this.config = config;
        this.authContext = authContext;
        this.conversationService = conversationService;
        this.namespaceResolver = namespaceResolver;
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
        long maxBytes = config.maxUploadBytes();
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

        String verifiedMime;
        try {
            verifiedMime = MediaTypeValidator.validate(upload.contentType(), data, allowedMimeTypes);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }

        String contentId = UUID.randomUUID().toString();
        String safeFilename = MediaTypeValidator.sanitizeFilename(upload.fileName(), contentId);
        String namespace = namespaceResolver.resolve(authContext);
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
        conversationService.ensureAccess(conversation, callerUserId, clientIp);
        try {
            Optional<byte[]> bytes = blobStore.get(meta.storageKey);
            if (bytes.isEmpty()) {
                throw new NotFoundException("Media blob missing: " + contentId);
            }
            return new MediaBytes(meta, bytes.get());
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
