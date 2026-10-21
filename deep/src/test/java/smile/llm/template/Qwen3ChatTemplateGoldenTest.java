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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import smile.llm.ChatOptions;
import smile.llm.ContentPart;
import smile.llm.FunctionCall;
import smile.llm.FunctionDefinition;
import smile.llm.ImageUrlPart;
import smile.llm.Message;
import smile.llm.Role;
import smile.llm.TextPart;
import smile.llm.ToolCall;
import smile.llm.ToolChoice;
import smile.llm.ToolDefinition;
import smile.llm.VideoUrlPart;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Compares {@link Qwen3ChatTemplate} with the rendering of the real Qwen3.5 (small and large) and Qwen3.8
 * {@code chat_template.jinja} by transformers ({@code gen_qwen_goldens.py}): 41 dialogs covering system turns,
 * history, think blocks, tools, tool calls and results, thinking and reasoning-effort switches, whitespace and media.
 *
 * @author Haifeng Li
 */
public class Qwen3ChatTemplateGoldenTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<String, String> TEMPLATE_FILES = Map.of(
            "qwen3.5-0.8b", "Qwen3.5-0.8B.jinja",
            "qwen3.5-27b", "Qwen3.5-27B.jinja",
            "qwen3.8", "Qwen3.8-27B.jinja");

    private static String resource(String name) throws IOException {
        try (InputStream in = Qwen3ChatTemplateGoldenTest.class.getResourceAsStream("/qwen/" + name)) {
            assertNotNull(in, name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<ContentPart> parts(JsonNode content) {
        List<ContentPart> out = new ArrayList<>();
        if (content.isString()) {
            out.add(new TextPart(content.asString()));
            return out;
        }
        for (JsonNode item : content) {
            switch (item.path("type").asString()) {
                case "text" -> out.add(new TextPart(item.get("text").asString()));
                case "image" -> out.add(new ImageUrlPart("x"));
                case "video" -> out.add(new VideoUrlPart("x", null));
                default -> fail("unknown part " + item);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Message> messages(JsonNode array) throws Exception {
        List<Message> out = new ArrayList<>();
        int n = 0;
        for (JsonNode m : array) {
            Role role = Role.valueOf(m.get("role").asString());
            List<ToolCall> calls = null;
            if (m.has("tool_calls")) {
                calls = new ArrayList<>();
                for (JsonNode c : m.get("tool_calls")) {
                    JsonNode fn = c.get("function");
                    calls.add(ToolCall.function("call_" + (++n), new FunctionCall(
                            fn.get("name").asString(), MAPPER.writeValueAsString(fn.get("arguments")))));
                }
            }
            List<ContentPart> parts = parts(m.get("content"));
            boolean empty = parts.stream().allMatch(p -> p instanceof TextPart t && t.text().isEmpty());
            if (role == Role.tool) {
                out.add(Message.tool("call_" + (++n), m.get("content").asString()));
            } else if (calls != null && empty) {
                out.add(new Message(role, List.of(), calls, null, null));
            } else {
                out.add(new Message(role, parts, calls, null, null));
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static ChatOptions options(JsonNode c) throws Exception {
        ToolDefinition[] tools = null;
        if (c.has("tools") && !c.get("tools").isNull()) {
            List<ToolDefinition> defs = new ArrayList<>();
            for (JsonNode t : c.get("tools")) {
                JsonNode fn = t.get("function");
                Map<String, Object> params = MAPPER.readValue(MAPPER.writeValueAsString(fn.get("parameters")), Map.class);
                defs.add(ToolDefinition.function(new FunctionDefinition(
                        fn.get("name").asString(), fn.get("description").asString(), params)));
            }
            tools = defs.toArray(ToolDefinition[]::new);
        }
        ChatOptions options = new ChatOptions(tools, ToolChoice.AUTO, true);
        JsonNode kw = c.get("kwargs");
        if (kw.has("enable_thinking")) {
            options = options.withThinkingMode(kw.get("enable_thinking").asBoolean()
                    ? ChatOptions.THINKING_ENABLED : ChatOptions.THINKING_DISABLED);
        }
        if (kw.has("reasoning_effort")) {
            options = options.withReasoningEffort(kw.get("reasoning_effort").asString());
        }
        if (kw.has("preserve_thinking")) {
            options = options.withPreserveThinking(kw.get("preserve_thinking").asBoolean());
        }
        return options;
    }

    @Test
    public void testGivenReleasedTemplatesWhenDetectedThenVariantsDiffer() throws Exception {
        assertEquals(Qwen3ChatTemplate.Variant.QWEN3_8, Qwen3ChatTemplate.Variant.detect(resource("Qwen3.8-27B.jinja")));
        assertEquals(Qwen3ChatTemplate.Variant.QWEN3_5, Qwen3ChatTemplate.Variant.detect(resource("Qwen3.5-27B.jinja")));
        assertEquals(Qwen3ChatTemplate.Variant.QWEN3_5_SMALL,
                Qwen3ChatTemplate.Variant.detect(resource("Qwen3.5-0.8B.jinja")));
    }

    @Test
    public void testGivenGoldenDialogsWhenRenderedThenMatchesTransformersForEveryTemplate() throws Exception {
        JsonNode goldens = MAPPER.readTree(resource("chat_template_goldens.json"));
        assertTrue(goldens.size() >= 40);
        int checked = 0;
        for (var entry : TEMPLATE_FILES.entrySet()) {
            Qwen3ChatTemplate template = new Qwen3ChatTemplate(
                    Qwen3ChatTemplate.Variant.detect(resource(entry.getValue())), true);
            for (JsonNode c : goldens) {
                String where = entry.getKey() + " / " + c.get("name").asString();
                JsonNode expected = c.get("expected").get(entry.getKey());
                List<Message> dialog = messages(c.get("messages"));
                ChatOptions options = options(c);
                if (expected.has("error")) {
                    assertThrows(IllegalArgumentException.class, () -> template.encode(dialog, options), where);
                } else {
                    assertEquals(expected.get("text").asString(), template.encode(dialog, options), where);
                }
                checked++;
            }
        }
        assertEquals(3 * goldens.size(), checked);
    }
}
