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
import jakarta.persistence.*;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import org.hibernate.annotations.CreationTimestamp;

/**
 * A single message turn within a {@link Conversation}.
 * Each item stores the role ({@code user} or {@code assistant}) and the
 * corresponding message content, together with a creation timestamp.
 */
@Entity
@Table(name = "ConversationItem",
       indexes = @Index(name = "idx_conversation_item_conversation_id",
                        columnList = "conversation_id"))
public class ConversationItem extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** Reference to the parent conversation. */
    @Column(name = "conversation_id", nullable = false)
    public Long conversationId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    /** The speaker role, e.g. {@code "user"} or {@code "assistant"}. */
    @Column(nullable = false, length = 32)
    public String role;

    /** The raw message text. */
    @Column(nullable = false, columnDefinition = "TEXT")
    public String content;
}
