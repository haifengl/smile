/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile Shell is free software: you can redistribute it and/or modify
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile Shell is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
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

@Entity
public class Conversation extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @CreationTimestamp
    @Column(name = "created_at")
    public Instant createdAt;
    @Column(name = "client_ip")
    public String clientIP;
    @Column(name = "user_agent")
    public String userAgent;

    @ElementCollection
    @CollectionTable(
            name = "ConversationMetadata", // Table name
            joinColumns = @JoinColumn(name = "conversation_id"))
    @MapKeyColumn(name = "tag_key") // Column for the map key
    @Column(name = "tag_value") // Column for the map value
    public Map<String, String> metadata = new HashMap<>();

    public void setContext(RoutingContext routingContext, HttpHeaders headers) {
        userAgent = headers.getHeaderString("User-Agent");
        clientIP = routingContext.request().remoteAddress().hostAddress();

        // Check for common headers if behind a proxy
        String forwardedFor = headers.getHeaderString("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            clientIP = forwardedFor.split(",")[0].trim();
        }
    }

    /**
     * Returns the list of conversations with given key-value tag.
     * @param key the tag key.
     * @param value the tag value.
     * @return the list of conversations with given key-value tag.
     */
    public static List<Conversation> findByTag(String key, String value) {
        // Panache uses positional parameters (e.g., ?1, ?2) for clarity in maps
        return find("metadata(?1, ?2)", key, value).list();
    }
}
