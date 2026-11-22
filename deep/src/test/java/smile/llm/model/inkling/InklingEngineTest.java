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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.llm.ChatCompletion;
import smile.llm.ChatOptions;
import smile.llm.Message;
import smile.llm.engine.GenerationHandle;
import smile.llm.engine.GenerationRequest;
import smile.llm.engine.InferenceEngine;
import smile.llm.tool.ToolCallPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Inkling-2-Mini behind {@link InferenceEngine}: concurrent requests with chunked prefill and batched decode steps,
 * the reply rendering the server pipeline reads, and the tool-call post-processing the chat endpoint applies.
 *
 * <p>Opt-in, GPU: {@code INKLING_CKPT}, {@code INKLING_DIALOGS}, {@code INKLING_DEVICE}, {@code INKLING_TOKENS}
 * (default 200), {@code INKLING_BUDGET} (prefill tokens per tick, default 64 so the 137- and 161-token prompts are chunked).
 *
 * @author Haifeng Li
 */
public class InklingEngineTest {
    @Test
    public void testGivenConcurrentDialogsWhenServedThroughTheEngineThenRepliesAreCoherent() throws Exception {
        String ckpt = System.getenv("INKLING_CKPT");
        String dialogs = System.getenv("INKLING_DIALOGS");
        Assumptions.assumeTrue(ckpt != null && dialogs != null && new File(dialogs).exists(), "set INKLING_CKPT and INKLING_DIALOGS");
        int tokens = Integer.parseInt(System.getenv().getOrDefault("INKLING_TOKENS", "200"));
        int budget = Integer.parseInt(System.getenv().getOrDefault("INKLING_BUDGET", "64"));
        byte device = Byte.parseByte(System.getenv().getOrDefault("INKLING_DEVICE", "0"));
        JsonNode input = new ObjectMapper().readTree(new File(dialogs));
        try (Inkling model = Inkling.build(ckpt, 0, 8192, device)) {
            var engine = new InferenceEngine(model, 3, 3, budget, 0);
            List<GenerationHandle> handles = new ArrayList<>();
            List<ChatOptions> options = new ArrayList<>();
            long t0 = System.nanoTime();
            for (JsonNode d : input) {
                List<Message> messages = new ArrayList<>();
                for (JsonNode m : d.get("messages")) {
                    messages.add(InklingGenerateTest.message(m));
                }
                ChatOptions o = InklingGenerateTest.options(d);
                options.add(o);
                int[] ids = model.encodeChat(messages.toArray(Message[]::new), o);
                handles.add(engine.submit(GenerationRequest.ofTokens(ids, tokens, 0.0, 1.0, false, 0, null)));
            }
            long generated = 0;
            List<ChatCompletion> results = new ArrayList<>();
            for (GenerationHandle h : handles) {                 // wait for all before asserting: closing the model fails the rest
                results.add(h.future().get(30, TimeUnit.MINUTES));
            }
            for (int i = 0; i < results.size(); i++) {
                ChatCompletion c = results.get(i);
                generated += c.completionTokens().length;
                String name = input.get(i).get("name").asString();
                String raw = model.tryDecode(c.completionTokens(), true);
                ChatCompletion post = ToolCallPostProcessor.apply(
                        new ChatCompletion(c.model(), raw, c.promptTokens(), c.completionTokens(), c.reason(), null), options.get(i));
                System.out.printf("--- [%s] %d prompt, %d generated, finish %s%n    raw: %s%n    => content: %s%n    => tool calls: %s%n", name,
                        c.promptTokens().length, c.completionTokens().length, c.reason(), raw.replace('\n', ' '),
                        post.content(), post.toolCalls());
                assertTrue(c.completionTokens().length > 0, "empty completion of " + name);
                assertTrue(raw.contains("<think>"), "no reasoning in " + name);
                assertFalse(c.content().contains("<|") || c.content().contains("<think>"), "markup in the content of " + name);
                if (name.equals("tools")) {
                    assertTrue(post.hasToolCalls(), "no tool call parsed for " + name + ": " + raw);
                    assertEquals("get_weather", post.toolCalls().getFirst().function().name());
                    assertTrue(post.toolCalls().getFirst().function().arguments().contains("Paris"));
                } else {
                    assertFalse(post.content() == null || post.content().isBlank() && !name.equals("tool_result"),
                            "no visible answer for " + name);
                }
            }
            double s = (System.nanoTime() - t0) / 1e9;
            System.out.printf("%d requests, %d tokens in %.1f s (%.1f tokens/s aggregate)%n", handles.size(), generated, s, generated / s);
        }
    }
}
