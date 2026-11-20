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
package smile.llm.model.glm;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Aten;
import smile.torch.Native;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The fused KDA kernel ({@code smile_kda_forward}) against a token-by-token ATen implementation of the same math, on
 * random data: two requests in different slots of a three-slot pool, a prefill of 37 tokens then single-token decode
 * steps, so the carried conv and recurrent states and the slot indexing are checked. Opt-in: {@code GLM_CUDA=true}.
 */
public class GlmKdaKernelTest {
    private static Tensor o(String op, Object... args) {
        return Aten.one(op, args);
    }

    private static Tensor randn(Device d, long... shape) {
        return o("aten::randn", shape, ScalarType.Float).to(d);
    }

    private static double rel(Tensor a, Tensor b) {
        Tensor diff = o("aten::sub.Tensor", a, b);
        return o("aten::linalg_vector_norm", diff, 2.0).doubleValue() / o("aten::linalg_vector_norm", b, 2.0).doubleValue();
    }

    /** Reference state of one request. */
    private static final class Ref {
        Tensor conv;   // [3, C]
        Tensor rec;    // [H, 128, 128]
    }

    /** One call of the reference for tokens {@code [T]} of one request; returns {@code [T, H, 128]}. */
    private static Tensor reference(Ref st, Tensor raw, Tensor fg, Tensor braw, Tensor convw, Tensor aLog, Tensor dtBias,
                                    int h, double bound) {
        long n = raw.size(0);
        int d = 128;
        long w = (long) h * d;
        Tensor full = o("aten::cat", new Tensor[]{st.conv, raw}, 0L);                       // [3 + n, C]
        Tensor conv = null;
        for (int j = 0; j < 4; j++) {
            Tensor term = o("aten::mul.Tensor", o("aten::narrow", full, 0L, (long) j, n), o("aten::select.int", convw, 1L, (long) j));
            conv = conv == null ? term : o("aten::add.Tensor", conv, term);
        }
        conv = o("aten::silu", conv);
        st.conv = o("aten::clone", o("aten::narrow", full, 0L, n, 3L));
        Tensor q = o("aten::reshape", o("aten::narrow", conv, 1L, 0L, w), new long[]{n, h, d});
        Tensor k = o("aten::reshape", o("aten::narrow", conv, 1L, w, w), new long[]{n, h, d});
        Tensor v = o("aten::reshape", o("aten::narrow", conv, 1L, 2 * w, w), new long[]{n, h, d});
        q = o("aten::div.Tensor", q, o("aten::sqrt", o("aten::add.Scalar", o("aten::sum.dim_IntList", o("aten::mul.Tensor", q, q), new long[]{-1}, true), 1e-6)));
        k = o("aten::div.Tensor", k, o("aten::sqrt", o("aten::add.Scalar", o("aten::sum.dim_IntList", o("aten::mul.Tensor", k, k), new long[]{-1}, true), 1e-6)));
        q = o("aten::mul.Scalar", q, Math.pow(d, -0.5));
        Tensor g = o("aten::mul.Scalar", o("aten::sigmoid", o("aten::mul.Tensor",
                o("aten::reshape", o("aten::add.Tensor", fg, dtBias), new long[]{n, h, d}),
                o("aten::reshape", o("aten::exp", aLog), new long[]{1, h, 1}))), bound);
        Tensor decay = o("aten::exp", g);
        Tensor beta = o("aten::sigmoid", braw);
        Tensor s = st.rec;
        Tensor[] outs = new Tensor[(int) n];
        for (int t = 0; t < n; t++) {
            Tensor qt = o("aten::select.int", q, 0L, (long) t);
            Tensor kt = o("aten::select.int", k, 0L, (long) t);
            Tensor vt = o("aten::select.int", v, 0L, (long) t);
            Tensor bt = o("aten::unsqueeze", o("aten::select.int", beta, 0L, (long) t), -1L);
            Tensor dt = o("aten::unsqueeze", o("aten::select.int", decay, 0L, (long) t), -1L);
            s = o("aten::mul.Tensor", s, dt);
            Tensor kvm = o("aten::bmm", o("aten::unsqueeze", kt, 1L), s);
            Tensor delta = o("aten::mul.Tensor", o("aten::sub.Tensor", o("aten::unsqueeze", vt, 1L), kvm), o("aten::unsqueeze", bt, -1L));
            s = o("aten::add.Tensor", s, o("aten::bmm", o("aten::unsqueeze", kt, 2L), delta));
            outs[t] = o("aten::reshape", o("aten::bmm", o("aten::unsqueeze", qt, 1L), s), new long[]{1, h, d});
        }
        st.rec = o("aten::clone", s);
        return o("aten::cat", outs, 0L);
    }

    @Test
    public void testGivenRandomInputsThenKernelEqualsReferenceAcrossPrefillAndDecode() {
        Assumptions.assumeTrue("true".equals(System.getenv("GLM_CUDA")), "set GLM_CUDA=true");
        Assumptions.assumeTrue(Native.kdaAvailable(), "no KDA kernel in the native library");
        Device dev = Device.CUDA((byte) 0);
        int h = 3;
        int d = 128;
        int w = h * d;
        int c = 3 * w;
        int pool = 3;
        Tensor convw = o("aten::mul.Scalar", randn(dev, c, 4), 0.5);
        Tensor aLog = o("aten::mul.Scalar", randn(dev, h), 0.3);
        Tensor dtBias = o("aten::mul.Scalar", randn(dev, w), 0.5);
        Tensor convState = o("aten::zeros", new long[]{pool, 3, c}, ScalarType.Float).to(dev);
        Tensor recState = o("aten::zeros", new long[]{pool, h, d, d}, ScalarType.Float).to(dev);
        // make the pool's other slot non-zero to catch indexing mistakes: slot 2 stays untouched
        Tensor sentinel = o("aten::select.int", recState, 0L, 2L);
        Aten.call("aten::fill_.Scalar", sentinel, 0.123);
        Tensor slots = Tensor.of(new int[]{1, 0}, 2).to(dev);
        Ref[] ref = new Ref[2];
        for (int i = 0; i < 2; i++) {
            ref[i] = new Ref();
            ref[i].conv = o("aten::zeros", new long[]{3, c}, ScalarType.Float).to(dev);
            ref[i].rec = o("aten::zeros", new long[]{h, d, d}, ScalarType.Float).to(dev);
        }
        int[] steps = {37, 1, 1, 5, 2, 1};
        for (int t : steps) {
            Tensor raw = o("aten::mul.Scalar", randn(dev, 2, t, c), 2.0);
            Tensor fg = randn(dev, 2, t, w);
            Tensor braw = randn(dev, 2, t, h);
            Tensor out = Native.kdaForward(raw, fg, braw, convw, aLog, dtBias, convState, recState, slots, -5.0);
            for (int b = 0; b < 2; b++) {
                Tensor want = reference(ref[b], o("aten::select.int", raw, 0L, (long) b), o("aten::select.int", fg, 0L, (long) b),
                        o("aten::select.int", braw, 0L, (long) b), convw, aLog, dtBias, h, -5.0);
                double e = rel(o("aten::select.int", out, 0L, (long) b), want);
                assertTrue(e < 2e-5, "T=" + t + " request " + b + " output rel err " + e);
                int slot = b == 0 ? 1 : 0;
                double es = rel(o("aten::select.int", recState, 0L, (long) slot), ref[b].rec);
                double ec = rel(o("aten::select.int", convState, 0L, (long) slot), ref[b].conv);
                assertTrue(es < 2e-5, "T=" + t + " request " + b + " state rel err " + es);
                assertTrue(ec < 1e-6, "T=" + t + " request " + b + " conv state rel err " + ec);
            }
        }
        assertEquals(0.123, o("aten::mean", o("aten::select.int", recState, 0L, 2L)).doubleValue(), 1e-6, "untouched slot");
    }

    /** Prints kernel times (no assertions): decode with 1 and 32 requests, and a long prefill, at the real model size (64 heads). */
    @Test
    public void testBenchmark() {
        Assumptions.assumeTrue("true".equals(System.getenv("GLM_CUDA")) && System.getenv("GLM_BENCH") != null, "set GLM_BENCH=1");
        Device dev = Device.CUDA((byte) 0);
        int h = 64;
        int w = h * 128;
        int c = 3 * w;
        Tensor convw = o("aten::mul.Scalar", randn(dev, c, 4), 0.5);
        Tensor aLog = o("aten::mul.Scalar", randn(dev, h), 0.3);
        Tensor dtBias = o("aten::mul.Scalar", randn(dev, w), 0.5);
        int pool = 32;
        Tensor convState = o("aten::zeros", new long[]{pool, 3, c}, ScalarType.Float).to(dev);
        Tensor recState = o("aten::zeros", new long[]{pool, h, 128, 128}, ScalarType.Float).to(dev);
        int[][] cases = {{1, 1}, {32, 1}, {1, 512}, {1, 8192}};
        for (int[] cs : cases) {
            int b = cs[0];
            int t = cs[1];
            int[] sl = new int[b];
            for (int i = 0; i < b; i++) {
                sl[i] = i % pool;
            }
            Tensor slots = Tensor.of(sl, b).to(dev);
            Tensor raw = o("aten::mul.Scalar", randn(dev, b, t, c), 2.0);
            Tensor fg = randn(dev, b, t, w);
            Tensor braw = randn(dev, b, t, h);
            int reps = t >= 512 ? 5 : 50;
            for (int i = 0; i < 3; i++) {
                Native.kdaForward(raw, fg, braw, convw, aLog, dtBias, convState, recState, slots, -5.0);
            }
            o("aten::sum", convState).doubleValue();
            long t0 = System.nanoTime();
            for (int i = 0; i < reps; i++) {
                Native.kdaForward(raw, fg, braw, convw, aLog, dtBias, convState, recState, slots, -5.0);
            }
            o("aten::sum", convState).doubleValue();
            double us = (System.nanoTime() - t0) / 1e3 / reps;
            System.out.printf("KDA B=%d T=%d: %.0f us per call, %.2f us per token-step%n", b, t, us, us / t);
        }
    }
}
