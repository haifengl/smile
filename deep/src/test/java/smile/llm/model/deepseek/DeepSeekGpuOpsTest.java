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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import smile.deep.CUDA;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Aten;

/** Opt-in (CUDA): every op of {@link DeepSeekOps} gives the same result on CUDA as on CPU for random inputs. */
@Tag("cuda")
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
        Assumptions.assumeTrue(CUDA.isAvailable() && CUDA.deviceCount() > 0 && Aten.isAvailable(), "no CUDA");
        Device cuda = Device.CUDA();
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
        Tensor idx = DeepSeekOps.windowTopkIdxs(16, 16, 40, 0);
        report("sparseAttention", DeepSeekOps.sparseAttention(q.to(cuda), kv.to(cuda), sink.to(cuda), idx.to(cuda), 0.088),
                DeepSeekOps.sparseAttention(q, kv, sink, idx, 0.088));
        Tensor wt = Aten.one("aten::randn", new long[]{512, 4096}).to(ScalarType.BFloat16);
        Tensor lin = Aten.one("aten::linear", DeepSeekOps.f32(DeepSeekOps.fakeQuantFp8(x, 32)), DeepSeekOps.f32(wt));
        Tensor lg = Aten.one("aten::linear", DeepSeekOps.fakeQuantFp8(xg, 32), wt.to(cuda));
        report("linear bf16 vs f32", lg, lin);
        Tensor lgf = Aten.one("aten::linear", DeepSeekOps.f32(DeepSeekOps.fakeQuantFp8(xg, 32)), DeepSeekOps.f32(wt).to(cuda));
        report("linear f32 cuda", lgf, lin);
    }

    /** The fused kernels (CUDA) against the composed ATen path (CPU, which never uses them). */
    @Test
    public void testGivenFusedKernelsThenMatchComposedOps() {
        Assumptions.assumeTrue(CUDA.isAvailable() && CUDA.deviceCount() > 0 && Aten.isAvailable(), "no CUDA");
        Device cuda = Device.CUDA();
        for (ScalarType dt : new ScalarType[]{ScalarType.BFloat16, ScalarType.Float, ScalarType.Half}) {
            Tensor x = Aten.one("aten::mul.Tensor", Aten.one("aten::randn", new long[]{37, 4096}),
                    Aten.one("aten::logspace", -3.0, 2.0, 4096L)).to(dt);
            Tensor fused = DeepSeekOps.fakeQuantFp8(x.to(cuda), 32);
            assertEquals(0.0, rel(fused, DeepSeekOps.fakeQuantFp8(x, 32)), 0.0, "fakeQuantFp8 " + dt);
            assertEquals(dt, fused.dtype());
        }
        for (int rows : new int[]{1, 7, 301}) {
            Tensor mixes = Aten.one("aten::mul.Scalar", Aten.one("aten::randn", new long[]{1, rows, 24}), 3.0);
            Tensor scale = Aten.one("aten::randn", new long[]{3});
            Tensor base = Aten.one("aten::randn", new long[]{24});
            Tensor[] c = DeepSeekOps.hcSplitSinkhorn(mixes, scale, base, 4, 20, 1e-6);
            Tensor[] g = DeepSeekOps.hcSplitSinkhorn(mixes.to(cuda), scale.to(cuda), base.to(cuda), 4, 20, 1e-6);
            for (int i = 0; i < 3; i++) {
                assertTrue(rel(g[i], c[i]) < 1e-5, "sinkhorn[" + i + "] rows=" + rows + " err " + rel(g[i], c[i]));
                assertEquals(c[i].shape().length, g[i].shape().length);
            }
        }
    }

    private static Tensor randn(ScalarType dt, long... shape) {
        return Aten.one("aten::randn", shape).to(dt);
    }

    @Test
    public void testGivenFusedHyperConnectionOpsThenMatchComposedOps() {
        Assumptions.assumeTrue(CUDA.isAvailable() && CUDA.deviceCount() > 0 && Aten.isAvailable(), "no CUDA");
        Device cuda = Device.CUDA();
        final int hc = 4;
        final long dim = 512;
        for (ScalarType dt : new ScalarType[]{ScalarType.BFloat16, ScalarType.Float}) {
            for (long s : new long[]{1, 5, 40}) {
                Tensor x = randn(dt, s, dim);
                Tensor w = randn(ScalarType.BFloat16, dim);
                double tol = dt == ScalarType.Float ? 1e-5 : 5e-3; // bf16: one rounding flip is ~4e-3 of an element
                double e = rel(DeepSeekOps.rmsNorm(x.to(cuda), w.to(cuda), 1e-20), DeepSeekOps.rmsNorm(x, w, 1e-20));
                System.out.printf("FUSED rmsNorm %s rows=%d rel err %.3e%n", dt, s, e);
                assertTrue(e < tol, "rmsNorm " + dt + " " + e);

                Tensor stream = randn(dt, 1, s, hc, dim);
                Tensor pre = Aten.one("aten::softmax.int", randn(ScalarType.Float, 1, s, hc), -1L);
                double ep = rel(DeepSeekBlock.hcPre(stream.to(cuda), pre.to(cuda)), DeepSeekBlock.hcPre(stream, pre));
                System.out.printf("FUSED hcPre %s rows=%d rel err %.3e%n", dt, s, ep);
                assertTrue(ep < tol, "hcPre " + dt + " " + ep);

                Tensor h = randn(dt, 1, s, dim);
                Tensor post = Aten.one("aten::softmax.int", randn(ScalarType.Float, 1, s, hc), -1L);
                Tensor comb = Aten.one("aten::softmax.int", randn(ScalarType.Float, 1, s, hc, hc), -1L);
                Tensor a = Aten.one("aten::mul.Tensor", post.unsqueeze(-1), h.unsqueeze(-2));
                Tensor b = Aten.one("aten::sum.dim_IntList", Aten.one("aten::mul.Tensor", comb.unsqueeze(-1),
                        stream.unsqueeze(-2)), new long[]{2});
                Tensor expect = Aten.one("aten::add.Tensor", a, b).to(dt);
                Tensor got = smile.torch.Native.dsHcPost(h.to(cuda), stream.to(cuda), post.to(cuda), comb.to(cuda));
                assertTrue(got != null, "hcPost kernel unavailable");
                System.out.printf("FUSED hcPost %s rows=%d rel err %.3e%n", dt, s, rel(got, expect));
                assertTrue(rel(got, expect) < tol, "hcPost " + dt + " " + rel(got, expect));
            }
        }
        Tensor flat = randn(ScalarType.Float, 1, 9, hc * dim);
        Tensor fn = randn(ScalarType.Float, (2 + hc) * hc, hc * dim);
        Tensor scale = randn(ScalarType.Float, 3);
        Tensor base = randn(ScalarType.Float, (2 + hc) * hc);
        Tensor r = Aten.one("aten::rsqrt", Aten.one("aten::add.Scalar", Aten.one("aten::mean.dim",
                Aten.one("aten::mul.Tensor", flat, flat), new long[]{-1}, true), 1e-20));
        Tensor mixes = Aten.one("aten::mul.Tensor", Aten.one("aten::linear", flat, fn), r);
        Tensor[] want = DeepSeekOps.hcSplitSinkhorn(mixes, scale, base, hc, 20, 1e-6);
        Tensor fg = flat.to(cuda);
        Tensor[] got = smile.torch.Native.dsHcMixes(fg, Aten.one("aten::linear", fg, fn.to(cuda)), scale.to(cuda),
                base.to(cuda), hc, 20, 1e-6, 1e-20);
        assertTrue(got != null, "hcMixes kernel unavailable");
        for (int i = 0; i < 3; i++) {
            assertTrue(rel(got[i], want[i]) < 1e-4, "hcMixes[" + i + "] " + rel(got[i], want[i]));
        }
    }

    @Test
    public void testGivenManyRowsThenTensorCoreSparseAttentionMatchesComposedOps() {
        Assumptions.assumeTrue(CUDA.isAvailable() && CUDA.deviceCount() > 0 && Aten.isAvailable(), "no CUDA");
        Device cuda = Device.CUDA();
        final long d = 512;
        final long h = 8;
        final long m = 70;                  // above GEMM_MIN_ROWS, and 2 chunks with a tiny chunk size is not needed here
        final long n = 200;
        final long topk = 96;
        Tensor q = randn(ScalarType.BFloat16, 1, m, h, d);
        Tensor kv = randn(ScalarType.BFloat16, 1, n, d);
        Tensor sink = randn(ScalarType.Float, h);
        int[] ix = new int[(int) (m * topk)];
        for (int r = 0; r < m; r++) {
            for (int t = 0; t < topk; t++) {
                ix[(int) (r * topk + t)] = r == m - 1 ? -1                          // an all-masked row
                        : (t % 5 == 2 ? -1 : (int) ((t * 11L + r * 3L) % n));
            }
        }
        Tensor idxs = Tensor.of(ix, 1, m, topk);
        Tensor want = DeepSeekOps.sparseAttention(q, kv, sink, idxs, 0.0442);     // CPU: composed ops
        Tensor got = DeepSeekOps.sparseAttentionGemm(q.to(cuda), kv.to(cuda), sink.to(cuda), idxs.to(cuda), 0.0442);
        assertTrue(got != null, "tensor-core attention unavailable");
        double e = rel(got, want);
        System.out.printf("GEMM sparseAttention m=%d rel err %.3e%n", m, e);
        assertTrue(e < 1e-2, "sparseAttention gemm err " + e);
    }

    @Test
    public void testGivenFusedSparseAttentionThenMatchesComposedOps() {
        Assumptions.assumeTrue(CUDA.isAvailable() && CUDA.deviceCount() > 0 && Aten.isAvailable(), "no CUDA");
        Device cuda = Device.CUDA();
        final long d = 512;
        final long h = 16;
        for (long m : new long[]{1, 5}) {
            long n = 300;
            long topk = 192;
            Tensor q = randn(ScalarType.BFloat16, 1, m, h, d);
            Tensor kv = randn(ScalarType.BFloat16, 1, n, d);
            Tensor sink = randn(ScalarType.Float, h);
            int[] ix = new int[(int) (m * topk)];
            for (int r = 0; r < m; r++) {
                for (int t = 0; t < topk; t++) {
                    ix[(int) (r * topk + t)] = r == m - 1 && m > 1 ? -1                 // an all-masked row
                            : (t % 7 == 3 ? -1 : (int) ((t * 13L + r * 5L) % n));        // some masked slots
                }
            }
            Tensor idxs = Tensor.of(ix, 1, m, topk);
            Tensor want = DeepSeekOps.sparseAttention(q, kv, sink, idxs, 0.0442);
            Tensor got = DeepSeekOps.sparseAttention(q.to(cuda), kv.to(cuda), sink.to(cuda), idxs.to(cuda), 0.0442);
            double e = rel(got, want);
            System.out.printf("FUSED sparseAttention m=%d rel err %.3e%n", m, e);
            assertTrue(e < 5e-3, "sparseAttention m=" + m + " err " + e);
            assertEquals(want.shape().length, got.shape().length);
        }
    }

    @Test
    public void testGivenFusedMoeKernelsThenMatchComposedOps() {
        Assumptions.assumeTrue(CUDA.isAvailable() && CUDA.deviceCount() > 0 && Aten.isAvailable(), "no CUDA");
        Device cuda = Device.CUDA();
        // router
        int experts = 384;
        int topk = 6;
        Tensor logits = Aten.one("aten::mul.Scalar", randn(ScalarType.Float, 9, experts), 2.0);
        Tensor bias = Aten.one("aten::mul.Scalar", randn(ScalarType.Float, experts), 0.1);
        Tensor scores = Aten.one("aten::sqrt", Aten.one("aten::softplus", logits));
        Tensor wantIdx = Aten.call("aten::topk", Aten.one("aten::add.Tensor", scores, bias), (long) topk, -1L, true, true)[1];
        Tensor wantW = Aten.one("aten::gather", scores, 1L, wantIdx);
        wantW = Aten.one("aten::div.Tensor", wantW, Aten.one("aten::add.Scalar",
                Aten.one("aten::sum.dim_IntList", wantW, new long[]{-1}, true), 1e-20));
        wantW = Aten.one("aten::mul.Scalar", wantW, 1.5);
        Tensor[] got = smile.torch.Native.dsRoute(logits.to(cuda), bias.to(cuda), topk, 1.5);
        assertTrue(got != null, "route kernel unavailable");
        assertEquals(0.0, rel(got[0], wantIdx), 0.0, "route indices");
        assertTrue(rel(got[1], wantW) < 1e-5, "route weights " + rel(got[1], wantW));
        assertEquals(ScalarType.Int64, got[0].dtype());

        // SwiGLU (routed: weights + fp8 fake quantization; shared: plain)
        long inter = 2304;
        long rows = 12;
        Tensor gu = Aten.one("aten::mul.Scalar", randn(ScalarType.BFloat16, rows, 2 * inter), 6.0);
        Tensor w = Aten.one("aten::abs", randn(ScalarType.Float, rows));
        Tensor gate = DeepSeekOps.f32(Aten.one("aten::slice.Tensor", gu, 1L, 0L, inter));
        Tensor up = DeepSeekOps.f32(Aten.one("aten::slice.Tensor", gu, 1L, inter, 2 * inter));
        up = Aten.one("aten::clamp", up, -10.0, 10.0);
        gate = Aten.one("aten::clamp", gate, null, 10.0);
        Tensor plain = Aten.one("aten::mul.Tensor", Aten.one("aten::silu", gate), up);
        Tensor weighted = Aten.one("aten::mul.Tensor", plain, w.reshape(-1, 1)).to(ScalarType.BFloat16);
        Tensor wantQ = DeepSeekOps.fakeQuantFp8(weighted, 32);
        Tensor gotQ = smile.torch.Native.dsSwiglu(gu.to(cuda), w.to(cuda), 10.0, true);
        Tensor gotP = smile.torch.Native.dsSwiglu(gu.to(cuda), null, 10.0, false);
        assertTrue(gotQ != null && gotP != null, "swiglu kernel unavailable");
        assertTrue(rel(gotQ, wantQ) < 5e-3, "swiglu routed " + rel(gotQ, wantQ));
        assertTrue(rel(gotP, plain.to(ScalarType.BFloat16)) < 5e-3, "swiglu shared " + rel(gotP, plain.to(ScalarType.BFloat16)));

        // combine
        Tensor down = randn(ScalarType.BFloat16, 4 * topk, 64);
        Tensor wantSum = Aten.one("aten::sum.dim_IntList", DeepSeekOps.f32(down.reshape(4, topk, 64)), new long[]{1}, false);
        Tensor gotSum = smile.torch.Native.dsSumTopk(down.to(cuda), topk);
        assertTrue(gotSum != null, "sum_topk kernel unavailable");
        assertTrue(rel(gotSum, wantSum) < 1e-6, "sum_topk " + rel(gotSum, wantSum));
    }

    @Test
    public void testGivenFusedAttentionHelpersThenMatchComposedOps() {
        Assumptions.assumeTrue(CUDA.isAvailable() && CUDA.deviceCount() > 0 && Aten.isAvailable(), "no CUDA");
        Device cuda = Device.CUDA();
        // rope tail, 3-d and 4-d, forward and inverse
        Tensor f = DeepSeekOps.precomputeFreqsCis(64, 64, 0, 10000.0, 1.0, 32, 1);
        Tensor fc = Aten.one("aten::narrow", f, 0L, 3L, 5L).contiguous();
        for (boolean inv : new boolean[]{false, true}) {
            for (long[] shape : new long[][]{{1, 5, 8, 512}, {1, 5, 512}}) {
                Tensor x = randn(ScalarType.BFloat16, shape);
                Tensor want = Dsv.ropeTail(x, fc, 64, inv);
                Tensor got = smile.torch.Native.dsRopeTail(x.to(cuda), fc.to(cuda), 64, inv);
                assertTrue(got != null, "rope kernel unavailable");
                double e = rel(got, want);
                System.out.printf("FUSED ropeTail %s inverse=%b rel err %.3e%n", java.util.Arrays.toString(shape), inv, e);
                assertTrue(e < 5e-3, "ropeTail " + e);
            }
        }
        // fp4 fake quantization
        Tensor x = Aten.one("aten::mul.Tensor", randn(ScalarType.BFloat16, 37, 4096),
                Aten.one("aten::logspace", -2.0, 1.0, 4096L).to(ScalarType.BFloat16));
        for (int block : new int[]{32, 16}) {
            boolean e4m3 = block == 16;
            double e = rel(DeepSeekOps.fakeQuantFp4(x.to(cuda), block, e4m3), DeepSeekOps.fakeQuantFp4(x, block, e4m3));
            System.out.printf("FUSED fakeQuantFp4 block=%d rel err %.3e%n", block, e);
            assertEquals(0.0, e, 0.0, "fakeQuantFp4 block " + block);
        }
        // indexer score and finalize
        Tensor q = randn(ScalarType.BFloat16, 1, 3, 32, 128);
        Tensor k = randn(ScalarType.BFloat16, 1, 50, 128);
        Tensor w = randn(ScalarType.BFloat16, 1, 3, 32);
        Tensor want = Aten.one("aten::sum.dim_IntList", Aten.one("aten::mul.Tensor",
                Aten.one("aten::relu", Aten.one("aten::einsum", "bshd,btd->bsht", new Tensor[]{q, k})), w.unsqueeze(-1)),
                new long[]{2});
        Tensor got = smile.torch.Native.dsIndexScore(q.to(cuda), k.to(cuda), w.to(cuda));
        assertTrue(got != null, "index score kernel unavailable");
        assertTrue(rel(got, want) < 2e-2, "index score " + rel(got, want));
        Tensor idx = Aten.one("aten::randint", 20L, new long[]{1, 2, 5});
        Tensor lensRows = Tensor.of(new long[]{9, 15}, 2, 1);
        Tensor lensOne = Tensor.of(new long[]{12}, 1);
        for (Tensor lens : new Tensor[]{lensRows, lensOne}) {
            Tensor shifted = Aten.one("aten::add.Scalar", idx, 7L);
            Tensor expect = Aten.one("aten::where.ScalarOther", Aten.one("aten::lt.Tensor", idx, lens), shifted, -1.0)
                    .to(ScalarType.Int32);
            Tensor fin = smile.torch.Native.dsIndexFinalize(idx.to(cuda), lens.to(cuda), 7L);
            assertTrue(fin != null, "index finalize kernel unavailable");
            assertEquals(0.0, rel(fin, expect), 0.0, "index finalize");
            assertEquals(ScalarType.Int32, fin.dtype());
        }
    }
}
