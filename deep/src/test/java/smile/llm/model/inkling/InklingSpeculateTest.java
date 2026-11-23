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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Tensor;
import smile.llm.Message;
import smile.llm.engine.Sampling;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Speculative rounds of the fused verify graph against plain fused decoding, through the executor API: the text must agree
 * up to rounding noise, and the rounds report the accept length and the time per token.
 *
 * <p>Opt-in, GPU: {@code INKLING_CKPT}, {@code INKLING_DIALOGS}, {@code INKLING_DEVICE}, {@code INKLING_TOKENS} (default 128),
 * {@code INKLING_DRAFTS} (default 7), {@code INKLING_BATCH} (requests per round, default 1).
 *
 * @author Haifeng Li
 */
public class InklingSpeculateTest {
    @Test
    public void testGivenSpeculativeRoundsWhenDecodedGreedilyThenTextMatchesPlainDecoding() throws Exception {
        String ckpt = System.getenv("INKLING_CKPT");
        String dialogs = System.getenv("INKLING_DIALOGS");
        Assumptions.assumeTrue(ckpt != null && dialogs != null && new File(dialogs).exists(), "set INKLING_CKPT and INKLING_DIALOGS");
        int count = Integer.parseInt(System.getenv().getOrDefault("INKLING_TOKENS", "128"));
        int drafts = Integer.parseInt(System.getenv().getOrDefault("INKLING_DRAFTS", "7"));
        int batch = Integer.parseInt(System.getenv().getOrDefault("INKLING_BATCH", "1"));
        byte device = Byte.parseByte(System.getenv().getOrDefault("INKLING_DEVICE", "0"));
        JsonNode input = new ObjectMapper().readTree(new File(dialogs));
        try (Inkling model = Inkling.build(ckpt, 0, 8192, device)) {
            assertTrue(model.hasDraft(), "no draft");
            int total = input.size();
            boolean all = System.getenv("INKLING_ALL") != null;
            int groups = all ? (total + batch - 1) / batch : 1;
            double plainTime = 0, specTime = 0;
            long tokens = 0;
            for (int gi = 0; gi < groups; gi++) {
                int from = gi * batch, n = Math.min(batch, total - from);
                int[][] prompts = new int[n][];
                for (int k = 0; k < n; k++) {
                    JsonNode d = input.get(from + k);
                    List<Message> messages = new ArrayList<>();
                    for (JsonNode m : d.get("messages")) {
                        messages.add(InklingGenerateTest.message(m));
                    }
                    prompts[k] = model.encodeChat(messages.toArray(Message[]::new), InklingGenerateTest.options(d));
                }
                int[][] plain = run(model, prompts, count, 0, "plain");
                plainTime += lastSeconds;
                int[][] spec = run(model, prompts, count, drafts, "speculative (warm-up)");
                spec = run(model, prompts, count, drafts, "speculative");
                specTime += lastSeconds;
                tokens += (long) n * (count - 1);
                for (int k = 0; k < n; k++) {
                    int same = 0;
                    while (same < count && plain[k][same] == spec[k][same]) {
                        same++;
                    }
                    System.out.printf("request %d: identical to plain greedy for %d of %d tokens%n", from + k, same, count);
                    assertTrue(same >= 2, "diverges almost at once");
                }
            }
            System.out.printf("TOTAL %d tokens: plain %.1f tok/s, speculative %.1f tok/s (x%.2f)%n", tokens, tokens / plainTime,
                    tokens / specTime, plainTime / specTime);
        }
    }

    private static double lastSeconds;

    private static int[][] run(Inkling model, int[][] prompts, int count, int drafts, String label) {
        int n = prompts.length;
        int[] ids = new int[n];
        int[] last = new int[n];
        int[][] out = new int[n][count];
        int[] have = new int[n];
        for (int k = 0; k < n; k++) {
            ids[k] = model.bind(prompts[k], prompts[k].length + count + 16);
            try (Tensor logits = model.prefill(ids[k], prompts[k], 0)) {
                last[k] = Sampling.sampleTokenIds(logits, 0.0, 1.0)[0];
            }
            out[k][0] = last[k];
            have[k] = 1;
        }
        long t0 = System.nanoTime();
        int rounds = 0;
        while (true) {
            List<Integer> act = new ArrayList<>();
            for (int k = 0; k < n; k++) {
                if (have[k] < count) {
                    act.add(k);
                }
            }
            if (act.isEmpty()) {
                break;
            }
            int[] rid = act.stream().mapToInt(k -> ids[k]).toArray();
            int[] lt = act.stream().mapToInt(k -> last[k]).toArray();
            int[] ps = act.stream().mapToInt(k -> prompts[k].length + have[k] - 1).toArray();
            int[][] got;
            if (drafts == 0) {
                try (Tensor lg = model.decodeStep(rid, lt, ps)) {
                    int[] s = Sampling.sampleTokenIds(lg, 0.0, 1.0);
                    got = new int[rid.length][];
                    for (int i = 0; i < got.length; i++) {
                        got[i] = new int[]{s[i]};
                    }
                }
            } else {
                got = model.speculateStep(rid, lt, ps, drafts, 0.0, 1.0);
            }
            for (int i = 0; i < rid.length; i++) {
                int k = act.get(i);
                for (int t : got[i]) {
                    if (have[k] < count) {
                        out[k][have[k]++] = t;
                        last[k] = t;
                    }
                }
            }
            rounds++;
        }
        double s = (System.nanoTime() - t0) / 1e9;
        lastSeconds = s;
        System.out.printf("%s: %d requests x %d tokens in %.2f s, %d rounds, %.1f ms/round, %.1f tok/s aggregate, %.2f tokens/round/request%n",
                label, n, count, s, rounds, s * 1000 / rounds, n * (count - 1) / s, n * (count - 1) / (double) rounds / n);
        for (int id : ids) {
            model.evict(id);
        }
        return out;
    }
}
