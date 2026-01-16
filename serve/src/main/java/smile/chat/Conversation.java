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
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import org.hibernate.annotations.CreationTimestamp;

@Entity
public class Conversation extends PanacheEntity {
    public String clientIP;
    public String userAgent;

    @CreationTimestamp
    public Instant createdAt;

    @ElementCollection
    @CollectionTable(name = "CONVERSATION_METADATA") // Table name
    @MapKeyColumn(name = "KEY") // Column for the map key
    @Column(name = "VALUE") // Column for the map value
    public Map<String, String> metadata = new HashMap<>();

    public static List<Conversation> findByTag(String key, String value) {
        // Panache uses positional parameters (e.g., ?1, ?2) for clarity in maps
        return find("metadata(?1, ?2)", key, value).list();
    }
}
