/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
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
 * Compares {@link GlmChatTemplate} with the rendering of the real GLM-5.3-Flash {@code chat_template.jinja} by
 * transformers ({@code gen_glm_goldens.py}): system, history, think blocks, clear_thinking, reasoning efforts,
 * tools, tool calls with every argument type, out-of-order tool results and media.
 *
 * @author Haifeng Li
 */
public class GlmChatTemplateTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static String resource(String name) throws IOException {
        try (InputStream in = GlmChatTemplateTest.class.getResourceAsStream("/glm/" + name)) {
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

    /** Dialogs the {@link Message} model cannot express (tool calls without an id). */
    private static boolean expressible(JsonNode c) {
        for (JsonNode m : c.get("messages")) {
            for (JsonNode call : m.path("tool_calls")) {
                if (!call.has("id")) {
                    return false;
                }
            }
        }
        return true;
    }

    private static List<Message> messages(JsonNode array) throws Exception {
        List<Message> out = new ArrayList<>();
        for (JsonNode m : array) {
            Role role = Role.valueOf(m.get("role").asString());
            List<ToolCall> calls = null;
            if (m.has("tool_calls")) {
                calls = new ArrayList<>();
                for (JsonNode c : m.get("tool_calls")) {
                    JsonNode fn = c.get("function");
                    calls.add(ToolCall.function(c.get("id").asString(), new FunctionCall(
                            fn.get("name").asString(), MAPPER.writeValueAsString(fn.get("arguments")))));
                }
            }
            List<ContentPart> parts = parts(m.get("content"));
            boolean empty = parts.stream().allMatch(p -> p instanceof TextPart t && t.text().isEmpty());
            if (role == Role.tool) {
                String id = m.has("tool_call_id") ? m.get("tool_call_id").asString() : null;
                out.add(new Message(role, parts, null, id, null));
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
        if (kw.has("reasoning_effort")) {
            options = options.withReasoningEffort(kw.get("reasoning_effort").asString());
        }
        if (kw.has("clear_thinking")) {
            // clear_thinking=true <=> preserve_thinking=false
            options = options.withPreserveThinking(!kw.get("clear_thinking").asBoolean());
        }
        return options;
    }

    @Test
    public void testGivenGoldenDialogsWhenRenderedThenMatchesTransformers() throws Exception {
        JsonNode goldens = MAPPER.readTree(resource("chat_template_goldens.json"));
        assertTrue(goldens.size() >= 35);
        GlmChatTemplate template = new GlmChatTemplate();
        int checked = 0;
        for (JsonNode c : goldens) {
            if (!expressible(c)) {
                continue;
            }
            assertFalse(c.has("error"), c.get("name").asString());
            assertEquals(c.get("text").asString(), template.encode(messages(c.get("messages")), options(c)),
                    c.get("name").asString());
            checked++;
        }
        assertTrue(checked >= goldens.size() - 2, "checked " + checked + " of " + goldens.size());
    }

    @Test
    public void testGivenNoGenerationPromptThenNoOpenTurn() {
        String text = new GlmChatTemplate(false).encode(List.of(Message.user("Hi")), null);
        assertEquals("[gMASK]<sop><|system|>Reasoning Effort: Max<|user|>Hi", text);
    }

    @Test
    public void testGivenReasoningContentInAssistantTextThenSplitLikeTheTemplate() {
        List<Message> dialog = List.of(Message.user("q"), Message.assistant("<think>why</think>\n  because \n"),
                Message.user("q2"));
        assertEquals("[gMASK]<sop><|system|>Reasoning Effort: Max<|user|>q<|assistant|><think>why</think>because"
                + "<|user|>q2<|assistant|><think>", new GlmChatTemplate().encode(dialog, null));
        assertEquals("[gMASK]<sop><|system|>Reasoning Effort: Max<|user|>q<|assistant|><think></think>because"
                + "<|user|>q2<|assistant|><think>",
                new GlmChatTemplate().encode(dialog, ChatOptions.NONE.withPreserveThinking(false)));
    }
}
