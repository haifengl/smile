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
package smile.llm.model.minimax;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import smile.llm.ChatCompletion;
import smile.llm.ChatOptions;
import smile.llm.FunctionDefinition;
import smile.llm.Message;
import smile.llm.Role;
import smile.llm.ToolChoice;
import smile.llm.ToolDefinition;
import smile.llm.engine.GenerationRequest;
import smile.llm.engine.InferenceEngine;
import smile.llm.tool.ToolCallPostProcessor;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Opt-in end-to-end smoke test of the serving path without HTTP: real tokenizer and chat template,
 * {@link InferenceEngine} continuous batching with prefix reuse, tool options and post-processing.
 * Environment: {@code MINIMAX_MODEL_DIR}, {@code MINIMAX_DEVICES}.
 *
 * @author Haifeng Li
 */
public class MiniMaxEngineSmokeTest {
    @Test
    public void serve() throws Exception {
        String dir = System.getenv("MINIMAX_MODEL_DIR");
        String devList = System.getenv("MINIMAX_DEVICES");
        assumeTrue(dir != null && devList != null, "MINIMAX_* not set");
        String[] ds = devList.split(",");
        byte[] devices = new byte[ds.length];
        for (int i = 0; i < ds.length; i++) devices[i] = Byte.parseByte(ds[i].trim());

        try (MiniMax m = MiniMax.build(dir, 4, 4096, devices, 0.7, 1)) {
            m.setPrefixReuseEnabled(true);
            InferenceEngine engine = new InferenceEngine(m, 4, 4, 2048, 30_000, 0);
            ToolDefinition weather = new ToolDefinition("function", new FunctionDefinition("get_weather", "Weather",
                    Map.of("type", "object", "properties", Map.of("city", Map.of("type", "string")))));
            ChatOptions tools = new ChatOptions(new ToolDefinition[]{weather}, ToolChoice.AUTO, true, "adaptive");

            String system = "You are a careful assistant. ".repeat(40);   // long shared prefix
            List<int[]> prompts = new ArrayList<>();
            for (String q : new String[]{"What is 2+2?", "Name a color.", "Say hi."}) {
                prompts.add(m.encodeChat(new Message[]{new Message(Role.system, system), new Message(Role.user, q)}, tools));
            }
            // The same prompt twice: the second run must hit the radix cache and give the same tokens.
            int[] same = prompts.getFirst();

            List<ChatCompletion> results = new ArrayList<>();
            for (int[] p : prompts) {
                var handle = engine.submit(GenerationRequest.ofTokens(p, 12, 0.0, 1.0, false, 0, null, tools));
                results.add(handle.future().get(120, TimeUnit.SECONDS));
            }
            var again = engine.submit(GenerationRequest.ofTokens(same, 12, 0.0, 1.0, false, 0, null, tools))
                    .future().get(120, TimeUnit.SECONDS);
            assertArrayEquals(results.getFirst().completionTokens(), again.completionTokens(),
                    "prefix-reused request must reproduce the first run");

            // Concurrent requests share decode batches.
            List<java.util.concurrent.CompletableFuture<ChatCompletion>> futures = new ArrayList<>();
            for (int[] p : prompts) {
                futures.add(engine.submit(GenerationRequest.ofTokens(p, 8, 0.0, 1.0, false, 0, null, tools)).future());
            }
            for (int i = 0; i < futures.size(); i++) {
                ChatCompletion c = futures.get(i).get(120, TimeUnit.SECONDS);
                assertArrayEquals(java.util.Arrays.copyOf(results.get(i).completionTokens(),
                        Math.min(8, results.get(i).completionTokens().length)),
                        java.util.Arrays.copyOf(c.completionTokens(),
                                Math.min(8, results.get(i).completionTokens().length)),
                        "batched decode must match the single-request run");
                ChatCompletion cleaned = ToolCallPostProcessor.apply(c, tools);
                assertNotNull(cleaned);
                String visible = cleaned.content();
                assertTrue(visible == null || (!visible.contains("[e~[") && !visible.contains("]~b]")),
                        "control tokens must not leak: " + visible);
            }
            System.out.println("SMOKE ok: prompt tokens " + prompts.stream().mapToInt(p -> p.length).boxed().toList()
                    + ", first completion \"" + results.getFirst().content() + "\"");
            engine.close();
        }
    }
}
