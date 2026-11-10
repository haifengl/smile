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
package smile.llm.template;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import smile.llm.ChatOptions;
import smile.llm.FunctionCall;
import smile.llm.FunctionDefinition;
import smile.llm.Message;
import smile.llm.ToolCall;
import smile.llm.ToolChoice;
import smile.llm.ToolDefinition;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests of {@link DeepSeekChatTemplate}. The five vectors and the expected strings of the other tests come from
 * the checkpoint's own {@code encoding/} directory (test_input_N.json, test_output_N.txt, test_encoding.py).
 */
public class DeepSeekChatTemplateTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SYS = "<｜System｜>";
    private static final String BOS = "<｜begin▁of▁sentence｜>";
    private static final String EOS = "<｜end▁of▁sentence｜>";

    private static String resource(String name) throws IOException {
        try (InputStream in = DeepSeekChatTemplateTest.class.getResourceAsStream("/deepseek/" + name)) {
            assertNotNull(in, name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String effort(int budget) {
        return SYS + "Reasoning Effort: " + budget
                + " (range 1-100, the higher the value, the more thorough the reasoning)\n\n";
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> msgs(String json) {
        return (List<Map<String, Object>>) JSON.readValue(json, Object.class);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testGivenShippedVectorsWhenEncodedThenOutputsMatchByteForByte() throws IOException {
        int checked = 0;
        for (int n = 1; n <= 5; n++) {
            Object data = JSON.readValue(resource("test_input_" + n + ".json"), Object.class);
            // load_cases: a dict is one case, a list of messages is one case, otherwise a list of cases
            List<Map<String, Object>> cases;
            if (data instanceof Map<?, ?>) {
                cases = List.of((Map<String, Object>) data);
            } else {
                var list = (List<Map<String, Object>>) data;
                cases = list.getFirst().containsKey("role") ? List.of(Map.of("messages", list)) : list;
            }
            assertEquals(1, cases.size(), "vector " + n + " has one expected output");
            Map<String, Object> c = cases.getFirst();
            List<Map<String, Object>> messages = (List<Map<String, Object>>) c.get("messages");
            if (c.get("tools") != null) {
                messages.getFirst().put("tools", c.get("tools"));
            }
            String mode = c.get("thinking_mode") != null ? (String) c.get("thinking_mode") : "chat";
            var encoded = DeepSeekChatTemplate.encodeMessages(messages, mode,
                    (List<Map<String, Object>>) c.get("context"), true, true, c.get("reasoning_effort"));
            assertEquals(resource("test_output_" + n + ".txt"), encoded.prompt(), "vector " + n);
            checked++;
        }
        assertEquals(5, checked);
    }

    @Test
    public void testGivenEffortWhenThinkingThenBudgetRendered() {
        for (Object[] e : new Object[][]{{null, 75}, {"low", 50}, {"high", 75}, {"max", 100}, {42, 42}, {100, 100}, {1, 1}}) {
            var prompt = DeepSeekChatTemplate.encodeMessages(msgs("[{\"role\":\"user\",\"content\":\"question\"}]"),
                    "thinking", null, true, true, e[0]).prompt();
            assertEquals(BOS + effort((Integer) e[1]) + "<｜User｜>question<｜Assistant｜><think>", prompt);
        }
    }

    @Test
    public void testGivenChatModeThenNoEffortOrSystemToken() {
        var prompt = DeepSeekChatTemplate.encodeMessages(msgs("[{\"role\":\"user\",\"content\":\"hello\"}]"),
                "chat", null, true, true, "max").prompt();
        assertEquals(BOS + "<｜User｜>hello<｜Assistant｜></think>", prompt);
    }

    @Test
    public void testGivenInvalidEffortThenRejected() {
        for (Object bad : new Object[]{-1, 0, 101, "medium", true, false, 1.5}) {
            assertThrows(IllegalArgumentException.class, () -> DeepSeekChatTemplate.encodeMessages(
                    msgs("[{\"role\":\"user\",\"content\":\"q\"}]"), "thinking", null, true, true, bad), String.valueOf(bad));
        }
    }

    @Test
    public void testGivenLeadingSystemThenSystemTokenUsed() {
        var prompt = DeepSeekChatTemplate.encodeMessages(msgs(
                "[{\"role\":\"system\",\"content\":\"You are a helpful assistant.\"},{\"role\":\"user\",\"content\":\"hello\"}]"),
                "chat");
        assertEquals(BOS + SYS + "You are a helpful assistant.<｜User｜>hello<｜Assistant｜></think>", prompt);
    }

    @Test
    public void testGivenMidConversationSystemThenOwnTokenAndAssistantHeader() {
        var prompt = DeepSeekChatTemplate.encodeMessages(msgs("""
                [{"role":"system","content":"sys"},{"role":"user","content":"q1"},
                 {"role":"assistant","content":"a1","reasoning_content":"r1"},{"role":"system","content":"mid sys"}]"""),
                "thinking", null, true, true, 88).prompt();
        assertEquals(BOS + effort(88) + "sys<｜User｜>q1<｜Assistant｜></think>a1" + EOS
                + "<｜System｜>mid sys<｜Assistant｜><think>", prompt);
    }

    private static final String TOOL = """
            {"type":"function","function":{"name":"lookup","description":"Look up a value",
             "parameters":{"type":"object","properties":{"query":{"type":"string"},"limit":{"type":"integer"}}}}}""";
    private static final String CALL_OUTPUT = "  reason  </think>summary\n\n"
            + "<｜DSML｜ calls>\n<｜DSML｜ invoke name=\"lookup\">\n"
            + "<｜DSML｜ parameter name=\"query\" string=\"true\">value</｜DSML｜ parameter>\n"
            + "<｜DSML｜ parameter name=\"limit\" string=\"false\">2</｜DSML｜ parameter>\n"
            + "</｜DSML｜ invoke>\n</｜DSML｜ calls>" + EOS;

    @SuppressWarnings("unchecked")
    @Test
    public void testGivenToolsInChatModeThenSpacedDsmlTags() {
        List<Map<String, Object>> m = msgs("[{\"role\":\"system\",\"content\":\"system\"},{\"role\":\"user\",\"content\":\"question\"}]");
        m.getFirst().put("tools", List.of(JSON.readValue(TOOL, Object.class)));
        String prompt = DeepSeekChatTemplate.encodeMessages(m, "chat");
        assertTrue(prompt.contains("<｜DSML｜ calls>\n<｜DSML｜ invoke name=\"$TOOL_NAME\">\n"
                + "<｜DSML｜ parameter name=\"$PARAMETER_NAME\" string=\"true|false\">$PARAMETER_VALUE</｜DSML｜ parameter>\n"
                + "...\n</｜DSML｜ invoke>"));
        assertFalse(prompt.contains("<｜DSML｜tool_calls>"));
        assertFalse(prompt.contains("<｜DSML｜invoke"));
        assertFalse(prompt.contains("<｜DSML｜parameter"));
        assertTrue(prompt.contains("{\"name\": \"lookup\", \"description\": \"Look up a value\", \"parameters\": "));
    }

    private static List<Map<String, Object>> toolCallConversation() {
        return msgs("""
                [{"role":"user","content":"question"},
                 {"role":"assistant","reasoning_content":"  reason  ","content":"summary",
                  "tool_calls":[{"type":"function","function":{"name":"lookup","arguments":"{\\"query\\":\\"value\\",\\"limit\\":2}"}}]}]""");
    }

    @Test
    public void testGivenToolCallThenAssistantTurnRendersSpacedDsml() {
        var all = toolCallConversation();
        var prompt = DeepSeekChatTemplate.encodeMessages(List.of(all.get(1)), "thinking", List.of(all.get(0)), true, true, null);
        assertEquals(CALL_OUTPUT, prompt.prompt());
    }

    @Test
    public void testGivenToolCallCompletionThenParsedAndReEncodedIdentically() {
        var parsed = DeepSeekChatTemplate.parseCompletion(CALL_OUTPUT, "thinking");
        assertEquals("  reason  ", parsed.reasoningContent());
        assertEquals("summary", parsed.content());
        assertEquals(1, parsed.toolCalls().size());
        assertEquals("lookup", parsed.toolCalls().getFirst().name());
        assertEquals("{\"query\": \"value\", \"limit\": 2}", parsed.toolCalls().getFirst().arguments());

        // re-encoding the parsed message reproduces the completion
        Map<String, Object> assistant = new LinkedHashMap<>();
        assistant.put("role", "assistant");
        assistant.put("content", parsed.content());
        assistant.put("reasoning_content", parsed.reasoningContent());
        assistant.put("tool_calls", List.of(Map.of("type", "function", "function",
                Map.of("name", "lookup", "arguments", parsed.toolCalls().getFirst().arguments()))));
        var again = DeepSeekChatTemplate.encodeMessages(List.of(assistant), "thinking",
                List.of(toolCallConversation().getFirst()), true, true, null);
        assertEquals(CALL_OUTPUT, again.prompt());
    }

    @Test
    public void testGivenV4UnspacedDsmlThenParseRejected() {
        String v4 = CALL_OUTPUT.replace("｜DSML｜ calls", "｜DSML｜tool_calls")
                .replace("｜DSML｜ invoke", "｜DSML｜invoke").replace("｜DSML｜ parameter", "｜DSML｜parameter");
        assertThrows(IllegalArgumentException.class, () -> DeepSeekChatTemplate.parseCompletion(v4, "thinking"));
    }

    @Test
    public void testGivenPlainAnswerThenParsed() {
        var parsed = DeepSeekChatTemplate.parseCompletion("Simple arithmetic.</think>2 + 2 = 4." + EOS, "thinking");
        assertEquals("Simple arithmetic.", parsed.reasoningContent());
        assertEquals("2 + 2 = 4.", parsed.content());
        assertTrue(parsed.toolCalls().isEmpty());
    }

    @Test
    public void testGivenImageBlockThenPlaceholderAndRecord() {
        var encoded = DeepSeekChatTemplate.encodeMessages(msgs("""
                [{"role":"user","content":[{"type":"text","text":"第一张图"},
                  {"type":"image_url","image_url":{"url":"a.jpeg"}},{"type":"text","text":"有什么内容？"}]}]"""),
                "chat", null, true, true, null);
        assertEquals(BOS + "<｜User｜>第一张图\n\n<｜deepseek_image｜>\n\n有什么内容？<｜Assistant｜></think>", encoded.prompt());
        assertEquals(List.of(Map.of("type", "image", "url", "a.jpeg")), encoded.images());
        assertThrows(IllegalArgumentException.class, () -> DeepSeekChatTemplate.encodeMessages(
                msgs("[{\"role\":\"user\",\"content\":\"x <｜deepseek_image｜>\"}]"), "chat"));
    }

    @Test
    public void testGivenMessageAdapterThenSameAsCore() {
        var template = new DeepSeekChatTemplate();
        var dialog = List.of(Message.system("You are a helpful assistant."), Message.user("What is 2+2?"));
        assertEquals(BOS + effort(75) + "You are a helpful assistant.<｜User｜>What is 2+2?<｜Assistant｜><think>",
                template.encode(dialog, ChatOptions.NONE.withThinkingMode(ChatOptions.THINKING_ENABLED)));
        assertEquals(BOS + SYS + "You are a helpful assistant.<｜User｜>What is 2+2?<｜Assistant｜></think>",
                template.encode(dialog));
        assertEquals(BOS + effort(100) + "You are a helpful assistant.<｜User｜>What is 2+2?<｜Assistant｜><think>",
                template.encode(dialog, ChatOptions.NONE.withThinkingMode(ChatOptions.THINKING_ENABLED).withReasoningEffort("xhigh")));
    }

    @Test
    public void testGivenToolRoundTripThroughAdapterThenResultFoldedIntoUserTurn() {
        var template = new DeepSeekChatTemplate();
        ToolDefinition tool = ToolDefinition.function(new FunctionDefinition("lookup", "Look up a value",
                Map.of("type", "object", "properties", Map.of("query", Map.of("type", "string")))));
        var options = new ChatOptions(new ToolDefinition[]{tool}, ToolChoice.AUTO, true);
        var dialog = new ArrayList<Message>();
        dialog.add(Message.user("find it"));
        dialog.add(Message.assistant("", List.of(ToolCall.function("c1", new FunctionCall("lookup", "{\"query\":\"x\"}")))));
        dialog.add(Message.tool("c1", "found"));
        String prompt = template.encode(dialog, options);
        assertTrue(prompt.startsWith(BOS + SYS + "\n\n## Tools\n"), prompt.substring(0, 40));
        assertTrue(prompt.contains("<｜User｜>find it<｜Assistant｜></think><｜DSML｜ calls>") == false
                || prompt.contains("<｜Assistant｜></think>"));
        assertTrue(prompt.contains("\n\n<｜DSML｜ calls>\n<｜DSML｜ invoke name=\"lookup\">\n"
                + "<｜DSML｜ parameter name=\"query\" string=\"true\">x</｜DSML｜ parameter>\n</｜DSML｜ invoke>\n</｜DSML｜ calls>" + EOS));
        assertTrue(prompt.endsWith("<｜User｜><tool_result>found</tool_result><｜Assistant｜></think>"));
    }
}
