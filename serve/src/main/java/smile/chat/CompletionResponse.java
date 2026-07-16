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

import smile.llm.FinishReason;
import smile.llm.Message;

/**
 * Represents a single non-streaming chat completion choice.
 * Currently reserved for future non-streaming API support;
 * streaming responses are emitted as plain-text chunks directly.
 *
 * @author Haifeng Li
 */
public class CompletionResponse {
    /** The generated assistant message. */
    public Message message;
    /** The reason generation stopped (e.g. max tokens, stop sequence). */
    public FinishReason finishReason;
}
