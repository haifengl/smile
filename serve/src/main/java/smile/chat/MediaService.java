/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Serve is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.chat;

import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
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
import smile.chat.blob.BlobKeys;
import smile.chat.BlobStorageConfig;
import smile.chat.blob.BlobStore;
import smile.chat.blob.ClientIdentity;
import smile.chat.blob.MediaUrls;
import smile.llm.ContentPart;
import smile.llm.ImageUrlPart;
import smile.llm.Message;
import smile.llm.VideoUrlPart;

/**
 * Upload, fetch, link, and resolve conversation multimedia blobs.
 *
 * @author Haifeng Li
 */
@ApplicationScoped
public class MediaService {
    private static final Logger logger = Logger.getLogger(MediaService.class);

    private final BlobStore blobStore;
    private final BlobStorageConfig config;

    /**
     * @param blobStore object storage backend.
     * @param config    upload limits and backend settings.
     */
    @Inject
    public MediaService(BlobStore blobStore, BlobStorageConfig config) {
        this.blobStore = blobStore;
        this.config = config;
    }

    /**
     * Stores an uploaded file under the conversation and returns metadata.
     *
     * @param conversation   parent conversation.
     * @param upload         multipart file.
     * @param role           {@code user} or {@code assistant}.
     * @param routingContext request context for client IP hashing.
     * @param headers        HTTP headers.
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
        long size = upload.size();
        if (size < 0) {
            size = 0;
        }
        if (size > config.maxUploadBytes()) {
            throw new BadRequestException(
                    "file exceeds max upload size of " + config.maxUploadBytes() + " bytes");
        }
        String mime = upload.contentType();
        if (mime == null || mime.isBlank()) {
            mime = "application/octet-stream";
        }
        String filename = upload.fileName();
        String userId = ClientIdentity.fromRequest(routingContext, headers);
        String contentId = UUID.randomUUID().toString();
        String storageKey = BlobKeys.of(userId, conversation.id, null, contentId);

        byte[] data;
        try {
            var path = upload.uploadedFile();
            if (path == null || !Files.isRegularFile(path)) {
                throw new BadRequestException("empty upload");
            }
            data = Files.readAllBytes(path);
        } catch (IOException e) {
            throw new BadRequestException("Failed to read upload", e);
        }
        if (data.length > config.maxUploadBytes()) {
            throw new BadRequestException(
                    "file exceeds max upload size of " + config.maxUploadBytes() + " bytes");
        }

        String sha256 = sha256Hex(data);
        try {
            blobStore.put(storageKey, data, mime);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store blob", e);
        }

        ConversationContent content = new ConversationContent();
        content.id = contentId;
        content.conversationId = conversation.id;
        content.messageId = null;
        content.userId = userId;
        content.role = (role == null || role.isBlank()) ? "user" : role.trim();
        content.mimeType = mime;
        content.filename = filename;
        content.sizeBytes = data.length;
        content.sha256 = sha256;
        content.storageKey = storageKey;
        content.persist();

        logger.infof("Stored media contentId=%s conversationId=%d size=%d mime=%s",
                contentId, conversation.id, data.length, mime);
        return ContentObject.from(content);
    }

    /**
     * Loads blob bytes and metadata for streaming.
     *
     * @param contentId opaque UUID.
     * @return metadata and bytes.
     */
    public MediaBytes getBytes(String contentId) {
        ConversationContent meta = ConversationContent.findById(contentId);
        if (meta == null) {
            throw new NotFoundException("Media not found: " + contentId);
        }
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
     * Deletes all content metadata and blobs for a conversation.
     *
     * @param conversationId conversation primary key.
     */
    @Transactional
    public void deleteConversationMedia(long conversationId) {
        List<ConversationContent> rows = ConversationContent
                .list("conversationId", conversationId);
        for (ConversationContent row : rows) {
            try {
                blobStore.delete(row.storageKey);
            } catch (IOException e) {
                logger.warnf(e, "Failed to delete blob key=%s", row.storageKey);
            }
            row.delete();
        }
        // Best-effort prefix cleanup (orphans under pending/).
        if (!rows.isEmpty()) {
            String userId = rows.getFirst().userId;
            try {
                blobStore.deletePrefix(BlobKeys.conversationPrefix(userId, conversationId));
            } catch (IOException e) {
                logger.warnf(e, "Failed to delete blob prefix for conversation %d", conversationId);
            }
        }
    }

    /**
     * Rewrites internal {@code /api/v1/media/...} URLs to {@code data:} URLs so
     * the VL processor does not HTTP-loopback to this service.
     *
     * @param messages request messages.
     * @return messages with resolved media parts (same array when unchanged).
     */
    public Message[] resolveInternalMedia(Message[] messages) {
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
                    Optional<String> dataUrl = toDataUrl(image.url());
                    if (dataUrl.isPresent()) {
                        parts.add(new ImageUrlPart(dataUrl.get()));
                        partChanged = true;
                    } else {
                        parts.add(part);
                    }
                } else if (part instanceof VideoUrlPart video) {
                    Optional<String> dataUrl = toDataUrl(video.url());
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
                out[i] = new Message(m.role(), parts);
                changed = true;
            } else {
                out[i] = m;
            }
        }
        return changed ? out : messages;
    }

    private Optional<String> toDataUrl(String url) {
        Optional<String> contentId = MediaUrls.parseContentId(url);
        if (contentId.isEmpty()) {
            return Optional.empty();
        }
        MediaBytes media = getBytes(contentId.get());
        String mime = media.meta().mimeType;
        if (mime == null || mime.isBlank()) {
            mime = "application/octet-stream";
        }
        String encoded = Base64.getEncoder().encodeToString(media.bytes());
        return Optional.of("data:" + mime + ";base64," + encoded);
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
