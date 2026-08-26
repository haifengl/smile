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
 * OpenAI {@code tool_choice} semantics.
 *
 * @author Haifeng Li
 */
public sealed interface ToolChoice
        permits ToolChoice.Auto, ToolChoice.None, ToolChoice.Required, ToolChoice.Named {

    /** Model may call tools or answer normally. */
    record Auto() implements ToolChoice {}

    /** Do not inject tools; disable tool-call parsing. */
    record None() implements ToolChoice {}

    /** Model should call at least one tool. */
    record Required() implements ToolChoice {}

    /**
     * Force a specific function by name.
     *
     * @param name function name.
     */
    record Named(String name) implements ToolChoice {
        public Named {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("named tool choice requires a function name");
            }
        }
    }

    /** Shared singleton for {@link Auto}. */
    ToolChoice AUTO = new Auto();
    /** Shared singleton for {@link None}. */
    ToolChoice NONE = new None();
    /** Shared singleton for {@link Required}. */
    ToolChoice REQUIRED = new Required();
}
