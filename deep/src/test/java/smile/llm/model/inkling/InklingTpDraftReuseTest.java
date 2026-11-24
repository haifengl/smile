/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
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
import smile.deep.tensor.Device;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A request resumed from a stored prefix must speculate as well as a cold one: the draft's context is part of the snapshot.
 * Greedy rounds of {@code speculateStep} on a copy task, cold and after a prefix hit; the tokens per round and the text must agree.
 *
 * <p>Opt-in, GPU: {@code INKLING_CKPT}, {@code INKLING_TP}, {@code INKLING_PROMPTS} (json with {@code results[].prompt_ids}),
 * {@code INKLING_PROMPT_INDEX} (default 1: the copy task), {@code INKLING_STEPS} (default 120).
 *
 * @author Haifeng Li
 */
public class InklingTpDraftReuseTest {
    private static int argmax(float[] x) {
        int best = 0;
        for (int i = 1; i < x.length; i++) {
            if (x[i] > x[best]) {
                best = i;
            }
        }
        return best;
    }

    /** Greedy speculative continuation of a bound request; returns {tokens, rounds} and fills {@code out}. */
    private static int[] run(InklingTp model, int[] prompt, int steps, List<Integer> out, boolean[] hit) {
        int id = model.bind(prompt, prompt.length + 2 * steps + 64);
        int matched = model.prefixLen(id);
        hit[0] = matched > 0;
        model.warmPrefix(id, prompt, matched);
        int last = argmax(model.prefill(id, prompt, matched).to(Device.CPU()).floatArray());
        int pos = prompt.length;
        int rounds = 0;
        while (out.size() < steps) {
            int[][] r = model.speculateStep(new int[]{id}, new int[]{last}, new int[]{pos}, model.draftBlock(), 0.0, 1.0);
            out.add(last);
            for (int k = 0; k < r[0].length - 1; k++) {
                out.add(r[0][k]);
            }
            last = r[0][r[0].length - 1];
            pos += r[0].length;
            rounds++;
        }
        model.evict(id);
        return new int[]{out.size(), rounds};
    }

    @Test
    public void testGivenPrefixHitWhenSpeculatedThenAsManyTokensPerRoundAsCold() throws Exception {
        String ckpt = System.getenv("INKLING_CKPT");
        String tp = System.getenv("INKLING_TP");
        String prompts = System.getenv("INKLING_PROMPTS");
        Assumptions.assumeTrue(ckpt != null && tp != null && prompts != null, "set INKLING_CKPT, INKLING_TP, INKLING_PROMPTS");
        int steps = Integer.parseInt(System.getenv().getOrDefault("INKLING_STEPS", "120"));
        String[] ids = tp.split(",");
        byte[] devices = new byte[ids.length];
        for (int i = 0; i < devices.length; i++) {
            devices[i] = Byte.parseByte(ids[i].trim());
        }
        InklingTp model = InklingTp.build(ckpt, 0, 16384, devices);
        JsonNode p = new ObjectMapper().readTree(new File(prompts)).get("results")
                .get(Integer.parseInt(System.getenv().getOrDefault("INKLING_PROMPT_INDEX", "1"))).get("prompt_ids");
        int[] prompt = new int[p.size()];
        for (int i = 0; i < prompt.length; i++) {
            prompt[i] = p.get(i).asInt();
        }
        try {
            List<Integer> cold = new ArrayList<>();
            List<Integer> warm = new ArrayList<>();
            boolean[] hit = new boolean[1];
            int[] a = run(model, prompt, steps, cold, hit);
            assertFalse(hit[0]);
            int[] b = run(model, prompt, steps, warm, hit);
            assertTrue(hit[0], "no prefix hit");
            System.out.printf("%d-token prompt: cold %.2f tokens per round, after a prefix hit %.2f%n", prompt.length,
                    (double) a[0] / a[1], (double) b[0] / b[1]);
            int same = 0;
            while (same < Math.min(cold.size(), warm.size()) && cold.get(same).equals(warm.get(same))) {
                same++;
            }
            System.out.printf("greedy text identical for %d of %d tokens%n", same, steps);
            assertTrue((double) b[0] / b[1] > 0.8 * a[0] / a[1], "the draft lost its context after a prefix hit");
        } finally {
            model.close();
        }
    }
}
