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

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Metadata for a multimedia blob stored outside the relational database.
 *
 * <p>Bytes live in {@link smile.chat.blob.BlobStore} under {@link #storageKey};
 * this row holds MIME type, size, ownership, and message linkage.
 *
 * @author Haifeng Li
 */
@Entity
@Table(name = "ConversationContent",
       indexes = {
               @Index(name = "idx_conversation_content_conversation_id",
                      columnList = "conversation_id"),
               @Index(name = "idx_conversation_content_message_id",
                      columnList = "message_id")
       })
public class ConversationContent extends PanacheEntityBase {
    /** Opaque UUID primary key ({@code content_id}). */
    @Id
    @Column(length = 36, nullable = false)
    public String id;

    @Column(name = "conversation_id", nullable = false)
    public Long conversationId;

    /**
     * Owning {@link ConversationItem} id once the message is persisted;
     * {@code null} while the upload is still pending send.
     */
    @Column(name = "message_id")
    public Long messageId;

    /** Hashed client identity used in the storage key namespace. */
    @Column(name = "user_id", nullable = false, length = 64)
    public String userId;

    /** Speaker role, e.g. {@code user} or {@code assistant}. */
    @Column(nullable = false, length = 32)
    public String role;

    @Column(name = "mime_type", length = 128)
    public String mimeType;

    @Column(length = 512)
    public String filename;

    @Column(name = "size_bytes", nullable = false)
    public long sizeBytes;

    @Column(length = 64)
    public String sha256;

    /** Full blob key in object storage. */
    @Column(name = "storage_key", nullable = false, length = 512)
    public String storageKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;
}
