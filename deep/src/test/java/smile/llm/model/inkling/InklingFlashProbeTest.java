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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Aten;

import static org.junit.jupiter.api.Assertions.*;
import static smile.llm.model.inkling.Ix.*;

/**
 * Probes {@code aten::_scaled_dot_product_flash_attention} (with the output's log-sum-exp) as the far-key part of global
 * prefill attention: checks it against a float32 softmax and times it at a long cache.
 *
 * <p>Opt-in, GPU: {@code INKLING_PROBE=1}.
 *
 * @author Haifeng Li
 */
public class InklingFlashProbeTest {
    private static final java.util.Random RANDOM = new java.util.Random(3);

    private static Tensor rand(Device d, long... shape) {
        long n = 1;
        for (long x : shape) {
            n *= x;
        }
        float[] data = new float[(int) n];
        for (int i = 0; i < data.length; i++) {
            data[i] = (float) RANDOM.nextGaussian();
        }
        return Tensor.of(data, shape).to(d, ScalarType.BFloat16);
    }

    @Test
    public void testGivenSharedKvHeadWhenFlashAttendedThenMatchesSoftmaxAndIsFast() throws Exception {
        Assumptions.assumeTrue(System.getenv("INKLING_PROBE") != null, "set INKLING_PROBE");
        Device dev = Device.CUDA((byte) Integer.parseInt(System.getenv().getOrDefault("INKLING_DEVICE", "0")));
        int heads = 6, hd = 128, t = 200, s = 3000;
        Tensor q = rand(dev, 1, heads, t, hd);
        Tensor kv = rand(dev, 2, 1, s, hd);                     // one KV head shared by the 6 query heads
        Tensor k = o("aten::select.int", kv, 0L, 0L);           // [1, s, hd]
        Tensor v = o("aten::select.int", kv, 0L, 1L);
        Tensor ke = o("aten::expand", unsqueeze(k, 1), new long[]{1, heads, s, hd});
        Tensor ve = o("aten::expand", unsqueeze(v, 1), new long[]{1, heads, s, hd});
        Tensor[] r = Aten.call("aten::_scaled_dot_product_flash_attention", q, ke, ve);
        System.out.println("flash returned " + r.length + " tensors; out " + java.util.Arrays.toString(r[0].shape())
                + " lse " + java.util.Arrays.toString(r[1].shape()));
        Tensor scores = mul(o("aten::matmul", f32(q), transpose(f32(ke), 2, 3)), 1.0 / Math.sqrt(hd));
        Tensor lse = o("aten::logsumexp", scores, new long[]{-1}, false);
        Tensor ref = bf16(o("aten::matmul", softmax(scores), f32(ve)));
        double dOut = o("aten::amax", o("aten::abs", sub(f32(r[0]), f32(ref))), new long[]{0, 1, 2, 3}, false).to(Device.CPU()).floatArray()[0];
        double dLse = o("aten::amax", o("aten::abs", sub(r[1], lse)), new long[]{0, 1, 2}, false).to(Device.CPU()).floatArray()[0];
        System.out.printf("max |out - reference| %.5f, max |lse - reference| %.5f%n", dOut, dLse);
        assertTrue(dOut < 0.05 && dLse < 0.05);
        // time at a long cache: 4096 queries over 100k keys
        int tq = 4096, sl = 100000;
        Tensor q2 = rand(dev, 1, heads, tq, hd);
        Tensor kv2 = rand(dev, 2, 1, sl, hd);
        Tensor k2 = o("aten::expand", unsqueeze(o("aten::select.int", kv2, 0L, 0L), 1), new long[]{1, heads, sl, hd});
        Tensor v2 = o("aten::expand", unsqueeze(o("aten::select.int", kv2, 0L, 1L), 1), new long[]{1, heads, sl, hd});
        Aten.call("aten::_scaled_dot_product_flash_attention", q2, k2, v2);
        Aten.one("aten::sum", Aten.call("aten::_scaled_dot_product_flash_attention", q2, k2, v2)[0]).doubleValue();
        long t0 = System.nanoTime();
        for (int i = 0; i < 5; i++) {
            Tensor[] x = Aten.call("aten::_scaled_dot_product_flash_attention", q2, k2, v2);
            Aten.one("aten::sum", x[0]).doubleValue();
        }
        double ms = (System.nanoTime() - t0) / 5e6;
        System.out.printf("flash, %d queries x %d keys x %d heads: %.1f ms (%.0f TFLOPs)%n", tq, sl, heads, ms,
                4.0 * tq * sl * heads * hd / (ms * 1e-3) / 1e12);
    }
}
