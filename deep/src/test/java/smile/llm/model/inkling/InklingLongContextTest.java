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
import java.util.Arrays;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.SafeTensors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Long-context check against the Hugging Face model: layer outputs of the tokens 1024.. of a 1600-token prompt (past the
 * sliding window) in float32 and in bf16, next to ours. A repetitive prompt is chaotic, so what matters is that ours is as
 * close to the float32 truth as the bf16 reference is.
 *
 * <p>Opt-in, GPU: {@code INKLING_CKPT}, {@code INKLING_LONG_F32}, {@code INKLING_LONG_BF16} (from {@code oracle_long.py}).
 *
 * @author Haifeng Li
 */
public class InklingLongContextTest {
    private static double relRms(float[] a, float[] b) {
        double num = 0;
        double den = 0;
        for (int i = 0; i < a.length; i++) {
            double d = a[i] - b[i];
            num += d * d;
            den += (double) b[i] * b[i];
        }
        return Math.sqrt(num / den);
    }

    @Test
    public void testGivenLongPromptWhenComparedWithTheReferenceThenAsCloseToFloat32AsBf16() throws Exception {
        String ckpt = System.getenv("INKLING_CKPT");
        String f32 = System.getenv("INKLING_LONG_F32");
        String bf16 = System.getenv("INKLING_LONG_BF16");
        Assumptions.assumeTrue(ckpt != null && f32 != null && bf16 != null && new File(f32).exists() && new File(bf16).exists(),
                "set INKLING_CKPT, INKLING_LONG_F32 and INKLING_LONG_BF16");
        byte device = Byte.parseByte(System.getenv().getOrDefault("INKLING_DEVICE", "0"));
        InklingModelArgs args = InklingModelArgs.fromCheckpoint(ckpt, 4096);
        InklingModel model = InklingModel.load(ckpt, args, Device.CUDA(device));
        InklingTokenizer tokenizer = InklingTokenizer.of(ckpt);
        StringBuilder sb = new StringBuilder();
        int n = 0;
        while (sb.length() < 9000) {
            sb.append("Item ").append(n).append(": the quick brown fox number ").append(n * 7919 % 1000)
                    .append(" jumps over the lazy dog.\n");
            n++;
        }
        long[] tokens = Arrays.copyOf(Arrays.stream(tokenizer.encode(sb.toString())).asLongStream().toArray(), 1600);
        int from = 1024;
        int dim = args.dim();
        int chunk = Integer.parseInt(System.getenv().getOrDefault("INKLING_CHUNK", "200"));
        float[][] ours = new float[args.numApplications()][(tokens.length - from) * dim];
        try (InklingState st = model.newState(2048)) {
            for (int pos = 0; pos < tokens.length; pos += chunk) {
                int end = Math.min(tokens.length, pos + chunk);
                int[] app = {0};
                int p = pos;
                model.forward(st, Arrays.copyOfRange(tokens, pos, end), new int[0], t -> {
                    float[] rows = t.to(smile.deep.tensor.ScalarType.Float).floatArray();
                    int a = app[0]++;
                    for (int r = Math.max(p, from); r < end; r++) {
                        System.arraycopy(rows, (r - p) * dim, ours[a], (r - from) * dim, dim);
                    }
                });
            }
        }
        SafeTensors truth = SafeTensors.read(f32, Device.CPU());
        SafeTensors ref = SafeTensors.read(bf16, Device.CPU());
        double worstOurs = 0;
        double worstRef = 0;
        for (int a = 0; a < ours.length; a++) {
            float[] t = truth.tensors().get("l" + a).floatArray();
            double o = relRms(ours[a], t);
            double r = relRms(ref.tensors().get("l" + a).floatArray(), t);
            worstOurs = Math.max(worstOurs, o);
            worstRef = Math.max(worstRef, r);
            if (a % 6 == 5 || a == ours.length - 1) {
                System.out.printf("  app %2d: ours vs fp32 %.4f, HF bf16 vs fp32 %.4f%n", a, o, r);
            }
        }
        System.out.printf("worst: ours vs fp32 %.4f, HF bf16 vs fp32 %.4f%n", worstOurs, worstRef);
        assertTrue(worstOurs < Math.max(0.1, 2 * worstRef), "ours is farther from float32 than the bf16 reference: " + worstOurs + " vs " + worstRef);
    }
}
