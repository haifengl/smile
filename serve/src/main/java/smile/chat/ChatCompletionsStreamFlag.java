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

import java.nio.charset.StandardCharsets;

/**
 * Parses the {@code stream} flag from a chat-completions JSON body.
 *
 * @author Haifeng Li
 */
public final class ChatCompletionsStreamFlag {

    private ChatCompletionsStreamFlag() {}

    /**
     * Parses the {@code stream} boolean from a raw JSON body.
     *
     * <p>Omitted / unparseable → {@code true} (smile streaming default).
     *
     * @param body raw request bytes.
     * @return whether SSE streaming should be used.
     */
    public static boolean streamFlag(byte[] body) {
        if (body == null || body.length == 0) {
            return true;
        }
        String json = new String(body, StandardCharsets.UTF_8);
        try {
            var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            var stream = node.get("stream");
            if (stream == null || stream.isNull()) {
                return true;
            }
            if (stream.isBoolean()) {
                return stream.booleanValue();
            }
            if (stream.isTextual()) {
                return !stream.asText().equalsIgnoreCase("false");
            }
            return true;
        } catch (Exception e) {
            return true;
        }
    }
}
