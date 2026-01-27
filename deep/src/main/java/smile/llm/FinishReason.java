/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
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
 * The reasons that the chat completions finish.
 *
 * @author Karl Li
 */
public enum FinishReason {
    /** A message terminated by one of the stop tokens. */
    stop,
    /** Incomplete model output due to token limit. */
    length,
    /** The model decided to call a function. */
    function_call, 
    /** Omitted content due to a flag from content filters. */
    content_filter
}
