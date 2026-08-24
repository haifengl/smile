/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm;

import java.util.List;

/**
 * Dialog message with OpenAI-style multimodal content parts.
 *
 * <p>Text-only messages use a single {@link TextPart}. Multimodal turns may
 * interleave text with {@link ImageUrlPart} / {@link VideoUrlPart}.
 *
 * @param role  speaker role.
 * @param parts ordered content parts (non-empty).
 * @author Haifeng Li
 */
public record Message(Role role, List<ContentPart> parts) {
    /**
     * Compact canonical constructor that validates inputs.
     *
     * @param role  speaker role — must not be null.
     * @param parts content parts — must be non-null and non-empty.
     */
    public Message {
        if (role == null) {
            throw new IllegalArgumentException("Message role must not be null");
        }
        if (parts == null || parts.isEmpty()) {
            throw new IllegalArgumentException("Message parts must not be null or empty");
        }
        for (ContentPart part : parts) {
            if (part == null) {
                throw new IllegalArgumentException("Message parts must not contain null");
            }
        }
        parts = List.copyOf(parts);
    }

    /**
     * Text-only convenience constructor.
     *
     * @param role    speaker role.
     * @param content plain text body.
     */
    public Message(Role role, String content) {
        this(role, List.of(new TextPart(content)));
    }

    /**
     * Varargs content parts constructor.
     *
     * @param role  speaker role.
     * @param parts content parts.
     */
    public Message(Role role, ContentPart... parts) {
        this(role, parts == null ? null : List.of(parts));
    }

    /**
     * Concatenates all {@link TextPart} text (ignores media parts).
     *
     * @return combined text, possibly empty.
     */
    public String content() {
        StringBuilder sb = new StringBuilder();
        for (ContentPart part : parts) {
            if (part instanceof TextPart text) {
                sb.append(text.text());
            }
        }
        return sb.toString();
    }

    /**
     * @return {@code true} when any part is an image, video, or audio.
     */
    public boolean hasMedia() {
        for (ContentPart part : parts) {
            if (part instanceof ImageUrlPart || part instanceof VideoUrlPart
                    || part instanceof AudioUrlPart) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return {@code true} when any part is audio.
     */
    public boolean hasAudio() {
        for (ContentPart part : parts) {
            if (part instanceof AudioUrlPart) {
                return true;
            }
        }
        return false;
    }

    /**
     * Factory method for a system message.
     *
     * @param content the message content.
     * @return a system message.
     */
    public static Message system(String content) {
        return new Message(Role.system, content);
    }

    /**
     * Factory method for a user message.
     *
     * @param content the message content.
     * @return a user message.
     */
    public static Message user(String content) {
        return new Message(Role.user, content);
    }

    /**
     * Multimodal user message.
     *
     * @param parts ordered content parts.
     * @return a user message.
     */
    public static Message user(ContentPart... parts) {
        return new Message(Role.user, parts);
    }

    /**
     * Factory method for an assistant message.
     *
     * @param content the message content.
     * @return an assistant message.
     */
    public static Message assistant(String content) {
        return new Message(Role.assistant, content);
    }
}
