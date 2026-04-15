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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.persistence.*;
import jakarta.ws.rs.core.HttpHeaders;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.vertx.ext.web.RoutingContext;
import org.hibernate.annotations.CreationTimestamp;

/**
 * JPA entity representing a single chat session. Each conversation groups
 * one or more {@link ConversationItem} turns and stores client metadata such
 * as IP address and user-agent for auditing.
 */
@Entity
public class Conversation extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "client_ip", length = 64)
    public String clientIP;

    @Column(name = "user_agent", length = 512)
    public String userAgent;

    @ElementCollection
    @CollectionTable(
            name = "ConversationMetadata",
            joinColumns = @JoinColumn(name = "conversation_id"))
    @MapKeyColumn(name = "tag_key")
    @Column(name = "tag_value")
    public Map<String, String> metadata = new HashMap<>();

    /**
     * Captures the client IP and user-agent from the current HTTP request.
     * The {@code X-Forwarded-For} header is honoured so that the real client
     * IP is recorded when the service runs behind a reverse proxy.
     *
     * @param routingContext the Vert.x routing context for the current request.
     * @param headers        the JAX-RS HTTP headers for the current request.
     */
    public void setContext(RoutingContext routingContext, HttpHeaders headers) {
        userAgent = headers.getHeaderString("User-Agent");
        clientIP = routingContext.request().remoteAddress().hostAddress();

        // Prefer the leftmost address in X-Forwarded-For when behind a proxy.
        String forwardedFor = headers.getHeaderString("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            clientIP = forwardedFor.split(",")[0].trim();
        }
    }

    /**
     * Returns all conversations that have the given metadata tag.
     *
     * @param key   the metadata key.
     * @param value the expected value.
     * @return the matching conversations, or an empty list.
     */
    public static List<Conversation> findByTag(String key, String value) {
        // Use a JOIN against the element-collection table to filter by tag.
        return find("SELECT c FROM Conversation c JOIN c.metadata m WHERE KEY(m) = ?1 AND m = ?2", key, value).list();
    }
}
