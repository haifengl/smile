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
import java.util.Map;
import org.junit.jupiter.api.Test;
import smile.llm.ChatOptions;
import smile.llm.FunctionCall;
import smile.llm.FunctionDefinition;
import smile.llm.Message;
import smile.llm.ToolCall;
import smile.llm.ToolChoice;
import smile.llm.ToolDefinition;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Qwen3ChatTemplate}.
 */
public class Qwen3ChatTemplateTest {
    private final Qwen3ChatTemplate template = new Qwen3ChatTemplate(true, true);

    @Test
    public void testGivenToolsWhenEncodedThenSystemBlockContainsTools() {
        ToolDefinition tool = ToolDefinition.function(new FunctionDefinition(
                "get_weather",
                "Get weather",
                Map.of("type", "object",
                        "properties", Map.of("location", Map.of("type", "string")),
                        "required", List.of("location"))));
        ChatOptions options = new ChatOptions(new ToolDefinition[]{tool}, ToolChoice.AUTO, true);
        String prompt = template.encode(List.of(Message.user("Weather in SF?")), options);
        assertTrue(prompt.contains("# Tools"));
        assertTrue(prompt.contains("get_weather"));
        assertTrue(prompt.contains("<tool_call>"));
        assertTrue(prompt.contains("<|im_start|>user\nWeather in SF?"));
        assertTrue(prompt.endsWith("<|im_start|>assistant\n<think>\n")
                || prompt.contains("<|im_start|>assistant\n<think>\n"));
    }

    @Test
    public void testGivenAssistantToolCallsWhenEncodedThenXmlEmitted() {
        Message assistant = Message.assistant(null, List.of(
                ToolCall.function("call_1", new FunctionCall(
                        "get_weather", "{\"location\":\"SF\"}"))));
        String prompt = new Qwen3ChatTemplate(false, true).encode(
                List.of(Message.user("hi"), assistant), null);
        assertTrue(prompt.contains("<tool_call>"));
        assertTrue(prompt.contains("<function=get_weather>"));
        assertTrue(prompt.contains("<parameter=location>"));
        assertTrue(prompt.contains("SF"));
    }

    @Test
    public void testGivenToolResultWhenEncodedThenToolResponseBlock() {
        String prompt = new Qwen3ChatTemplate(false, true).encode(List.of(
                Message.user("hi"),
                Message.assistant(null, List.of(
                        ToolCall.function("call_1", new FunctionCall("get_weather", "{\"location\":\"SF\"}")))),
                Message.tool("call_1", "72F and sunny")
        ), null);
        assertTrue(prompt.contains("<tool_response>"));
        assertTrue(prompt.contains("72F and sunny"));
        assertTrue(prompt.contains("<|im_start|>user"));
    }
}
