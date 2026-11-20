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
 * The speculative-window kernels against the sequential decode kernels: a window of R rows run in one call and then
 * committed to {@code a} rows must leave exactly the state (and produce the rows) of processing those {@code a} rows
 * one call at a time. Opt-in: {@code GLM_CUDA=true}.
 */
public class GlmWindowKernelTest {
    private static final Device DEV = Device.CUDA((byte) 0);

    private static Tensor o(String op, Object... args) {
        return Aten.one(op, args);
    }

    private static Tensor randn(long... shape) {
        return o("aten::randn", shape, ScalarType.Float).to(DEV);
    }

    private static Tensor clone(Tensor t) {
        return o("aten::clone", t);
    }

    private static double maxAbs(Tensor a, Tensor b) {
        return o("aten::max", o("aten::abs", o("aten::sub.Tensor", a, b))).doubleValue();
    }

    private static Tensor sel(Tensor t, long i) {
        return o("aten::select.int", t, 0L, i);
    }

    private static Tensor ints(int... v) {
        return Tensor.of(v, v.length).to(DEV);
    }

    @Test
    public void testGivenWindowThenKdaCommitEqualsSequentialRows() {
        Assumptions.assumeTrue("true".equals(System.getenv("GLM_CUDA")), "set GLM_CUDA=true");
        Assumptions.assumeTrue(Native.windowAvailable(), "no window kernels in the native library");
        int h = 3, d = 128, w = h * d, c = 3 * w, slots = 3, window = 4;
        Tensor convw = o("aten::mul.Scalar", randn(c, 4), 0.5);
        Tensor aLog = o("aten::mul.Scalar", randn(h), 0.3);
        Tensor dtBias = o("aten::mul.Scalar", randn(w), 0.5);
        Tensor conv0 = o("aten::zeros", new long[]{slots, 3, c}, ScalarType.Float).to(DEV);
        Tensor rec0 = o("aten::zeros", new long[]{slots, h, d, d}, ScalarType.Float).to(DEV);
        Tensor slotT = ints(2, 0);
        // some history in slots 2 and 0
        Tensor raw0 = o("aten::mul.Scalar", randn(2, 11, c), 2.0);
        Native.kdaForward(raw0, randn(2, 11, w), randn(2, 11, h), convw, aLog, dtBias, conv0, rec0, slotT, -5.0);

        Tensor raw = o("aten::mul.Scalar", randn(2, window, c), 2.0);
        Tensor fg = randn(2, window, w);
        Tensor braw = randn(2, window, h);
        Tensor save = o("aten::zeros", new long[]{2, window, h, d, d}, ScalarType.Float).to(DEV);
        Tensor conv1 = clone(conv0), rec1 = clone(rec0);
        Tensor out = Native.kdaForwardWindow(raw, fg, braw, convw, aLog, dtBias, conv1, rec1, slotT, save, -5.0);
        assertEquals(0.0, maxAbs(conv1, conv0), "the window forward must not touch the conv state");

        // full window on the reference path
        Tensor convR = clone(conv0), recR = clone(rec0);
        Tensor outR = Native.kdaForward(raw, fg, braw, convw, aLog, dtBias, convR, recR, slotT, -5.0);
        assertEquals(0.0, maxAbs(out, outR), "window rows");
        assertEquals(0.0, maxAbs(rec1, recR), "state after the whole window");

        for (int a0 = 1; a0 <= window; a0++) {
            for (int a1 = 1; a1 <= window; a1 += 2) {
                Tensor cc = clone(conv1), rc = clone(rec1);
                Native.kdaCommit(raw, cc, rc, save, slotT, ints(a0, a1));
                // reference: each request alone with its first a rows
                Tensor cr = clone(conv0), rr = clone(rec0);
                int[] acc = {a0, a1};
                int[] sl = {2, 0};
                for (int b = 0; b < 2; b++) {
                    Native.kdaForward(o("aten::narrow", o("aten::narrow", raw, 0L, (long) b, 1L), 1L, 0L, (long) acc[b]),
                            o("aten::narrow", o("aten::narrow", fg, 0L, (long) b, 1L), 1L, 0L, (long) acc[b]),
                            o("aten::narrow", o("aten::narrow", braw, 0L, (long) b, 1L), 1L, 0L, (long) acc[b]),
                            convw, aLog, dtBias, cr, rr, ints(sl[b]), -5.0);
                }
                assertEquals(0.0, maxAbs(rc, rr), "recurrent state accept=" + a0 + "," + a1);
                assertEquals(0.0, maxAbs(cc, cr), "conv state accept=" + a0 + "," + a1);
            }
        }
    }

    private static final int CAP = 64;

    private record Req(Tensor lat, Tensor pool, Tensor ring) {
        Req copy() {
            return new Req(GlmWindowKernelTest.clone(lat), GlmWindowKernelTest.clone(pool), GlmWindowKernelTest.clone(ring));
        }
    }

    private static Req newReq() {
        return new Req(o("aten::zeros", new long[]{CAP, 512}, ScalarType.BFloat16).to(DEV),
                o("aten::zeros", new long[]{CAP / 4, 128}, ScalarType.BFloat16).to(DEV),
                o("aten::zeros", new long[]{4, 256}, ScalarType.Float).to(DEV));
    }

    private static Tensor ptrs(Tensor... ts) {
        return Tensor.of(Native.dsDataPtrs(ts), ts.length).to(DEV);
    }

    @Test
    public void testGivenWindowThenMlaCommitEqualsSequentialAppends() {
        Assumptions.assumeTrue("true".equals(System.getenv("GLM_CUDA")), "set GLM_CUDA=true");
        Assumptions.assumeTrue(Native.windowAvailable() && Native.mlaDecodeAvailable(), "no window kernels in the native library");
        int window = 4;
        int[] start = {5, 6};              // the windows cross a pool boundary (positions 7 and 8 / 7 and 11)
        Tensor ape = randn(4, 128);
        Req[] base = {newReq(), newReq()};
        for (int b = 0; b < 2; b++) {
            for (int t = 0; t < start[b]; t++) {
                Native.mlaDecodeAppend(randn(1, 512), randn(1, 128), randn(1, 128), ape, ptrs(base[b].lat()), ptrs(base[b].pool()),
                        ptrs(base[b].ring()), ints(t));
            }
        }
        Tensor lat = randn(2 * window, 512), key = randn(2 * window, 128), gate = randn(2 * window, 128);
        for (int a0 = 1; a0 <= window; a0++) {
            for (int a1 = 1; a1 <= window; a1++) {
                Req[] win = {base[0].copy(), base[1].copy()};
                Req[] ref = {base[0].copy(), base[1].copy()};
                Tensor lens = ints(start[0], start[1]);
                Native.mlaWindowAppend(lat, key, gate, ape, ptrs(win[0].lat(), win[1].lat()), ptrs(win[0].pool(), win[1].pool()),
                        ptrs(win[0].ring(), win[1].ring()), lens, window);
                Native.mlaWindowCommit(key, gate, ptrs(win[0].ring(), win[1].ring()), lens, ints(a0, a1), window);
                int[] acc = {a0, a1};
                for (int b = 0; b < 2; b++) {
                    for (int j = 0; j < acc[b]; j++) {
                        long r = (long) b * window + j;
                        Native.mlaDecodeAppend(o("aten::narrow", lat, 0L, r, 1L), o("aten::narrow", key, 0L, r, 1L),
                                o("aten::narrow", gate, 0L, r, 1L), ape, ptrs(ref[b].lat()), ptrs(ref[b].pool()),
                                ptrs(ref[b].ring()), ints(start[b] + j));
                    }
                    int len = start[b] + acc[b];
                    String ctx = " request " + b + " accept " + a0 + "," + a1;
                    assertEquals(0.0, maxAbs(o("aten::narrow", win[b].lat(), 0L, 0L, (long) len), o("aten::narrow", ref[b].lat(), 0L, 0L, (long) len)), "latents" + ctx);
                    long pools = len >> 2;
                    if (pools > 0) {
                        assertEquals(0.0, maxAbs(o("aten::narrow", win[b].pool(), 0L, 0L, pools), o("aten::narrow", ref[b].pool(), 0L, 0L, pools)), "pools" + ctx);
                    }
                    int pending = len & 3;
                    if (pending > 0) {
                        assertEquals(0.0, maxAbs(o("aten::narrow", win[b].ring(), 0L, 0L, (long) pending), o("aten::narrow", ref[b].ring(), 0L, 0L, (long) pending)), "ring" + ctx);
                    }
                }
            }
        }
    }
}
