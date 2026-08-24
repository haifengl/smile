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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import smile.chat.blob.MediaUrls;
import smile.llm.ContentPart;
import smile.llm.ImageUrlPart;
import smile.llm.Message;
import smile.llm.TextPart;
import smile.llm.VideoUrlPart;

/**
 * Serializes multimodal {@link Message} parts to JSON for
 * {@link ConversationItem#content} persistence (text inline; media as URLs).
 *
 * @author Haifeng Li
 */
public final class MessageContentCodec {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MessageContentCodec() {}

    /**
     * Encodes message parts as an OpenAI-style content JSON array string.
     * Text-only messages are stored as a plain string for backward compatibility.
     *
     * @param message chat message.
     * @return JSON array or plain text.
     */
    public static String toStoredContent(Message message) {
        if (!message.hasMedia()) {
            return message.content();
        }
        ArrayNode array = MAPPER.createArrayNode();
        for (ContentPart part : message.parts()) {
            if (part instanceof TextPart text) {
                ObjectNode node = array.addObject();
                node.put("type", "text");
                node.put("text", text.text());
            } else if (part instanceof ImageUrlPart image) {
                ObjectNode node = array.addObject();
                node.put("type", "image_url");
                ObjectNode imageUrl = node.putObject("image_url");
                imageUrl.put("url", image.url());
            } else if (part instanceof VideoUrlPart video) {
                ObjectNode node = array.addObject();
                node.put("type", "video_url");
                ObjectNode videoUrl = node.putObject("video_url");
                videoUrl.put("url", video.url());
                if (video.fps() != null) {
                    videoUrl.put("fps", video.fps());
                }
            }
        }
        try {
            return MAPPER.writeValueAsString(array);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to encode message content", e);
        }
    }

    /**
     * Collects internal media content ids referenced by the message.
     *
     * @param message chat message.
     * @return ordered unique content ids.
     */
    public static List<String> mediaContentIds(Message message) {
        Set<String> ids = new LinkedHashSet<>();
        for (ContentPart part : message.parts()) {
            String url = null;
            if (part instanceof ImageUrlPart image) {
                url = image.url();
            } else if (part instanceof VideoUrlPart video) {
                url = video.url();
            }
            if (url != null) {
                MediaUrls.parseContentId(url).ifPresent(ids::add);
            }
        }
        return new ArrayList<>(ids);
    }
}
