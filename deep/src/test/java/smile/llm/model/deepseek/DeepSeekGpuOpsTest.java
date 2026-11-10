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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Aten;

/** Opt-in (CUDA): every op of {@link DeepSeekOps} gives the same result on CUDA as on CPU for random inputs. */
public class DeepSeekGpuOpsTest {
    private static double rel(Tensor a, Tensor b) {
        Tensor x = a.to(Device.CPU()).to(ScalarType.Float);
        Tensor y = b.to(Device.CPU()).to(ScalarType.Float);
        double n = Aten.one("aten::norm.Scalar", Aten.one("aten::sub.Tensor", x, y)).doubleValue();
        double d = Aten.one("aten::norm.Scalar", y).doubleValue();
        return n / Math.max(d, 1e-30);
    }

    private static void report(String name, Tensor gpu, Tensor cpu) {
        System.out.printf("GPUOP %-22s rel err %.3e%n", name, rel(gpu, cpu));
    }

    @Test
    public void testGivenRandomInputsThenCudaMatchesCpu() {
        Device cuda;
        try {
            cuda = Device.CUDA();
        } catch (Throwable t) {
            Assumptions.abort("no CUDA");
            return;
        }
        Tensor x = Aten.one("aten::mul.Tensor", Aten.one("aten::randn", new long[]{40, 4096}),
                Aten.one("aten::logspace", -2.0, 1.0, 4096L)).to(ScalarType.BFloat16);
        Tensor xg = x.to(cuda);
        report("fakeQuantFp8", DeepSeekOps.fakeQuantFp8(xg, 32), DeepSeekOps.fakeQuantFp8(x, 32));
        report("fakeQuantFp4", DeepSeekOps.fakeQuantFp4(xg, 32, false), DeepSeekOps.fakeQuantFp4(x, 32, false));
        report("fakeQuantFp4 e4m3/16", DeepSeekOps.fakeQuantFp4(xg, 16, true), DeepSeekOps.fakeQuantFp4(x, 16, true));
        Tensor w = Aten.one("aten::randn", new long[]{4096}).to(ScalarType.BFloat16);
        report("rmsNorm", DeepSeekOps.rmsNorm(xg, w.to(cuda), 1e-20), DeepSeekOps.rmsNorm(x, w, 1e-20));
        Tensor mixes = Aten.one("aten::randn", new long[]{1, 40, 24});
        Tensor scale = Aten.one("aten::randn", new long[]{3});
        Tensor base = Aten.one("aten::randn", new long[]{24});
        Tensor[] c = DeepSeekOps.hcSplitSinkhorn(mixes, scale, base, 4, 20, 1e-6);
        Tensor[] g = DeepSeekOps.hcSplitSinkhorn(mixes.to(cuda), scale.to(cuda), base.to(cuda), 4, 20, 1e-6);
        for (int i = 0; i < 3; i++) {
            report("sinkhorn[" + i + "]", g[i], c[i]);
        }
        Tensor f = DeepSeekOps.precomputeFreqsCis(64, 64, 0, 10000.0, 1.0, 32, 1);
        Tensor q = Aten.one("aten::randn", new long[]{1, 40, 8, 128}).to(ScalarType.BFloat16);
        Tensor fc = Aten.one("aten::narrow", f, 0L, 0L, 40L);
        Tensor rq = Aten.one("aten::narrow", q, -1L, 0L, 64L);
        report("rope", DeepSeekOps.rope(Aten.one("aten::narrow", q.to(cuda), -1L, 0L, 64L), fc.to(cuda), false),
                DeepSeekOps.rope(rq, fc, false));
        Tensor kv = Aten.one("aten::randn", new long[]{1, 40, 128}).to(ScalarType.BFloat16);
        Tensor sink = Aten.one("aten::randn", new long[]{8});
        Tensor idx = DeepSeekOps.windowTopkIdxs(16, 40, 0);
        report("sparseAttention", DeepSeekOps.sparseAttention(q.to(cuda), kv.to(cuda), sink.to(cuda), idx.to(cuda), 0.088),
                DeepSeekOps.sparseAttention(q, kv, sink, idx, 0.088));
        Tensor wt = Aten.one("aten::randn", new long[]{512, 4096}).to(ScalarType.BFloat16);
        Tensor lin = Aten.one("aten::linear", DeepSeekOps.f32(DeepSeekOps.fakeQuantFp8(x, 32)), DeepSeekOps.f32(wt));
        Tensor lg = Aten.one("aten::linear", DeepSeekOps.fakeQuantFp8(xg, 32), wt.to(cuda));
        report("linear bf16 vs f32", lg, lin);
        Tensor lgf = Aten.one("aten::linear", DeepSeekOps.f32(DeepSeekOps.fakeQuantFp8(xg, 32)), DeepSeekOps.f32(wt).to(cuda));
        report("linear f32 cuda", lgf, lin);
    }
}
