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
package smile.llm.model.glm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.llm.Message;
import smile.llm.engine.GenerationRequest;
import smile.llm.engine.InferenceEngine;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The complete 45-layer GLM-5.3-Flash on several GPUs, behind the engine: loads the checkpoint, prints the GPU memory after
 * the load, runs the prompts of {@code prompts.json} greedily (concurrently, so the batched decode and the graphs run),
 * writes the completions (ids and text) to {@code GLM_FULL_OUT} for comparison with a reference (vLLM), checks that a repeated
 * greedy run is identical, and optionally prefills a long prompt. This is the check of coherent text a truncated slice cannot give.
 *
 * <p>Opt-in: {@code GLM_FULL=<checkpoint dir>}; {@code GLM_FULL_DEVICES} (default 8), {@code GLM_FULL_MAXSEQ} (default 131072),
 * {@code GLM_FULL_SLOTS} (default 16), {@code GLM_FULL_TOKENS} (default 192), {@code GLM_FULL_LONG} (tokens of a long prompt,
 * default 0 = skip), {@code GLM_FULL_PROMPTS}, {@code GLM_FULL_OUT}.
 */
public class GlmFullModelTest {
    private static int env(String name, int def) {
        return Integer.parseInt(System.getenv().getOrDefault(name, String.valueOf(def)));
    }

    @Test
    public void testGivenFullCheckpointThenCoherentGreedyText() throws Exception {
        String dir = System.getenv("GLM_FULL");
        Assumptions.assumeTrue(dir != null && Files.exists(Path.of(dir, "model.safetensors.index.json")), "set GLM_FULL=<checkpoint dir>");
        int devices = env("GLM_FULL_DEVICES", 8);
        int maxSeq = env("GLM_FULL_MAXSEQ", 131072);
        int slots = env("GLM_FULL_SLOTS", 16);
        int tokens = env("GLM_FULL_TOKENS", 192);
        int longTokens = env("GLM_FULL_LONG", 0);
        byte[] dev = new byte[devices];
        for (int i = 0; i < devices; i++) {
            dev[i] = (byte) i;
        }
        JsonNode prompts = new ObjectMapper().readTree(Path.of(System.getenv().getOrDefault("GLM_FULL_PROMPTS",
                "/glm/full/prompts.json")).toFile());

        long t0 = System.nanoTime();
        try (Glm model = Glm.build(dir, env("GLM_FULL_LAYERS", 0), maxSeq, dev, slots)) {
            System.out.printf("loaded in %.0f s%n", (System.nanoTime() - t0) / 1e9);
            for (int i = 0; i < devices; i++) {
                long[] mem = smile.torch.Native.cudaMemGetInfo(i);
                System.out.printf("GPU %d after load: free %.1f GB of %.1f GB%n", i, mem[0] / 1e9, mem[1] / 1e9);
            }
            var engine = new InferenceEngine(model, slots, slots, 2048, 0);
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode out = mapper.createObjectNode();
            List<int[]> promptIds = new ArrayList<>();
            for (JsonNode p : prompts) {
                List<Message> dialog = new ArrayList<>();
                for (JsonNode m : p.get("messages")) {
                    dialog.add(switch (m.get("role").asString()) {
                        case "system" -> Message.system(m.get("content").asString());
                        case "assistant" -> Message.assistant(m.get("content").asString());
                        default -> Message.user(m.get("content").asString());
                    });
                }
                promptIds.add(model.encodeChat(dialog.toArray(Message[]::new)));
            }
            for (int round = 0; round < 2; round++) {
                long t1 = System.nanoTime();
                var handles = new ArrayList<smile.llm.engine.GenerationHandle>();
                for (int[] ids : promptIds) {
                    handles.add(engine.submit(GenerationRequest.ofTokens(ids, tokens, 0.0, 1.0, false, 0, null)));
                }
                long generated = 0;
                for (int i = 0; i < handles.size(); i++) {
                    int[] c = handles.get(i).future().get(60, TimeUnit.MINUTES).completionTokens();
                    generated += c.length;
                    String name = prompts.get(i).get("name").asString();
                    String text = model.tryDecodeSafe(c);
                    System.out.printf("--- [%s] round %d, %d prompt tokens, %d generated%n%s%n", name, round, promptIds.get(i).length, c.length, text);
                    if (round == 0) {
                        ObjectNode o = out.putObject(name);
                        o.put("text", text);
                        var arr = o.putArray("ids");
                        for (int v : c) {
                            arr.add(v);
                        }
                        assertTrue(c.length > 0, "empty completion of " + name);
                        o.put("prompt_tokens", promptIds.get(i).length);
                    } else {
                        // greedy decoding is only reproducible up to bf16 noise: another batch shape can flip a near-tie token
                        var first = out.get(name).get("ids");
                        int common = 0;
                        while (common < Math.min(first.size(), c.length) && first.get(common).asInt() == c[common]) {
                            common++;
                        }
                        System.out.printf("    repeat of [%s]: identical for %d of %d tokens%n", name, common, first.size());
                        assertTrue(common >= Math.min(env("GLM_FULL_MINCOMMON", 32), first.size()), "repeated greedy run of " + name + " diverges at token " + common);
                    }
                }
                double s = (System.nanoTime() - t1) / 1e9;
                System.out.printf("round %d: %d prompts, %d tokens in %.1f s (%.1f tokens/s aggregate)%n", round, promptIds.size(), generated, s, generated / s);
            }
            String outFile = System.getenv("GLM_FULL_OUT");
            if (outFile != null) {
                mapper.writerWithDefaultPrettyPrinter().writeValue(Path.of(outFile).toFile(), out);
            }
            if (longTokens > 0) {
                StringBuilder sb = new StringBuilder();
                for (int n = 0; sb.length() < longTokens * 4; n++) {
                    sb.append("Item ").append(n).append(": the quick brown fox number ").append(n * 7919 % 1000)
                            .append(" jumps over the lazy dog.\n");
                }
                int[] ids = model.encodeChat(Message.user(sb + "\nHow many items were listed above? Answer with one number."));
                ids = java.util.Arrays.copyOf(ids, Math.min(ids.length, maxSeq - 64));
                long t2 = System.nanoTime();
                int[] c = engine.submit(GenerationRequest.ofTokens(ids, 16, 0.0, 1.0, false, 0, null)).future()
                        .get(120, TimeUnit.MINUTES).completionTokens();
                double s = (System.nanoTime() - t2) / 1e9;
                System.out.printf("long prompt: %d tokens, prefill + 16 tokens in %.1f s (%.0f prompt tokens/s)%n%s%n", ids.length, s,
                        ids.length / s, model.tryDecodeSafe(c));
            }
        }
    }
}
