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

import java.util.Random;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Aten;
import smile.torch.Native;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in ({@code DS_CUDA=true}): the tensor-core sparse attention (dense kv and per-row pointer tables) against the float32
 * composed reference on the CPU, with masked slots and rows without any valid key, and its time against the gather + GEMM path.
 * {@code SMILE_DS_ATTN_TC_SPLIT=1} selects the hi/lo probabilities of the dense variant (the pointer variant always uses them).
 */
@Tag("cuda")
public class DeepSeekAttentionTcTest {
    private static final int HEADS = 64;
    private static final int D = 512;

    private static Tensor randBf16(Random r, double scale, long... shape) {
        long n = 1;
        for (long s : shape) {
            n *= s;
        }
        float[] a = new float[(int) n];
        for (int i = 0; i < a.length; i++) {
            a[i] = (float) (r.nextGaussian() * scale);
        }
        return Tensor.of(a, shape).to(ScalarType.BFloat16);
    }

    private static double rel(Tensor a, Tensor b) {
        Tensor x = a.to(Device.CPU()).to(ScalarType.Float);
        Tensor y = b.to(Device.CPU()).to(ScalarType.Float);
        double n = Aten.one("aten::norm.Scalar", Aten.one("aten::sub.Tensor", x, y)).doubleValue();
        double d = Aten.one("aten::norm.Scalar", y).doubleValue();
        return n / Math.max(d, 1e-30);
    }

    /** {@code [m, topk]} int32: a random number of valid slots per row (some rows none), the rest -1. */
    private static Tensor indices(Random r, int m, int topk, int n) {
        int[] ix = new int[m * topk];
        for (int row = 0; row < m; row++) {
            int valid = row % 7 == 3 ? 0 : (row % 5 == 0 ? topk : 1 + r.nextInt(topk));
            for (int j = 0; j < topk; j++) {
                ix[row * topk + j] = j < valid ? r.nextInt(n) : -1;
            }
            // scatter the masked slots (holes in the middle of tiles)
            for (int j = 0; j < topk / 8; j++) {
                ix[row * topk + r.nextInt(topk)] = -1;
            }
        }
        return Tensor.of(ix, 1, m, topk);
    }

    @Test
    public void testGivenMaskedRowsThenMatchesFloat32Reference() {
        Assumptions.assumeTrue("true".equals(System.getenv("DS_CUDA")), "set DS_CUDA=true");
        Device cuda = Device.CUDA();
        Random r = new Random(7);
        final int m = 48;
        final int topk = 640;
        final int n = 3000;
        Tensor q = randBf16(r, 1.0, 1, m, HEADS, D);
        Tensor kv = randBf16(r, 1.0, 1, n, D);
        Tensor sink = Tensor.of(new float[]{-1f, 0.5f, 2f, 0f, -3f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
                -1f, -1f, -1f, -1f, -1f, -1f, -1f, -1f, 2f, 2f, 2f, 2f, 2f, 2f, 2f, 2f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f}, HEADS);
        Tensor idxs = indices(r, m, topk, n);
        double scale = 1.0 / Math.sqrt(D);
        Tensor want = DeepSeekOps.sparseAttention(q, kv, sink, idxs, scale);                  // CPU: the composed float32 chain
        Tensor got = Native.dsSparseAttention(q.to(cuda), kv.to(cuda), sink.to(cuda), idxs.to(cuda), scale);
        assertTrue(got != null, "kernel unavailable");
        double e = rel(got, want);
        System.out.printf("ATTN-TC dense  rel err %.3e%n", e);
        assertTrue(e < 6e-3, "dense " + e);

        // per-row pointer tables: index < window reads the row's ring, the rest its compressed cache
        final int window = 128;
        final int nc = 300;
        Tensor[] rings = new Tensor[m];
        Tensor[] comps = new Tensor[m];
        long[] wp = new long[m];
        long[] cp = new long[m];
        Tensor[] all = new Tensor[2 * m];
        Tensor[] cat = new Tensor[m];
        int[] pix = new int[m * topk];
        for (int row = 0; row < m; row++) {
            rings[row] = randBf16(r, 1.0, window, D).to(cuda);
            comps[row] = randBf16(r, 1.0, nc, D).to(cuda);
            all[2 * row] = rings[row];
            all[2 * row + 1] = comps[row];
            cat[row] = Aten.one("aten::cat", new Tensor[]{rings[row].to(Device.CPU()), comps[row].to(Device.CPU())}, 0L);
            int valid = row % 7 == 3 ? 0 : 1 + r.nextInt(topk);
            for (int j = 0; j < topk; j++) {
                pix[row * topk + j] = j < valid && r.nextInt(9) != 0 ? r.nextInt(window + nc) : -1;
            }
        }
        long[] ptrs = Native.dsDataPtrs(all);
        for (int row = 0; row < m; row++) {
            wp[row] = ptrs[2 * row];
            cp[row] = ptrs[2 * row + 1];
        }
        Tensor pidx = Tensor.of(pix, 1, m, topk);
        // reference per row: a dense kv of [ring; compressed] and the same indices
        Tensor wantP = null;
        Tensor[] outs = new Tensor[m];
        for (int row = 0; row < m; row++) {
            outs[row] = DeepSeekOps.sparseAttention(Aten.one("aten::narrow", q, 1L, (long) row, 1L), cat[row].unsqueeze(0), sink,
                    Aten.one("aten::narrow", pidx, 1L, (long) row, 1L), scale);
        }
        wantP = Aten.one("aten::cat", outs, 1L);
        Tensor gotP = Native.dsSparseAttentionPtrs(q.to(cuda), Tensor.of(wp, m).to(cuda), Tensor.of(cp, m).to(cuda), window,
                sink.to(cuda), pidx.to(cuda), scale);
        assertTrue(gotP != null, "ptr kernel unavailable");
        double ep = rel(gotP, wantP);
        System.out.printf("ATTN-TC ptrs   rel err %.3e%n", ep);
        assertTrue(ep < 3e-3, "ptrs " + ep);
    }

    @Test
    public void testPrintsPrefillTimes() {
        Assumptions.assumeTrue("true".equals(System.getenv("DS_CUDA")), "set DS_CUDA=true");
        Device cuda = Device.CUDA();
        Random r = new Random(11);
        final int topk = 640;
        for (int m : new int[]{512, 2048}) {
            final int n = m + 1024;
            Tensor q = randBf16(r, 1.0, 1, 256, HEADS, D);
            q = Aten.one("aten::repeat", q, new long[]{1, m / 256, 1, 1}).to(cuda);
            Tensor kv = Aten.one("aten::repeat", randBf16(r, 1.0, 1, 256, D), new long[]{1, n / 256 + 1, 1}).to(cuda);
            Tensor sink = Aten.one("aten::zeros", new long[]{HEADS}, ScalarType.Float).to(cuda);
            Tensor idxs = indices(r, m, topk, n).to(cuda);
            final Tensor fq = q, fkv = kv, fsink = sink, fidx = idxs;
            double scale = 1.0 / Math.sqrt(D);
            for (int mode = 0; mode < 2; mode++) {
                final boolean tc = mode == 0;
                // each run in its own scope: the temporaries of the gather + GEMM path (GBs) are freed at once
                java.util.function.Supplier<Tensor> run = () -> Dsv.scoped(() -> tc ? Native.dsSparseAttention(fq, fkv, fsink, fidx, scale)
                        : DeepSeekOps.sparseAttentionGemm(fq, fkv, fsink, fidx, scale));
                for (int i = 0; i < 3; i++) {
                    run.get().close();
                }
                Aten.one("aten::sum", fsink).doubleValue();
                long t0 = System.nanoTime();
                int reps = 10;
                Tensor last = null;
                for (int i = 0; i < reps; i++) {
                    if (last != null) {
                        last.close();
                    }
                    last = run.get();
                }
                Aten.one("aten::sum", last.to(ScalarType.Float)).doubleValue();
                double ms = (System.nanoTime() - t0) / 1e6 / reps;
                System.out.printf("ATTN-TC prefill rows=%d %s: %.3f ms%n", m, tc ? "tensor-core kernel" : "gather + GEMM", ms);
                if (tc && System.getenv("DS_TC_PROF") != null) {
                    Native.profStart(0);
                    for (int i = 0; i < 5; i++) {
                        run.get().close();
                    }
                    Aten.one("aten::sum", fsink).doubleValue();
                    String tab = Native.profStop();
                    for (String line : tab.split("\n")) {
                        if (line.contains("sparse_attention")) {
                            System.out.println("ATTN-TC kernel rows=" + m + ": " + line.trim().substring(0, Math.min(90, line.trim().length())));
                        }
                    }
                }
            }
        }
    }
}
