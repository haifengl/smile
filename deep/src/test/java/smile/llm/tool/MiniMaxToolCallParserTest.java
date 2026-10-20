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
package smile.llm.tool;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import smile.llm.ChatCompletion;
import smile.llm.ChatOptions;
import smile.llm.FinishReason;
import smile.llm.FunctionCall;
import smile.llm.FunctionDefinition;
import smile.llm.Message;
import smile.llm.Role;
import smile.llm.TextPart;
import smile.llm.ToolCall;
import smile.llm.ToolChoice;
import smile.llm.ToolDefinition;
import smile.llm.template.MiniMaxChatTemplate;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link MiniMaxToolCallParser}, including a round trip through {@link MiniMaxChatTemplate}.
 *
 * @author Haifeng Li
 */
public class MiniMaxToolCallParserTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String NS = MiniMaxToolCallParser.NS;

    private static ToolDefinition tool(String name, Map<String, Object> properties) {
        return new ToolDefinition("function", new FunctionDefinition(name, "d",
                Map.of("type", "object", "properties", properties)));
    }

    private static final ToolDefinition SEARCH = tool("search", Map.of(
            "query", Map.of("type", "string"),
            "limit", Map.of("type", "integer"),
            "score", Map.of("type", "number"),
            "exact", Map.of("type", "boolean"),
            "tags", Map.of("type", "array", "items", Map.of("type", "string")),
            "filters", Map.of("type", "object", "properties", Map.of(
                    "year", Map.of("type", "integer"), "open", Map.of("type", "boolean")))));

    private static String call(String name, String... kv) {
        StringBuilder sb = new StringBuilder(NS + "<invoke name=\"" + name + "\">");
        for (int i = 0; i < kv.length; i += 2) {
            sb.append(NS).append('<').append(kv[i]).append('>').append(kv[i + 1])
                    .append(NS).append("</").append(kv[i]).append('>');
        }
        return sb.append(NS).append("</invoke>\n").toString();
    }

    private static String block(String... calls) {
        return NS + "<tool_call>\n" + String.join("", calls) + NS + "</tool_call>";
    }

    private static JsonNode args(ToolCall call) throws Exception {
        return MAPPER.readTree(call.function().arguments());
    }

    @Test
    public void testGivenSchemaWhenParsedThenValuesAreTypedFromIt() throws Exception {
        String text = block(call("search", "query", "123", "limit", "10", "score", "2.5", "exact", "true",
                "tags", NS + "<item>a" + NS + "</item>" + NS + "<item>b" + NS + "</item>",
                "filters", NS + "<year>2020" + NS + "</year>" + NS + "<open>false" + NS + "</open>"));
        ParseResult r = new MiniMaxToolCallParser(new ToolDefinition[]{SEARCH}).parse(text);
        assertTrue(r.hasToolCalls());
        assertEquals(FinishReason.tool_calls, r.reason());
        assertNull(r.content());
        assertEquals("search", r.toolCalls().getFirst().function().name());
        JsonNode a = args(r.toolCalls().getFirst());
        assertEquals("123", a.get("query").asString());            // string stays a string
        assertEquals(10, a.get("limit").asInt());
        assertEquals(2.5, a.get("score").asDouble(), 0);
        assertTrue(a.get("exact").asBoolean());
        assertEquals(List.of("a", "b"), List.of(a.get("tags").get(0).asString(), a.get("tags").get(1).asString()));
        assertEquals(2020, a.get("filters").get("year").asInt());
        assertFalse(a.get("filters").get("open").asBoolean());
    }

    @Test
    public void testGivenNoSchemaWhenParsedThenJsonLiteralsAreDecoded() throws Exception {
        String text = block(call("f", "n", "42", "ok", "true", "name", "Paris", "none", "null"));
        JsonNode a = args(new MiniMaxToolCallParser().parse(text).toolCalls().getFirst());
        assertEquals(42, a.get("n").asInt());
        assertTrue(a.get("ok").asBoolean());
        assertEquals("Paris", a.get("name").asString());
        assertTrue(a.get("none").isNull());
    }

    @Test
    public void testGivenUnknownToolWhenToolsAreKnownThenValuesStayStrings() throws Exception {
        String text = block(call("other", "n", "42"));
        JsonNode a = args(new MiniMaxToolCallParser(new ToolDefinition[]{SEARCH}).parse(text).toolCalls().getFirst());
        assertEquals("42", a.get("n").asString());
    }

    @Test
    public void testGivenSeveralCallsAndLeadingTextWhenParsedThenOrderAndPrefixKept() throws Exception {
        String text = "Let me look.\n" + block(call("a", "x", "1"), call("b", "y", "two"));
        ParseResult r = new MiniMaxToolCallParser().parse(text);
        assertEquals("Let me look.", r.content());
        assertEquals(2, r.toolCalls().size());
        assertEquals("a", r.toolCalls().get(0).function().name());
        assertEquals("b", r.toolCalls().get(1).function().name());
        assertNotEquals(r.toolCalls().get(0).id(), r.toolCalls().get(1).id());
    }

    @Test
    public void testGivenReasoningBeforeCallWhenParsedThenReasoningIsDropped() {
        String text = "<think>I should call it.</think>" + block(call("a", "x", "1"));
        ParseResult r = new MiniMaxToolCallParser().parse(text);
        assertTrue(r.hasToolCalls());
        assertNull(r.content());
    }

    @Test
    public void testGivenTruncatedBlockWhenParsedThenPartialCallIsKept() throws Exception {
        String text = NS + "<tool_call>\n" + NS + "<invoke name=\"search\">" + NS + "<query>cats and do";
        ParseResult r = new MiniMaxToolCallParser(new ToolDefinition[]{SEARCH}).parse(text, true);
        assertTrue(r.hasToolCalls());
        assertEquals("cats and do", args(r.toolCalls().getFirst()).get("query").asString());
    }

    @Test
    public void testGivenPlainTextWhenParsedThenNoToolCalls() {
        ParseResult r = new MiniMaxToolCallParser().parse("Just an answer.");
        assertFalse(r.hasToolCalls());
        assertEquals("Just an answer.", r.content());
        assertEquals(FinishReason.stop, r.reason());
    }

    @Test
    public void testGivenTemplateRenderedCallWhenParsedThenArgumentsRoundTrip() throws Exception {
        String json = "{\"query\":\"x <y> & \\\"z\\\"\",\"limit\":3,\"score\":0.25,\"exact\":false,"
                + "\"tags\":[\"a\",\"b c\"],\"filters\":{\"year\":1999,\"open\":true}}";
        var dialog = List.of(new Message(Role.user, "q"),
                new Message(Role.assistant, List.of(), List.of(
                        ToolCall.function("c1", new FunctionCall("search", json))), null, null));
        String prompt = new MiniMaxChatTemplate(false, null).encode(dialog, null);
        int from = prompt.indexOf(NS + "<tool_call>\n");
        int to = prompt.lastIndexOf("[e~[");
        String generated = prompt.substring(from, to);
        ParseResult r = new MiniMaxToolCallParser(new ToolDefinition[]{SEARCH}).parse(generated);
        assertEquals(1, r.toolCalls().size());
        assertEquals(MAPPER.readTree(json), args(r.toolCalls().getFirst()));
    }

    @Test
    public void testGivenCompletionWithToolsWhenPostProcessedThenToolCallsAndCleanContent() throws Exception {
        ChatOptions options = new ChatOptions(new ToolDefinition[]{SEARCH}, ToolChoice.AUTO, true);
        String raw = "<think>hmm</think>Checking." + block(call("search", "query", "cats", "limit", "5"));
        ChatCompletion in = new ChatCompletion("m", raw, new int[]{1}, new int[]{2}, FinishReason.stop, null);
        ChatCompletion out = ToolCallPostProcessor.apply(in, options);
        assertEquals(FinishReason.tool_calls, out.reason());
        assertEquals("Checking.", out.content());
        assertEquals(1, out.toolCalls().size());
        assertEquals(5, args(out.toolCalls().getFirst()).get("limit").asInt());
    }

    @Test
    public void testGivenMiniMaxMarkupWhenSanitizedThenOnlyAnswerRemains() {
        String text = "<think>plan</think>Answer." + block(call("a", "x", "1")) + "[e~[";
        assertEquals("Answer.", AssistantTextSanitizer.sanitize(text));
        String truncated = "Answer." + NS + "<tool_call>\n" + NS + "<invoke name=\"a\">" + NS + "<x>1";
        assertEquals("Answer.", AssistantTextSanitizer.sanitize(truncated));
        assertNull(AssistantTextSanitizer.sanitize("</think>" + NS + "<tool_call>"));
    }
}
