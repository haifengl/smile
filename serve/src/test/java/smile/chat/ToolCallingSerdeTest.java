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

import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import smile.llm.ChatCompletion;
import smile.llm.FinishReason;
import smile.llm.FunctionCall;
import smile.llm.ToolCall;
import smile.llm.ToolChoice;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tool-calling related unit tests for Serve DTOs.
 */
public class ToolCallingSerdeTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testGivenToolsJsonWhenToChatOptionsThenParsed() throws Exception {
        CompletionRequest request = mapper.readValue("""
                {
                  "messages":[{"role":"user","content":"hi"}],
                  "tools":[{
                    "type":"function",
                    "function":{
                      "name":"get_weather",
                      "description":"Weather",
                      "parameters":{"type":"object","properties":{"location":{"type":"string"}}}
                    }
                  }],
                  "tool_choice":"auto",
                  "parallel_tool_calls":true
                }
                """, CompletionRequest.class);
        var opts = request.toChatOptions();
        assertTrue(opts.hasTools());
        assertEquals(1, opts.tools().length);
        assertEquals("get_weather", opts.tools()[0].function().name());
        assertInstanceOf(ToolChoice.Auto.class, opts.toolChoice());
    }

    @Test
    public void testGivenRequiredWithoutToolsWhenToChatOptionsThenThrows() throws Exception {
        CompletionRequest request = mapper.readValue("""
                {"messages":[{"role":"user","content":"hi"}],"tool_choice":"required"}
                """, CompletionRequest.class);
        assertThrows(IllegalArgumentException.class, request::toChatOptions);
    }

    @Test
    public void testGivenAssistantWithToolCallsWhenSerializedThenOpenAiShape() throws Exception {
        ChatCompletion completion = new ChatCompletion(
                "qwen",
                null,
                new int[]{1},
                new int[]{2},
                FinishReason.tool_calls,
                null,
                List.of(ToolCall.function("call_abc",
                        new FunctionCall("get_weather", "{\"location\":\"SF\"}"))));
        ChatCompletionObject obj = ChatCompletionObject.of("chatcmpl-1", 1L, "qwen", completion);
        String json = mapper.writeValueAsString(obj);
        assertTrue(json.contains("\"tool_calls\""));
        assertTrue(json.contains("get_weather"));
        assertTrue(json.contains("tool_calls") || json.contains("\"finish_reason\":\"tool_calls\""));
        assertNull(obj.choices().getFirst().message().content());
    }

    @Test
    public void testGivenToolCallsWhenReplayDeltasThenOrderedChunks() {
        ChatCompletion completion = new ChatCompletion(
                "qwen", null, new int[0], new int[0], FinishReason.tool_calls, null,
                List.of(ToolCall.function("call_1",
                        new FunctionCall("fn", "{\"a\":1}"))));
        var chunks = StreamingToolCallAssembler.replayDeltas(completion);
        assertFalse(chunks.isEmpty());
        assertEquals("call_1", chunks.getFirst().getFirst().id());
        assertEquals("fn", chunks.getFirst().getFirst().function().name());
    }
}
