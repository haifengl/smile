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
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Tensor;
import smile.llm.Message;
import smile.llm.engine.Sampling;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Speculative decoding with the MTP layer: greedy speculative output must follow plain greedy decoding (up to bf16
 * near-ties) and reports the acceptance. Opt-in: {@code GLM_FULL=<checkpoint dir>}, {@code GLM_MTP_LAYERS} (default 8; 0 =
 * all 45 for real acceptance numbers), {@code GLM_FULL_DEVICES} (default 4), {@code GLM_MTP_TOKENS} (default 64),
 * {@code GLM_MTP_DRAFTS} (default 3); run with {@code SMILE_GLM_MTP=1}.
 */
public class GlmMtpTest {
    private static final String[] PROMPTS = {
            "Explain in two sentences why the sky is blue.",
            "Write a Python function that returns the n-th Fibonacci number iteratively, with a short docstring.",
            "What is 17 * 23 + 45? Answer with the final number only after brief reasoning.",
            "用中文简单介绍一下北京。",
    };

    private static int firstToken(Tensor logits) {
        try (logits) {
            return Sampling.sampleTokenIds(logits, 0.0, 1.0)[0];
        }
    }

    /** Prefills like the engine: resume from a matching prefix snapshot when there is one. */
    private static Tensor prefillResuming(Glm m, int id, int[] prompt) {
        int start = m.prefixLen(id);
        if (start > 0) {
            m.warmPrefix(id, prompt, start);
            return m.prefillChunk(id, prompt, start, prompt.length);
        }
        return m.prefill(id, prompt, 0);
    }

    private static int[] plain(Glm m, int[] prompt, int tokens) {
        int id = m.bind(prompt, prompt.length + tokens + 8);
        try (var guard = Tensor.noGradGuard()) {
            int tok = firstToken(m.prefill(id, prompt, 0));
            int[] out = new int[tokens];
            int pos = prompt.length;
            for (int i = 0; i < tokens; i++) {
                out[i] = tok;
                if (i + 1 == tokens) {
                    break;
                }
                Tensor l = m.decodeStep(new int[]{id}, new int[]{tok}, new int[]{pos++});
                tok = firstToken(l);
            }
            return out;
        } finally {
            m.evict(id);
        }
    }

    private static int[] speculative(Glm m, int[] prompt, int tokens, int drafts) {
        return speculative(m, prompt, tokens, drafts, 0.0, 1.0);
    }

    private static int[] speculative(Glm m, int[] prompt, int tokens, int drafts, double temperature, double topp) {
        int id = m.bind(prompt, prompt.length + tokens + drafts + 8);
        try (var guard = Tensor.noGradGuard()) {
            List<Integer> out = new ArrayList<>();
            int tok = firstToken(prefillResuming(m, id, prompt));
            out.add(tok);
            int pos = prompt.length;
            while (out.size() < tokens) {
                int[] acc = m.speculateStep(new int[]{id}, new int[]{tok}, new int[]{pos}, drafts, temperature, topp)[0];
                for (int t : acc) {
                    out.add(t);
                }
                pos += acc.length;
                tok = acc[acc.length - 1];
            }
            return out.stream().limit(tokens).mapToInt(Integer::intValue).toArray();
        } finally {
            m.evict(id);
        }
    }

    @Test
    public void testGivenGreedyThenSpeculativeFollowsPlainDecoding() throws Exception {
        String dir = System.getenv("GLM_FULL");
        Assumptions.assumeTrue(dir != null && Files.exists(Path.of(dir, "model.safetensors.index.json")), "set GLM_FULL=<checkpoint dir>");
        int layers = Integer.parseInt(System.getenv().getOrDefault("GLM_MTP_LAYERS", "8"));
        int devices = Integer.parseInt(System.getenv().getOrDefault("GLM_FULL_DEVICES", "4"));
        int tokens = Integer.parseInt(System.getenv().getOrDefault("GLM_MTP_TOKENS", "64"));
        int drafts = Integer.parseInt(System.getenv().getOrDefault("GLM_MTP_DRAFTS", "3"));
        byte[] dev = new byte[devices];
        for (int i = 0; i < devices; i++) {
            dev[i] = (byte) i;
        }
        try (Glm m = Glm.build(dir, layers, 16384, dev, 2)) {
            for (String q : PROMPTS) {
                int[] prompt = m.encodeChat(new Message[]{Message.user(q)});
                int[] a = plain(m, prompt, tokens);
                long t0 = System.nanoTime();
                int[] b = speculative(m, prompt, tokens, drafts);
                double ms = (System.nanoTime() - t0) / 1e6;
                int common = 0;
                while (common < tokens && a[common] == b[common]) {
                    common++;
                }
                System.out.printf("[%s] common prefix %d of %d, speculative %.0f ms (%.1f ms/token)%n   plain: %s%n   spec:  %s%n",
                        q.substring(0, Math.min(30, q.length())), common, tokens, ms, ms / tokens,
                        m.tryDecodeSafe(Arrays.copyOf(a, Math.min(tokens, 24))), m.tryDecodeSafe(Arrays.copyOf(b, Math.min(tokens, 24))));
                assertTrue(common >= Math.min(8, tokens), "speculative output diverges from plain greedy at token " + common);
            }
            System.out.println("speculation: " + m.speculationStats());
        }
    }

    @Test
    public void testGivenPrefixSnapshotThenSpeculationContinuesFromRestoredMtpState() throws Exception {
        String dir = System.getenv("GLM_FULL");
        Assumptions.assumeTrue(dir != null && Files.exists(Path.of(dir, "model.safetensors.index.json")), "set GLM_FULL=<checkpoint dir>");
        int layers = Integer.parseInt(System.getenv().getOrDefault("GLM_MTP_LAYERS", "8"));
        int devices = Integer.parseInt(System.getenv().getOrDefault("GLM_FULL_DEVICES", "4"));
        byte[] dev = new byte[devices];
        for (int i = 0; i < devices; i++) {
            dev[i] = (byte) i;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 120; i++) {
            sb.append("Item ").append(i).append(": the quick brown fox number ").append(i * 7919 % 1000).append(" jumps over the lazy dog.\n");
        }
        try (Glm m = Glm.build(dir, layers, 16384, dev, 2)) {
            int[] prompt = m.encodeChat(new Message[]{Message.user(sb + "\nSummarize the list above in one sentence.")});
            assertTrue(prompt.length > 1000, "prompt length " + prompt.length);
            int[] cold = speculative(m, prompt, 40, 3);
            String before = m.speculationStats();
            int[] warm = speculative(m, prompt, 40, 3);          // resumes from the snapshot at promptLen - 1
            int common = 0;
            while (common < cold.length && cold[common] == warm[common]) {
                common++;
            }
            System.out.println("cold vs resumed speculative: common prefix " + common + " of " + cold.length
                    + "\n   cold: " + m.tryDecodeSafe(Arrays.copyOf(cold, 20)) + "\n   warm: " + m.tryDecodeSafe(Arrays.copyOf(warm, 20))
                    + "\n   cache: " + m.prefixCacheStats() + "\n   spec: " + m.speculationStats());
            // the first tokens of this prompt are a near-tie ("has provided" / "wants a ..."): which one wins flips with the
            // accumulation noise of the batched MoE, so only check that the resumed request speculates sensibly
            assertTrue(common >= 2, "resumed speculative output diverges at token " + common);
            assertTrue(m.prefixCacheStats().contains("1/2 lookups hit"), "the second request did not resume: " + m.prefixCacheStats());
        }
    }

    @Test
    public void testGivenBatchOfRequestsThenSpeculativeFollowsPlainDecoding() throws Exception {
        String dir = System.getenv("GLM_FULL");
        Assumptions.assumeTrue(dir != null && Files.exists(Path.of(dir, "model.safetensors.index.json")), "set GLM_FULL=<checkpoint dir>");
        int layers = Integer.parseInt(System.getenv().getOrDefault("GLM_MTP_LAYERS", "8"));
        int devices = Integer.parseInt(System.getenv().getOrDefault("GLM_FULL_DEVICES", "4"));
        int tokens = Integer.parseInt(System.getenv().getOrDefault("GLM_MTP_TOKENS", "64"));
        int drafts = Integer.parseInt(System.getenv().getOrDefault("GLM_MTP_DRAFTS", "3"));
        byte[] dev = new byte[devices];
        for (int i = 0; i < devices; i++) {
            dev[i] = (byte) i;
        }
        try (Glm m = Glm.build(dir, layers, 16384, dev, PROMPTS.length + 1)) {
            int n = PROMPTS.length;
            int[][] prompts = new int[n][];
            int[][] want = new int[n][];
            for (int i = 0; i < n; i++) {
                prompts[i] = m.encodeChat(new Message[]{Message.user(PROMPTS[i])});
                want[i] = plain(m, prompts[i], tokens);
            }
            int[] ids = new int[n];
            int[] last = new int[n];
            int[] pos = new int[n];
            List<List<Integer>> out = new ArrayList<>();
            try (var guard = Tensor.noGradGuard()) {
                for (int i = 0; i < n; i++) {
                    ids[i] = m.bind(prompts[i], prompts[i].length + tokens + 4 * (drafts + 2));
                    last[i] = firstToken(m.prefill(ids[i], prompts[i], 0));
                    pos[i] = prompts[i].length;
                    out.add(new ArrayList<>(List.of(last[i])));
                }
                long t0 = System.nanoTime();
                int rounds = 0;
                while (out.stream().anyMatch(o -> o.size() < tokens)) {
                    int[][] acc = m.speculateStep(ids, last, pos, drafts, 0.0, 1.0);
                    for (int i = 0; i < n; i++) {
                        for (int t : acc[i]) {
                            out.get(i).add(t);
                        }
                        pos[i] += acc[i].length;
                        last[i] = acc[i][acc[i].length - 1];
                    }
                    rounds++;
                }
                double ms = (System.nanoTime() - t0) / 1e6;
                System.out.printf("%d requests, %d rounds, %.0f ms (%.1f ms per round, %.1f ms per token per request)%n", n, rounds, ms, ms / rounds, ms / tokens);
            } finally {
                for (int id : ids) {
                    m.evict(id);
                }
            }
            for (int i = 0; i < n; i++) {
                int common = 0;
                while (common < tokens && want[i][common] == out.get(i).get(common)) {
                    common++;
                }
                System.out.printf("request %d: common prefix %d of %d%n", i, common, tokens);
                assertTrue(common >= Math.min(8, tokens), "batched speculative output of request " + i + " diverges at token " + common);
            }
            System.out.println("speculation: " + m.speculationStats());
        }
    }

    @Test
    public void testGivenSwitchingBetweenPlainAndSpeculativeStepsThenOutputFollowsPlainDecoding() throws Exception {
        String dir = System.getenv("GLM_FULL");
        Assumptions.assumeTrue(dir != null && Files.exists(Path.of(dir, "model.safetensors.index.json")), "set GLM_FULL=<checkpoint dir>");
        int layers = Integer.parseInt(System.getenv().getOrDefault("GLM_MTP_LAYERS", "8"));
        int devices = Integer.parseInt(System.getenv().getOrDefault("GLM_FULL_DEVICES", "4"));
        int tokens = Integer.parseInt(System.getenv().getOrDefault("GLM_MTP_TOKENS", "64"));
        int drafts = Integer.parseInt(System.getenv().getOrDefault("GLM_MTP_DRAFTS", "3"));
        String pattern = System.getenv().getOrDefault("GLM_MTP_PATTERN", "SSPPPSPSSSPPSSSSPSPSSSSS");
        byte[] dev = new byte[devices];
        for (int i = 0; i < devices; i++) {
            dev[i] = (byte) i;
        }
        try (Glm m = Glm.build(dir, layers, 16384, dev, 4)) {
            int n = 3;
            int[][] prompts = new int[n][];
            int[][] want = new int[n][];
            for (int i = 0; i < n; i++) {
                prompts[i] = m.encodeChat(new Message[]{Message.user(PROMPTS[i])});
                want[i] = plain(m, prompts[i], tokens);
            }
            int[] ids = new int[n];
            int[] last = new int[n];
            int[] pos = new int[n];
            List<List<Integer>> out = new ArrayList<>();
            long specRoundsBefore = 0;
            try (var guard = Tensor.noGradGuard()) {
                for (int i = 0; i < n; i++) {
                    ids[i] = m.bind(prompts[i], prompts[i].length + tokens + 8 * (drafts + 2));
                    last[i] = firstToken(m.prefill(ids[i], prompts[i], 0));
                    pos[i] = prompts[i].length;
                    out.add(new ArrayList<>(List.of(last[i])));
                }
                for (int tick = 0; out.stream().anyMatch(o -> o.size() < tokens); tick++) {
                    char kind = pattern.charAt(tick % pattern.length());
                    if (kind == 'S') {
                        int[][] acc = m.speculateStep(ids, last, pos, drafts, 0.0, 1.0);
                        for (int i = 0; i < n; i++) {
                            for (int t : acc[i]) {
                                out.get(i).add(t);
                            }
                            pos[i] += acc[i].length;
                            last[i] = acc[i][acc[i].length - 1];
                        }
                    } else {
                        Tensor lg = m.decodeStep(ids, last, pos);
                        int[] next;
                        try (lg) {
                            next = Sampling.sampleTokenIds(lg, 0.0, 1.0);
                        }
                        for (int i = 0; i < n; i++) {
                            out.get(i).add(next[i]);
                            pos[i]++;
                            last[i] = next[i];
                        }
                    }
                }
            } finally {
                for (int id : ids) {
                    m.evict(id);
                }
            }
            for (int i = 0; i < n; i++) {
                int common = 0;
                while (common < tokens && want[i].length > common && want[i][common] == out.get(i).get(common)) {
                    common++;
                }
                System.out.printf("request %d: common prefix %d of %d%n", i, common, tokens);
                assertTrue(common >= Math.min(24, tokens), "switching output of request " + i + " diverges at token " + common);
            }
            System.out.println("speculation: " + m.speculationStats());
        }
    }

    @Test
    public void testGivenTemperatureThenAcceptanceAndSpeedReported() throws Exception {
        String dir = System.getenv("GLM_FULL");
        Assumptions.assumeTrue(dir != null && Files.exists(Path.of(dir, "model.safetensors.index.json")), "set GLM_FULL=<checkpoint dir>");
        int layers = Integer.parseInt(System.getenv().getOrDefault("GLM_MTP_LAYERS", "8"));
        int devices = Integer.parseInt(System.getenv().getOrDefault("GLM_FULL_DEVICES", "4"));
        int tokens = Integer.parseInt(System.getenv().getOrDefault("GLM_MTP_TOKENS", "128"));
        int drafts = Integer.parseInt(System.getenv().getOrDefault("GLM_MTP_DRAFTS", "3"));
        byte[] dev = new byte[devices];
        for (int i = 0; i < devices; i++) {
            dev[i] = (byte) i;
        }
        try (Glm m = Glm.build(dir, layers, 16384, dev, 2)) {
            // warm up: graph captures
            speculative(m, m.encodeChat(new Message[]{Message.user("Hello")}), 24, drafts);
            for (double temperature : new double[]{0.0, 0.3, 0.6, 1.0}) {
                long[] before = m.speculationCounters();
                long t0 = System.nanoTime();
                long generated = 0;
                for (String q : PROMPTS) {
                    int[] out = speculative(m, m.encodeChat(new Message[]{Message.user(q)}), tokens, drafts, temperature, 0.95);
                    generated += out.length;
                }
                double ms = (System.nanoTime() - t0) / 1e6;
                long[] after = m.speculationCounters();
                long rounds = after[0] - before[0];
                long proposed = after[1] - before[1];
                long accepted = after[2] - before[2];
                System.out.printf("T=%.1f top_p 0.95: %d rounds, %d/%d drafts accepted (%.1f%%), %.2f tokens per round, %.1f ms/token%n",
                        temperature, rounds, accepted, proposed, 100.0 * accepted / proposed, 1.0 + (double) accepted / rounds, ms / generated);
            }
        }
    }
}
