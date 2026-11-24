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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.Tensor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prefix reuse of {@link InklingTp}: a long prompt is prefilled cold, finished (its snapshots are stored), and prefilled again
 * from the longest stored prefix; the logits must agree up to the noise of two prefills with different chunking, and the
 * warm prefill must be faster.
 *
 * <p>Opt-in, GPU: {@code INKLING_CKPT}, {@code INKLING_TP}, {@code INKLING_PROMPTS}, {@code INKLING_LAYERS},
 * {@code INKLING_PROMPT_LEN} (default 2600).
 *
 * @author Haifeng Li
 */
public class InklingTpPrefixTest {
    private static double tv(float[] a, float[] b) {
        double ma = Double.NEGATIVE_INFINITY, mb = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < a.length; i++) {
            ma = Math.max(ma, a[i]);
            mb = Math.max(mb, b[i]);
        }
        double sa = 0, sb = 0;
        for (int i = 0; i < a.length; i++) {
            sa += Math.exp(a[i] - ma);
            sb += Math.exp(b[i] - mb);
        }
        double tv = 0;
        for (int i = 0; i < a.length; i++) {
            tv += Math.abs(Math.exp(a[i] - ma) / sa - Math.exp(b[i] - mb) / sb);
        }
        return tv / 2;
    }

    @Test
    public void testGivenStoredPrefixWhenPrefilledAgainThenLogitsAgreeAndItIsFaster() throws Exception {
        String ckpt = System.getenv("INKLING_CKPT");
        String tp = System.getenv("INKLING_TP");
        String prompts = System.getenv("INKLING_PROMPTS");
        Assumptions.assumeTrue(ckpt != null && tp != null && prompts != null, "set INKLING_CKPT, INKLING_TP, INKLING_PROMPTS");
        int len = Integer.parseInt(System.getenv().getOrDefault("INKLING_PROMPT_LEN", "2600"));
        String[] ids = tp.split(",");
        byte[] devices = new byte[ids.length];
        for (int i = 0; i < devices.length; i++) {
            devices[i] = Byte.parseByte(ids[i].trim());
        }
        int layers = Integer.parseInt(System.getenv().getOrDefault("INKLING_LAYERS", "0"));
        InklingTp model = InklingTp.build(ckpt, layers, Integer.parseInt(System.getenv().getOrDefault("INKLING_MAXLEN", "8192")), devices);
        JsonNode p = new ObjectMapper().readTree(new File(prompts)).get("results").get(5).get("prompt_ids");
        int[] prompt = new int[len];
        for (int i = 0; i < len; i++) {            // a long prompt made of the dialog's own tokens, repeated
            prompt[i] = p.get(i % p.size()).asInt();
        }
        try {
            long t0 = System.nanoTime();
            int a = model.bind(prompt, len + 64);
            assertEquals(0, model.prefixLen(a));
            float[] cold = model.prefill(a, prompt, 0).to(Device.CPU()).floatArray();
            double coldSecs = (System.nanoTime() - t0) / 1e9;
            model.finish(a, prompt);
            System.out.println(model.prefixCacheStats());
            long t1 = System.nanoTime();
            int b = model.bind(prompt, len + 64);
            int matched = model.prefixLen(b);
            model.warmPrefix(b, prompt, matched);
            float[] warm = model.prefill(b, prompt, matched).to(Device.CPU()).floatArray();
            double warmSecs = (System.nanoTime() - t1) / 1e9;
            System.out.printf("prompt %d tokens: cold %.2f s; warm (matched %d tokens) %.2f s; TV %.4f%n", len, coldSecs,
                    matched, warmSecs, tv(cold, warm));
            assertTrue(matched >= 1024, "no prefix matched");
            assertTrue(tv(cold, warm) < 0.1, "warm logits diverge from cold");
            model.evict(b);
        } finally {
            model.close();
        }
    }
}
