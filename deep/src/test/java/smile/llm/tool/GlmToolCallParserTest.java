/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.tool;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import smile.llm.ChatCompletion;
import smile.llm.ChatOptions;
import smile.llm.FinishReason;
import smile.llm.FunctionDefinition;
import smile.llm.Message;
import smile.llm.ToolChoice;
import smile.llm.ToolDefinition;
import smile.llm.template.GlmChatTemplate;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

/** Tests of {@link GlmToolCallParser}, alone and through {@link ToolCallPostProcessor}. */
public class GlmToolCallParserTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static ToolDefinition[] tools() {
        Map<String, Object> params = Map.of("type", "object", "properties", Map.of(
                "city", Map.of("type", "string"), "days", Map.of("type", "integer"),
                "detailed", Map.of("type", "boolean"), "tags", Map.of("type", "array")));
        return new ToolDefinition[]{ToolDefinition.function(new FunctionDefinition("forecast", "Weather", params))};
    }

    @Test
    public void testGivenArgumentsThenTypedBySchema() throws Exception {
        String text = "<tool_call>forecast<arg_key>city</arg_key><arg_value>12</arg_value>"
                + "<arg_key>days</arg_key><arg_value>3</arg_value><arg_key>detailed</arg_key><arg_value>true</arg_value>"
                + "<arg_key>tags</arg_key><arg_value>[\"a\", \"b\"]</arg_value></tool_call>";
        ParseResult r = new GlmToolCallParser(tools()).parse(text);
        assertEquals(FinishReason.tool_calls, r.reason());
        assertEquals(1, r.toolCalls().size());
        assertEquals("forecast", r.toolCalls().getFirst().function().name());
        Map<?, ?> args = MAPPER.readValue(r.toolCalls().getFirst().function().arguments(), Map.class);
        assertEquals("12", args.get("city"));                 // a string stays text although it looks like a number
        assertEquals(3, args.get("days"));
        assertEquals(true, args.get("detailed"));
        assertEquals(List.of("a", "b"), args.get("tags"));
    }

    @Test
    public void testGivenReasoningAndTextThenDroppedAndPrefixKept() {
        String text = "let me think</think>I will check.<tool_call>f<arg_key>x</arg_key><arg_value>hello world</arg_value></tool_call>"
                + "<tool_call>g</tool_call>";
        ParseResult r = new GlmToolCallParser().parse(text);
        assertEquals("I will check.", r.content());
        assertEquals(2, r.toolCalls().size());
        assertEquals("g", r.toolCalls().get(1).function().name());
        assertEquals("{}", r.toolCalls().get(1).function().arguments());
        assertTrue(r.toolCalls().getFirst().function().arguments().contains("\"hello world\""));
    }

    @Test
    public void testGivenTruncatedCallThenStillParsed() {
        ParseResult r = new GlmToolCallParser().parse("<tool_call>f<arg_key>a</arg_key><arg_value>1");
        assertEquals(1, r.toolCalls().size());
        assertTrue(r.toolCalls().getFirst().function().arguments().contains("\"a\":1"));
    }

    @Test
    public void testGivenNoCallThenPlainText() {
        ParseResult r = new GlmToolCallParser().parse("just an answer");
        assertFalse(r.hasToolCalls());
        assertEquals("just an answer", r.content());
    }

    @Test
    public void testGivenTemplateRenderedCallThenRoundTrips() throws Exception {
        // what the template renders for an assistant call is what the parser reads back
        var call = smile.llm.ToolCall.function("c1", new smile.llm.FunctionCall("forecast",
                "{\"city\":\"Paris\",\"days\":2,\"tags\":[\"x\"]}"));
        String prompt = new GlmChatTemplate(false).encode(List.of(Message.user("hi"), Message.assistant("", List.of(call))),
                new ChatOptions(tools(), ToolChoice.AUTO, true));
        String rendered = prompt.substring(prompt.lastIndexOf("<tool_call>forecast"));
        ParseResult r = new GlmToolCallParser(tools()).parse(rendered);
        assertEquals(1, r.toolCalls().size());
        assertEquals(MAPPER.readTree("{\"city\":\"Paris\",\"days\":2,\"tags\":[\"x\"]}"),
                MAPPER.readTree(r.toolCalls().getFirst().function().arguments()));
    }

    @Test
    public void testGivenPostProcessorThenGlmCallsAreRecognized() {
        ChatOptions options = new ChatOptions(tools(), ToolChoice.AUTO, true);
        ChatCompletion raw = new ChatCompletion("m", "reasoning</think><tool_call>forecast<arg_key>city</arg_key>"
                + "<arg_value>Oslo</arg_value></tool_call>", new int[]{1}, new int[]{2}, FinishReason.stop, null);
        ChatCompletion done = ToolCallPostProcessor.apply(raw, options);
        assertEquals(FinishReason.tool_calls, done.reason());
        assertEquals("forecast", done.toolCalls().getFirst().function().name());
        assertTrue(done.content() == null || done.content().isEmpty());
        // Qwen-style and JSON calls still go to their parsers
        ChatCompletion qwen = ToolCallPostProcessor.apply(new ChatCompletion("m",
                "<tool_call>\n<function=forecast>\n<parameter=city>\nRome\n</parameter>\n</function>\n</tool_call>",
                new int[]{1}, new int[]{2}, FinishReason.stop, null), options);
        assertEquals(FinishReason.tool_calls, qwen.reason());
        assertTrue(qwen.toolCalls().getFirst().function().arguments().contains("Rome"));
    }
}
