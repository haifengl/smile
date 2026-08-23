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
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import smile.llm.ContentPart;
import smile.llm.ImageUrlPart;
import smile.llm.Message;
import smile.llm.Role;
import smile.llm.TextPart;
import smile.llm.VideoUrlPart;

/**
 * Deserializes OpenAI-style chat messages: {@code content} may be a string or
 * an array of {@code text} / {@code image_url} / {@code video_url} parts.
 *
 * @author Haifeng Li
 */
public class MessageDeserializer extends JsonDeserializer<Message> {
    @Override
    public Message deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        String roleStr = node.path("role").asText("user");
        Role role = switch (roleStr) {
            case "system" -> Role.system;
            case "assistant" -> Role.assistant;
            case "ipython" -> Role.ipython;
            default -> Role.user;
        };
        JsonNode content = node.get("content");
        if (content == null || content.isNull()) {
            throw new IOException("message content required");
        }
        if (content.isTextual()) {
            return new Message(role, content.asText());
        }
        if (!content.isArray()) {
            throw new IOException("message content must be a string or array");
        }
        List<ContentPart> parts = new ArrayList<>();
        for (JsonNode part : content) {
            String type = part.path("type").asText("");
            switch (type) {
                case "text" -> parts.add(new TextPart(part.path("text").asText("")));
                case "image_url" -> {
                    String url = part.path("image_url").path("url").asText(null);
                    if (url == null || url.isBlank()) {
                        url = part.path("url").asText(null);
                    }
                    if (url == null || url.isBlank()) {
                        throw new IOException("image_url.url required");
                    }
                    parts.add(new ImageUrlPart(url));
                }
                case "video_url" -> {
                    String url = part.path("video_url").path("url").asText(null);
                    if (url == null || url.isBlank()) {
                        url = part.path("url").asText(null);
                    }
                    if (url == null || url.isBlank()) {
                        throw new IOException("video_url.url required");
                    }
                    Double fps = null;
                    if (part.path("video_url").has("fps")) {
                        fps = part.path("video_url").path("fps").asDouble();
                    }
                    parts.add(new VideoUrlPart(url, fps));
                }
                default -> {
                    if (part.has("text")) {
                        parts.add(new TextPart(part.path("text").asText("")));
                    }
                }
            }
        }
        if (parts.isEmpty()) {
            throw new IOException("message content parts empty");
        }
        return new Message(role, parts);
    }
}
