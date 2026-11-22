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
package smile.llm.model.inkling;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;
import smile.llm.*;
import smile.llm.template.InklingChatTemplate;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks {@link InklingTokenizer} against token ids from the Hugging Face tokenizer. Needs the checkpoint's
 * {@code tokenizer.json}: set {@code INKLING_CKPT} (default {@code /raid/haili/inkling/raw}); the test is skipped
 * without it.
 *
 * @author Haifeng Li
 */
public class InklingTokenizerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static String checkpoint() {
        String dir = System.getenv().getOrDefault("INKLING_CKPT", "/raid/haili/inkling/raw");
        Assumptions.assumeTrue(new File(dir, "tokenizer.json").exists(), "no Inkling checkpoint at " + dir);
        return dir;
    }

    private static int[] ids(JsonNode node) {
        int[] out = new int[node.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = node.get(i).asInt();
        }
        return out;
    }

    @Test
    public void testGivenTextsWhenEncodedThenMatchesHuggingFaceIds() throws IOException {
        InklingTokenizer tokenizer = InklingTokenizer.of(checkpoint());
        JsonNode cases = MAPPER.readTree(new File("deep/src/test/resources/inkling/tokenizer_goldens.json"));
        for (JsonNode c : cases) {
            String text = c.get("text").asString();
            assertArrayEquals(ids(c.get("ids")), tokenizer.encodeWithSpecials(text), "text " + text);
            assertEquals(text, tokenizer.decode(ids(c.get("ids"))).replace("�", "�"), "round trip " + text);
        }
    }

    @Test
    public void testGivenGoldenPromptsWhenEncodedThenMatchesHuggingFaceIds() throws IOException {
        InklingTokenizer tokenizer = InklingTokenizer.of(checkpoint());
        JsonNode cases = MAPPER.readTree(new File("deep/src/test/resources/inkling/chat_template_goldens.json"));
        int n = 0;
        for (JsonNode c : cases) {
            if (c.has("error")) {
                continue;
            }
            n++;
            assertArrayEquals(ids(c.get("ids")), tokenizer.encodeWithSpecials(c.get("expected").asString()),
                    "prompt of case " + c.get("name").asString());
        }
        assertTrue(n >= 40);
    }

    @Test
    public void testGivenSpecialTokensWhenLookedUpThenIdsMatchConfig() throws IOException {
        InklingTokenizer tokenizer = InklingTokenizer.of(checkpoint());
        assertEquals(200006, tokenizer.specialToken(InklingTokenizer.EOS));
        assertEquals(199999, tokenizer.pad());
        assertEquals(200001, tokenizer.specialToken(InklingTokenizer.MESSAGE_MODEL));
        assertEquals(200010, tokenizer.specialToken(InklingTokenizer.END_MESSAGE));
        assertEquals(200054, tokenizer.specialToken(InklingTokenizer.IMAGE_TOKEN));
        assertArrayEquals(new int[]{200006}, tokenizer.stopTokens());
    }

    @Test
    public void testGivenReplyTokensWhenDecodedSkippingSpecialsThenOnlyTextRemains() throws Exception {
        InklingTokenizer tokenizer = InklingTokenizer.of(checkpoint());
        int[] reply = tokenizer.encodeWithSpecials(
                "<|message_model|><|content_thinking|>hmm<|end_message|><|message_model|><|content_text|>Hi!<|end_message|><|content_model_end_sampling|>");
        assertEquals("<think>hmm</think>Hi!", tokenizer.tryDecode(reply, true));
        assertTrue(tokenizer.tryDecode(reply, false).startsWith("<|message_model|><|content_thinking|>hmm"));

        // no reasoning: a leading </think> the sanitizer drops
        int[] plain = tokenizer.encodeWithSpecials("<|message_model|><|content_text|>Hi<|end_message|><|message_model|><|content_text|>!<|end_message|>");
        assertEquals("</think>Hi!", tokenizer.tryDecode(plain, true));

        // a tool call: the header's name is dropped, the JSON body is wrapped in <tool_call>
        int[] call = tokenizer.encodeWithSpecials("<|message_model|><|content_thinking|>t<|end_message|><|message_model|>get_weather"
                + "<|content_invoke_tool_json|>{\"name\":\"get_weather\",\"args\":{\"city\":\"Paris\"}}<|end_message|>"
                + "<|content_model_end_sampling|>");
        String rendered = tokenizer.tryDecode(call, true);
        assertEquals("<think>t</think><tool_call>{\"name\":\"get_weather\",\"args\":{\"city\":\"Paris\"}}</tool_call>", rendered);
        var parsed = smile.llm.tool.ToolCallPostProcessor.apply(
                new smile.llm.ChatCompletion("m", rendered, new int[0], new int[0], smile.llm.FinishReason.stop, null),
                new smile.llm.ChatOptions(new smile.llm.ToolDefinition[]{smile.llm.ToolDefinition.function(
                        new smile.llm.FunctionDefinition("get_weather", "", java.util.Map.of()))}, smile.llm.ToolChoice.AUTO, true));
        assertEquals(1, parsed.toolCalls().size());
        assertEquals("get_weather", parsed.toolCalls().getFirst().function().name());
        assertEquals("{\"city\":\"Paris\"}", parsed.toolCalls().getFirst().function().arguments());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGivenOracleDialogsWhenEncodedThenPromptIdsMatchTheOracleRun() throws IOException {
        InklingTokenizer tokenizer = InklingTokenizer.of(checkpoint());
        File out = new File("/raid/haili/inkling/oracle/out_full.json");
        File dialogs = new File("/raid/haili/inkling/oracle/dialogs.json");
        Assumptions.assumeTrue(out.exists() && dialogs.exists(), "no oracle output");
        JsonNode results = MAPPER.readTree(out).get("results");
        JsonNode input = MAPPER.readTree(dialogs);
        assertEquals(input.size(), results.size());
        for (int k = 0; k < input.size(); k++) {
            JsonNode d = input.get(k);
            List<Message> messages = new ArrayList<>();
            for (JsonNode m : d.get("messages")) {
                List<ToolCall> calls = null;
                if (m.has("tool_calls")) {
                    calls = new ArrayList<>();
                    for (JsonNode c : m.get("tool_calls")) {
                        calls.add(ToolCall.function(c.get("id").asString(), new FunctionCall(
                                c.get("function").get("name").asString(), c.get("function").get("arguments").toString())));
                    }
                }
                String content = m.get("content").asString();
                List<ContentPart> parts = content.isEmpty() && calls != null ? List.of() : List.of(new TextPart(content));
                messages.add(new Message(Role.valueOf(m.get("role").asString()), parts, calls,
                        m.has("tool_call_id") ? m.get("tool_call_id").asString() : null, null));
            }
            ToolDefinition[] tools = null;
            if (d.has("tools")) {
                tools = new ToolDefinition[d.get("tools").size()];
                for (int i = 0; i < tools.length; i++) {
                    JsonNode fn = d.get("tools").get(i).get("function");
                    tools[i] = ToolDefinition.function(new FunctionDefinition(fn.get("name").asString(),
                            fn.get("description").asString(), MAPPER.convertValue(fn.get("parameters"), Map.class)));
                }
            }
            ChatOptions options = new ChatOptions(tools, ToolChoice.AUTO, true);
            if (d.has("reasoning_effort")) {
                options = options.withReasoningEffort(d.get("reasoning_effort").asString());
            }
            String prompt = new InklingChatTemplate().encode(messages, options);
            assertArrayEquals(ids(results.get(k).get("prompt_ids")), tokenizer.encodeWithSpecials(prompt),
                    "dialog " + d.get("name").asString());
        }
    }
}
