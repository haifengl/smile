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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.InklingNative;

import static org.junit.jupiter.api.Assertions.*;
import static smile.llm.model.inkling.Ix.*;

/**
 * The Inkling decode kernels against the torch ops of the reference path, on random data.
 *
 * <p>Opt-in, GPU: {@code INKLING_KERNELS=1} and {@code SMILE_INKLING_LIB} (or a libsmile_torch with the kernels).
 *
 * @author Haifeng Li
 */
public class InklingKernelTest {
    private static final Random RNG = new Random(7);
    private static final int D = 2304;

    private static Tensor random(Device dev, ScalarType type, double scale, long... shape) {
        long n = 1;
        for (long s : shape) {
            n *= s;
        }
        float[] a = new float[(int) n];
        for (int i = 0; i < a.length; i++) {
            a[i] = (float) (RNG.nextGaussian() * scale);
        }
        Tensor t = Tensor.of(a, shape).to(dev);
        return type == ScalarType.Float ? t : t.to(type);
    }

    private static double maxAbs(Tensor a, Tensor b) {
        float[] x = f32(a).to(Device.CPU()).floatArray();
        float[] y = f32(b).to(Device.CPU()).floatArray();
        double m = 0;
        for (int i = 0; i < x.length; i++) {
            m = Math.max(m, Math.abs(x[i] - y[i]));
        }
        return m;
    }

    private static boolean ready() {
        return System.getenv("INKLING_KERNELS") != null && InklingNative.isAvailable();
    }

    @Test
    public void testGivenRandomStatesWhenAttnResThenMatchesTorch() {
        Assumptions.assumeTrue(ready(), "set INKLING_KERNELS=1 and SMILE_INKLING_LIB");
        Device dev = Device.CUDA((byte) 0);
        for (int nb : new int[]{1, 3}) {
            for (int nq : new int[]{1, 3, 4}) {
                for (int n : new int[]{1, 3, 5}) {
                    List<Tensor> blocks = new ArrayList<>();
                    for (int i = 0; i < n - 1; i++) {
                        blocks.add(random(dev, ScalarType.BFloat16, 2.0, nb, D));
                    }
                    Tensor partial = random(dev, ScalarType.Float, 1.5, nb, D);
                    Tensor q = random(dev, ScalarType.Float, 1.0, nq, D);
                    Tensor expected = InklingLayer.attnRes(blocks, partial, q);
                    Tensor out = zeros(dev, ScalarType.Float, nb, nq, D);
                    long[] p = new long[2 + n];
                    p[0] = InklingNative.dataPtr(out);
                    p[1] = InklingNative.dataPtr(q);
                    for (int i = 0; i < n - 1; i++) {
                        p[2 + i] = InklingNative.dataPtr(blocks.get(i));
                    }
                    p[2 + n - 1] = InklingNative.dataPtr(partial);
                    InklingNative.op(1, p, new long[]{nb, nq, D, n, 1L << (n - 1)}, new double[0]);
                    double diff = maxAbs(out, expected);
                    assertTrue(diff < 2e-5, "attnres nb=" + nb + " nq=" + nq + " n=" + n + " differs by " + diff);
                }
            }
        }
    }

    @Test
    public void testGivenRowsWhenRmsNormThenMatchesTorch() {
        Assumptions.assumeTrue(ready(), "set INKLING_KERNELS=1 and SMILE_INKLING_LIB");
        Device dev = Device.CUDA((byte) 0);
        int nb = 3;
        Tensor x = random(dev, ScalarType.Float, 3.0, nb, 3, D);            // three selections per row
        Tensor[] w = new Tensor[3];
        Tensor[] y = new Tensor[3];
        Tensor[] y32 = new Tensor[3];
        long[] p = new long[12];
        for (int s = 0; s < 3; s++) {
            w[s] = random(dev, ScalarType.Float, 1.0, D);
            y[s] = zeros(dev, ScalarType.BFloat16, nb, D);
            y32[s] = zeros(dev, ScalarType.Float, nb, D);
            p[s] = InklingNative.dataPtr(x) + (long) s * D * 4;
            p[3 + s] = InklingNative.dataPtr(w[s]);
            p[6 + s] = InklingNative.dataPtr(y[s]);
            p[9 + s] = InklingNative.dataPtr(y32[s]);
        }
        InklingNative.op(2, p, new long[]{nb, D, 3, 3L * D, 0, 0}, new double[]{1e-6});
        for (int s = 0; s < 3; s++) {
            Tensor expected = bf16(rms(contiguous(o("aten::select.int", x, 1L, (long) s)), w[s], 1e-6));
            assertTrue(maxAbs(y[s], expected) < 1e-2 * 8, "rms output " + s);
            assertTrue(maxAbs(y32[s], bf16(rms(contiguous(o("aten::select.int", x, 1L, (long) s)), w[s], 1e-6))) < 0.05, "rms f32 " + s);
        }
    }

    @Test
    public void testGivenGatesWhenAppliedThenMatchesTorch() {
        Assumptions.assumeTrue(ready(), "set INKLING_KERNELS=1 and SMILE_INKLING_LIB");
        Device dev = Device.CUDA((byte) 0);
        int nb = 2;
        int rank = 64;
        int n = 3;
        Tensor[] hf = new Tensor[n], mf = new Tensor[n], down = new Tensor[n], fw = new Tensor[n], gw = new Tensor[n];
        Tensor[] out = new Tensor[n], gd = new Tensor[n];
        long[] p = new long[7 * n];
        for (int g = 0; g < n; g++) {
            hf[g] = random(dev, ScalarType.Float, 1.5, nb, D);
            mf[g] = random(dev, ScalarType.Float, 0.7, nb, D);
            down[g] = random(dev, ScalarType.BFloat16, 0.02, rank, 2 * D);
            fw[g] = random(dev, ScalarType.Float, 0.05, D, rank);
            gw[g] = random(dev, ScalarType.Float, 0.05, D, rank);
            out[g] = zeros(dev, ScalarType.Float, nb, D);
            gd[g] = zeros(dev, ScalarType.Float, nb, rank);
            p[g] = InklingNative.dataPtr(hf[g]);
            p[n + g] = InklingNative.dataPtr(mf[g]);
            p[2 * n + g] = InklingNative.dataPtr(down[g]);
            p[3 * n + g] = InklingNative.dataPtr(fw[g]);
            p[4 * n + g] = InklingNative.dataPtr(gw[g]);
            p[5 * n + g] = InklingNative.dataPtr(out[g]);
            p[6 * n + g] = InklingNative.dataPtr(gd[g]);
        }
        long[] iv = {nb, D, rank, n, D, D};
        InklingNative.op(3, p, iv, new double[]{1e-6});
        InklingNative.op(4, p, iv, new double[]{1e-6});
        for (int g = 0; g < n; g++) {
            Tensor cat = cat(-1, unweightedRms(hf[g], 1e-6), unweightedRms(mf[g], 1e-6));
            Tensor gdown = f32(linear(bf16(cat), down[g]));
            Tensor forget = tanh(linear(gdown, fw[g]));
            Tensor gain = tanh(linear(gdown, gw[g]));
            Tensor expected = add(mul(o("aten::rsub.Scalar", forget, 1.0), hf[g]), mul(gain, mf[g]));
            assertTrue(maxAbs(gd[g], gdown) < 0.05, "gate down " + g + ": " + maxAbs(gd[g], gdown));
            assertTrue(maxAbs(out[g], expected) < 0.02, "gate out " + g + ": " + maxAbs(out[g], expected));
        }
    }

    @Test
    public void testGivenLogitsWhenRoutedThenMatchesTorchTopk() {
        Assumptions.assumeTrue(ready(), "set INKLING_KERNELS=1 and SMILE_INKLING_LIB");
        Device dev = Device.CUDA((byte) 0);
        int nb = 5;
        Tensor logits = random(dev, ScalarType.Float, 2.0, nb, 128);
        Tensor bias = random(dev, ScalarType.Float, 0.1, 128);
        Tensor idx = zeros(dev, ScalarType.Int32, nb, 8);
        Tensor wts = zeros(dev, ScalarType.Float, nb, 8);
        InklingNative.op(9, new long[]{InklingNative.dataPtr(logits), InklingNative.dataPtr(bias), InklingNative.dataPtr(idx),
                InklingNative.dataPtr(wts)}, new long[]{nb, 128, 8}, new double[]{8.0});
        Tensor scores = sigmoid(logits);
        Tensor top = smile.torch.Aten.call("aten::topk", add(scores, bias), 8L, -1L, true, true)[1];
        Tensor w = o("aten::gather", scores, 1L, top);
        w = mul(o("aten::div.Tensor", w, o("aten::sum.dim_IntList", w, new long[]{-1}, true)), 8.0);
        assertEquals(0.0, maxAbs(idx, top.to(ScalarType.Int32)), "selected experts (in rank order)");
        assertTrue(maxAbs(wts, w) < 1e-5, "routing weights " + maxAbs(wts, w));
    }

    @Test
    public void testGivenAssignmentsWhenAlignedThenEachBlockHoldsOneExpert() {
        Assumptions.assumeTrue(ready(), "set INKLING_KERNELS=1 and SMILE_INKLING_LIB");
        Device dev = Device.CUDA((byte) 0);
        int tokens = 3;
        int[] ids = {5, 7, 5, 0, 127, 5, 7, 9, 9, 9, 1, 2, 3, 4, 5, 6};
        ids = java.util.Arrays.copyOf(ids, tokens * 8);
        for (int block : new int[]{8, 16}) {
            int numel = ids.length;
            int padded = (numel + 128 * (block - 1) + block - 1) / block * block;
            int blocks = padded / block;
            Tensor in = Tensor.of(ids, ids.length).to(dev);
            Tensor out = zeros(dev, ScalarType.Int32, padded + blocks + 1);
            InklingNative.op(13, new long[]{InklingNative.dataPtr(in), InklingNative.dataPtr(out)},
                    new long[]{numel, 128, block, padded, blocks}, new double[0]);
            int[] r = out.to(Device.CPU()).intArray();
            Tensor ref = smile.torch.Native.moeAlignBlockSize(in, 0, 128, block);
            int[] expect = ref.to(Device.CPU()).intArray();
            assertEquals(expect[padded + blocks], r[padded + blocks], "padded length");
            int total = r[padded + blocks];
            java.util.Map<Integer, java.util.TreeSet<Integer>> got = new java.util.HashMap<>();
            java.util.Map<Integer, java.util.TreeSet<Integer>> want = new java.util.HashMap<>();
            for (int b = 0; b < total / block; b++) {
                assertEquals(expect[padded + b], r[padded + b], "expert of block " + b);
                for (int k = 0; k < block; k++) {
                    int a = r[b * block + k];
                    int e = expect[b * block + k];
                    if (a != numel) {
                        got.computeIfAbsent(r[padded + b], x -> new java.util.TreeSet<>()).add(a);
                        assertEquals(r[padded + b], ids[a], "assignment " + a + " in the wrong block");
                    }
                    if (e != numel) {
                        want.computeIfAbsent(expect[padded + b], x -> new java.util.TreeSet<>()).add(e);
                    }
                }
            }
            assertEquals(want, got, "assignments per expert, block " + block);
        }
    }
}
