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
package smile.llm.template;

import java.util.List;
import smile.llm.ChatOptions;
import smile.llm.Message;

/**
 * Chat template that encodes a dialog (and optional tools) into a prompt string
 * ready for tokenization.
 *
 * @author Haifeng Li
 */
public interface ChatTemplate {
    /**
     * Encodes dialog messages with optional tool-calling options.
     *
     * @param dialog  ordered conversation turns.
     * @param options chat options; may be {@code null}.
     * @return prompt text including an open assistant turn when applicable.
     */
    String encode(List<Message> dialog, ChatOptions options);

    /**
     * Encodes dialog with no tools and an open generation prompt.
     *
     * @param dialog ordered conversation turns.
     * @return prompt text.
     */
    default String encode(List<Message> dialog) {
        return encode(dialog, null);
    }
}
