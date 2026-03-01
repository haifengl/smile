/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Studio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Studio is distributed in the hope that it will be useful,
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
 * Tool calling gives models access to new functionality and data
 * that they can use to follow instructions and respond to prompts.
 * A message may involve multiple tool calls.
 * @param message the tool calling message.
 * @param outputs the output of tool calls.
 *
 * @author Haifeng Li
 */
public record ToolCalling(Object message, List<ToolCallOutput> outputs) {
}
