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
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import smile.llm.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Compares {@link InklingChatTemplate} with prompts rendered from the real Inkling-2-Mini
 * {@code chat_template.jinja} by Hugging Face transformers (goldens from {@code gen_goldens.py}).
 *
 * @author Haifeng Li
 */
public class InklingChatTemplateTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @SuppressWarnings("unchecked")
    private static ToolDefinition[] tools(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        List<ToolDefinition> list = new ArrayList<>();
        for (JsonNode t : node) {
            JsonNode fn = t.has("function") ? t.get("function") : t;
            Map<String, Object> params = fn.has("parameters") ? MAPPER.convertValue(fn.get("parameters"), Map.class) : null;
            String desc = fn.has("description") ? fn.get("description").asString() : null;
            String type = t.has("type") ? t.get("type").asString() : "function";
            list.add(new ToolDefinition(type, new FunctionDefinition(fn.get("name").asString(), desc, params)));
        }
        return list.toArray(ToolDefinition[]::new);
    }

    private static List<ContentPart> parts(JsonNode content) {
        if (content.isString()) {
            return List.of(new TextPart(content.asString()));
        }
        List<ContentPart> parts = new ArrayList<>();
        for (JsonNode p : content) {
            if (p.isString()) {
                parts.add(new TextPart(p.asString()));
                continue;
            }
            switch (p.get("type").asString()) {
                case "text", "input_text" -> parts.add(new TextPart(p.has("text") ? p.get("text").asString() : ""));
                case "image", "input_image", "image_url" -> parts.add(new ImageUrlPart("x"));
                case "audio" -> parts.add(new AudioUrlPart("x"));
                default -> parts.add(new VideoUrlPart("x", null));
            }
        }
        return parts;
    }

    private static List<Message> messages(JsonNode node) {
        List<Message> out = new ArrayList<>();
        for (JsonNode m : node) {
            Role role = Role.valueOf(m.get("role").asString());
            List<ContentPart> parts = m.has("content") && !m.get("content").isNull() ? parts(m.get("content")) : List.of();
            if (m.has("reasoning_content") && !m.get("reasoning_content").asString().isEmpty()) {
                // The Java Message has no reasoning field: reasoning rides in front of the text as <think> tags.
                String text = parts.isEmpty() ? "" : ((TextPart) parts.getFirst()).text();
                parts = List.of(new TextPart("<think>" + m.get("reasoning_content").asString() + "</think>" + text));
            }
            List<ToolCall> calls = null;
            if (m.has("tool_calls")) {
                calls = new ArrayList<>();
                for (JsonNode c : m.get("tool_calls")) {
                    calls.add(ToolCall.function(c.get("id").asString(), new FunctionCall(
                            c.get("function").get("name").asString(), c.get("function").get("arguments").toString())));
                }
            }
            String callId = m.has("tool_call_id") ? m.get("tool_call_id").asString() : null;
            String name = m.has("name") ? m.get("name").asString() : null;
            out.add(new Message(role, parts, calls, callId, name));
        }
        return out;
    }

    private static ChatOptions options(JsonNode c) {
        ChatOptions options = new ChatOptions(tools(c.get("tools")), ToolChoice.AUTO, true);
        if (c.has("reasoning_effort")) {
            JsonNode e = c.get("reasoning_effort");
            options = options.withReasoningEffort(e.isString() ? e.asString() : e.toString());
        }
        return options;
    }

    @Test
    public void testGivenGoldenDialogsWhenEncodedThenMatchesJinjaTemplateExactly() throws IOException {
        JsonNode cases = MAPPER.readTree(new File("deep/src/test/resources/inkling/chat_template_goldens.json"));
        assertTrue(cases.size() >= 40);
        int rendered = 0;
        int errors = 0;
        for (JsonNode c : cases) {
            String name = c.get("name").asString();
            InklingChatTemplate template = new InklingChatTemplate();
            if (c.has("error")) {
                errors++;
                assertThrows(IllegalArgumentException.class,
                        () -> template.encode(messages(c.get("messages")), options(c)), "case " + name);
            } else {
                rendered++;
                assertEquals(c.get("expected").asString(), template.encode(messages(c.get("messages")), options(c)),
                        "case " + name);
            }
        }
        assertTrue(rendered >= 40 && errors >= 7, rendered + " rendered, " + errors + " errors");
    }

    @Test
    public void testGivenThinkingDisabledWhenEncodedThenEffortIsNone() {
        var dialog = List.of(new Message(Role.user, "q"));
        String disabled = new InklingChatTemplate().encode(dialog,
                new ChatOptions(null, ToolChoice.AUTO, true, ChatOptions.THINKING_DISABLED));
        String none = new InklingChatTemplate().encode(dialog, ChatOptions.NONE.withReasoningEffort("none"));
        assertEquals(none, disabled);
        assertTrue(disabled.contains("Thinking effort level: 0<|end_message|>"));
        String dflt = new InklingChatTemplate().encode(dialog, null);
        assertTrue(dflt.contains("Thinking effort level: 0.9<|end_message|>"));
        // An explicit effort wins over the thinking mode.
        String explicit = new InklingChatTemplate().encode(dialog,
                new ChatOptions(null, ToolChoice.AUTO, true, ChatOptions.THINKING_DISABLED).withReasoningEffort("low"));
        assertTrue(explicit.contains("Thinking effort level: 0.2<|end_message|>"));
    }

    @Test
    public void testGivenNamedToolChoiceWhenEncodedThenOnlyThatToolIsDeclared() {
        var a = new ToolDefinition("function", new FunctionDefinition("a", "A", Map.of()));
        var b = new ToolDefinition("function", new FunctionDefinition("b", "B", Map.of()));
        var options = new ChatOptions(new ToolDefinition[]{a, b}, new ToolChoice.Named("b"), true);
        String prompt = new InklingChatTemplate().encode(List.of(new Message(Role.user, "q")), options);
        assertTrue(prompt.contains("\"name\":\"b\""));
        assertFalse(prompt.contains("\"name\":\"a\""));
    }

    @Test
    public void testGivenEmptyDialogWhenEncodedThenFails() {
        assertThrows(IllegalArgumentException.class, () -> new InklingChatTemplate().encode(List.of(), null));
    }
}
