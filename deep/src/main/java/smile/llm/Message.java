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

/**
 * Dialog messages.
 * @param role the role of message speaker.
 * @param content the message content.
 *
 * @author Haifeng Li
 */
public record Message(Role role, String content) {
    /**
     * Compact canonical constructor that validates inputs.
     * @param role the role of the message speaker — must not be null.
     * @param content the message content — must not be null.
     */
    public Message {
        if (role == null) throw new IllegalArgumentException("Message role must not be null");
        if (content == null) throw new IllegalArgumentException("Message content must not be null");
    }

    /**
     * Factory method for a system message.
     * @param content the message content.
     * @return a system message.
     */
    public static Message system(String content) {
        return new Message(Role.system, content);
    }

    /**
     * Factory method for a user message.
     * @param content the message content.
     * @return a user message.
     */
    public static Message user(String content) {
        return new Message(Role.user, content);
    }

    /**
     * Factory method for an assistant message.
     * @param content the message content.
     * @return an assistant message.
     */
    public static Message assistant(String content) {
        return new Message(Role.assistant, content);
    }
}
