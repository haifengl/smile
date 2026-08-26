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
import java.util.List;
import smile.llm.ChatCompletion;
import smile.llm.ToolCall;

/**
 * Buffers streamed text and, when the final completion includes tool calls,
 * replays OpenAI-style {@code delta.tool_calls} chunks (v1: not token-true).
 *
 * @author Haifeng Li
 */
public final class StreamingToolCallAssembler {

    private StreamingToolCallAssembler() {}

    /**
     * Builds replay deltas for tool calls in a completed generation.
     *
     * @param completion final completion (after tool-call parsing).
     * @return ordered tool-call deltas; empty when no tool calls.
     */
    public static List<List<ToolCallDelta>> replayDeltas(ChatCompletion completion) {
        if (completion == null || !completion.hasToolCalls()) {
            return List.of();
        }
        List<List<ToolCallDelta>> chunks = new ArrayList<>();
        List<ToolCall> calls = completion.toolCalls();
        // First chunk: ids + names + empty arguments
        List<ToolCallDelta> first = new ArrayList<>();
        for (int i = 0; i < calls.size(); i++) {
            ToolCall call = calls.get(i);
            first.add(new ToolCallDelta(
                    i,
                    call.id(),
                    call.type(),
                    new ToolCallDelta.FunctionDelta(call.function().name(), "")));
        }
        chunks.add(first);
        // Second chunk: full arguments per call
        for (int i = 0; i < calls.size(); i++) {
            ToolCall call = calls.get(i);
            String args = call.function().arguments();
            if (args == null || args.isEmpty()) {
                continue;
            }
            chunks.add(List.of(new ToolCallDelta(
                    i, null, null, new ToolCallDelta.FunctionDelta(null, args))));
        }
        return chunks;
    }
}
