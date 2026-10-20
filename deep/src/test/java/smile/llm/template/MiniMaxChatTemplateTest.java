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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import smile.llm.ChatOptions;
import smile.llm.FunctionCall;
import smile.llm.FunctionDefinition;
import smile.llm.Message;
import smile.llm.Role;
import smile.llm.TextPart;
import smile.llm.ToolCall;
import smile.llm.ToolChoice;
import smile.llm.ToolDefinition;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Compares {@link MiniMaxChatTemplate} with prompts rendered from the real MiniMax-M3
 * {@code chat_template.jinja} by Hugging Face transformers (golden file generated offline).
 *
 * @author Haifeng Li
 */
public class MiniMaxChatTemplateTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @SuppressWarnings("unchecked")
    private static ToolDefinition[] tools(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        List<ToolDefinition> list = new ArrayList<>();
        for (JsonNode t : node) {
            JsonNode fn = t.get("function");
            Map<String, Object> params = fn.has("parameters") ? MAPPER.convertValue(fn.get("parameters"), Map.class) : null;
            String desc = fn.has("description") ? fn.get("description").asString() : null;
            list.add(new ToolDefinition("function", new FunctionDefinition(fn.get("name").asString(), desc, params)));
        }
        return list.toArray(ToolDefinition[]::new);
    }

    private static List<Message> messages(JsonNode node) {
        List<Message> out = new ArrayList<>();
        for (JsonNode m : node) {
            Role role = Role.valueOf(m.get("role").asString());
            String content = m.has("content") && !m.get("content").isNull() ? m.get("content").asString() : "";
            List<ToolCall> calls = null;
            if (m.has("tool_calls")) {
                calls = new ArrayList<>();
                for (JsonNode c : m.get("tool_calls")) {
                    calls.add(ToolCall.function(c.get("id").asString(),
                            new FunctionCall(c.get("function").get("name").asString(),
                                    c.get("function").get("arguments").asString())));
                }
            }
            String callId = m.has("tool_call_id") ? m.get("tool_call_id").asString() : null;
            boolean bare = calls == null && callId == null;
            List<smile.llm.ContentPart> parts = content.isEmpty() && !bare ? List.of() : List.of(new TextPart(content));
            out.add(new Message(role, parts, calls, callId, null));
        }
        return out;
    }

    @Test
    public void testGivenGoldenDialogsWhenEncodedThenMatchesJinjaTemplateExactly() throws IOException {
        JsonNode cases = MAPPER.readTree(new File("deep/src/test/resources/minimax/chat_template_goldens.json"));
        assertTrue(cases.size() >= 15);
        for (JsonNode c : cases) {
            String mode = c.get("thinking_mode").isNull() ? null : c.get("thinking_mode").asString();
            ToolDefinition[] tools = tools(c.get("tools"));
            ChatOptions options = new ChatOptions(tools, ToolChoice.AUTO, true, mode);
            MiniMaxChatTemplate template = new MiniMaxChatTemplate(c.get("add_generation_prompt").asBoolean(), mode);
            String expected = c.get("expected").asString();
            String actual = template.encode(messages(c.get("messages")), options);
            assertEquals(expected, actual, "case " + c.get("name").asString());
        }
    }

    @Test
    public void testGivenToolMessageWithoutPriorToolCallWhenEncodedThenFails() {
        var dialog = List.of(new Message(Role.user, "hi"),
                new Message(Role.tool, List.of(new TextPart("x")), null, "c1", null));
        assertThrows(IllegalArgumentException.class, () -> new MiniMaxChatTemplate().encode(dialog, null));
    }

    @Test
    public void testGivenThinkTagsInAssistantHistoryWhenEncodedThenTreatedAsMiniMaxTags() {
        var withMm = List.of(new Message(Role.user, "q"),
                new Message(Role.assistant, "<mm:think>why</mm:think>because"), new Message(Role.user, "n"));
        var withPlain = List.of(new Message(Role.user, "q"),
                new Message(Role.assistant, "<think>why</think>because"), new Message(Role.user, "n"));
        assertEquals(new MiniMaxChatTemplate().encode(withMm, null), new MiniMaxChatTemplate().encode(withPlain, null));
    }

    @Test
    public void testGivenPythonFloatsWhenFormattedThenMatchesRepr() {
        assertEquals("1.0", PyJson.floatRepr(1.0));
        assertEquals("0.5", PyJson.floatRepr(0.5));
        assertEquals("1e-05", PyJson.floatRepr(1e-5));
        assertEquals("0.0001", PyJson.floatRepr(1e-4));
        assertEquals("1.5e+20", PyJson.floatRepr(1.5e20));
        assertEquals("1e+16", PyJson.floatRepr(1e16));
        assertEquals("123456789012345.0", PyJson.floatRepr(123456789012345.0));
        assertEquals("-2.5", PyJson.floatRepr(-2.5));
        assertEquals("3.14159", PyJson.floatRepr(3.14159));
    }
}
