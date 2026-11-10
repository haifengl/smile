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
package smile.llm.model.deepseek;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Aten;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in ({@code DEEPSEEK_CUDA=true}, layer 3 of the slice shards): FP8 dense layers packed for the Marlin kernel give the
 * same result as the dequantized bf16 GEMM (only the summation order differs), also when fused.
 *
 * @author Haifeng Li
 */
public class DeepSeekMarlinLinearTest {
    private static double rel(Tensor a, Tensor b) {
        Tensor x = a.to(ScalarType.Float);
        Tensor y = b.to(ScalarType.Float);
        return Aten.one("aten::norm.Scalar", Aten.one("aten::sub.Tensor", x, y)).doubleValue()
                / Aten.one("aten::norm.Scalar", y).doubleValue();
    }

    private static Tensor input(long n, long k, long seed) {
        java.util.Random r = new java.util.Random(seed);
        float[] v = new float[(int) (n * k)];
        for (int i = 0; i < v.length; i++) {
            v[i] = (float) r.nextGaussian();
        }
        return Tensor.of(v, 1, n, k).to(ScalarType.BFloat16).to(Device.CUDA((byte) 0));
    }

    @Test
    public void testGivenRealFp8WeightsThenMarlinEqualsDequantizedGemm() throws Exception {
        Assumptions.assumeTrue(Boolean.parseBoolean(System.getenv().getOrDefault("DEEPSEEK_CUDA", "false")), "DEEPSEEK_CUDA not set");
        Path dir = Path.of(System.getenv().getOrDefault("DEEPSEEK_DIR", "/raid/haili/dsv4/flash_raw"));
        Assumptions.assumeTrue(Files.exists(dir.resolve("model-00003-of-00048.safetensors")), "no slice shards");
        Dsv.setDevice(Device.CUDA((byte) 0));
        try (DeepSeekCheckpoint ck = new DeepSeekCheckpoint(dir)) {
            for (String name : new String[]{"layers.3.attn.wq_a", "layers.3.attn.wq_b", "layers.3.attn.wkv",
                    "layers.3.attn.wo_b", "layers.3.ffn.shared_experts.w1", "layers.3.ffn.shared_experts.w2"}) {
                Dsv.marlin = false;
                DeepSeekLinear dense = new DeepSeekLinear(ck, name, null);
                Dsv.marlin = true;
                DeepSeekLinear packed = new DeepSeekLinear(ck, name, null);
                assertTrue(packed.packed() && !dense.packed(), name);
                long k = ck.shape(name + ".weight")[1];
                for (long n : new long[]{1, 7, 40}) {
                    Tensor x = input(n, k, 7);
                    double e = rel(packed.forward(x), dense.forward(x));
                    System.out.printf("%-34s n=%-3d rel err %.5f%n", name, n, e);
                    assertTrue(e < 1e-2, name + " n=" + n + " rel err " + e);
                }
            }
            // fused: wq_a + wkv in one kernel equals the two separate outputs
            Dsv.marlin = true;
            DeepSeekLinear a = new DeepSeekLinear(ck, "layers.3.attn.wq_a", null);
            DeepSeekLinear b = new DeepSeekLinear(ck, "layers.3.attn.wkv", null);
            DeepSeekLinear a2 = new DeepSeekLinear(ck, "layers.3.attn.wq_a", null);
            DeepSeekLinear b2 = new DeepSeekLinear(ck, "layers.3.attn.wkv", null);
            Tensor x = input(5, 5120, 11);
            Tensor ya = a.forward(x);
            Tensor yb = b.forward(x);
            Tensor y = DeepSeekLinear.fuse(a2, b2).forward(x);
            double ea = rel(Dsv.narrow(y, 2, 0, ya.size(2)), ya);
            double eb = rel(Dsv.narrow(y, 2, ya.size(2), yb.size(2)), yb);
            System.out.printf("fused wq_a+wkv vs separate: %.6f %.6f%n", ea, eb);
            assertTrue(ea < 1e-3 && eb < 1e-3);
        } finally {
            Dsv.setDevice(Device.CPU());
            Dsv.marlin = false;
        }
    }
}
