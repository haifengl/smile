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

import smile.deep.tensor.Tensor;

import static smile.llm.model.inkling.Ix.*;

/**
 * The causal depthwise short convolutions of Inkling-2: {@code (x + conv(x)) / sqrt(taps + 1)} in float32, with the
 * last {@code taps - 1} inputs kept as state. Tap {@code j} of the static kernel weights the input
 * {@code taps - 1 - j} steps back (the last tap is the current token); the dynamic kernel of global-layer keys and
 * values indexes taps by lag (tap 0 is the current token).
 *
 * @author Haifeng Li
 */
final class InklingConv {
    private InklingConv() { }

    /**
     * When set, every conv call appends {@code {state, ctx}} (the state tensor and the {@code [taps - 1 + T, C]} input
     * history it was updated from) so that a speculative verify can roll the state back to any accepted prefix.
     */
    static final ThreadLocal<java.util.List<Tensor[]>> RECORD = new ThreadLocal<>();

    private static void record(Tensor state, Tensor ctx) {
        java.util.List<Tensor[]> r = RECORD.get();
        if (r != null) {
            ctx.detachFromScopes();
            r.add(new Tensor[]{state, ctx});
        }
    }

    /** Frees the recorded histories. */
    static void release(java.util.List<Tensor[]> recorded) {
        for (Tensor[] r : recorded) {
            r[1].close();
        }
        recorded.clear();
    }

    /** Sets every recorded state to the history after {@code accepted} of the recorded rows. */
    static void commit(java.util.List<Tensor[]> recorded, int accepted) {
        for (Tensor[] r : recorded) {
            long keep = r[0].size(0);
            o("aten::copy_", r[0], narrow(r[1], 0, accepted, keep));
        }
    }

    /**
     * Static kernel (one weight per channel and tap).
     *
     * @param x      {@code [T, C]} (any float dtype).
     * @param w      {@code [C, taps]} float32.
     * @param state  {@code [taps - 1, C]} float32; updated to the last inputs.
     * @return bf16 {@code [T, C]}.
     */
    static Tensor apply(Tensor x, Tensor w, Tensor state) {
        long taps = w.size(1);
        long t = x.size(0);
        Tensor xf = f32(x);
        Tensor ctx = cat(0, state, xf);
        Tensor acc = null;
        for (int j = 0; j < taps; j++) {
            Tensor term = mul(narrow(ctx, 0, j, t), o("aten::select.int", w, 1L, (long) j));
            acc = acc == null ? term : add(acc, term);
        }
        record(state, ctx);
        o("aten::copy_", state, narrow(ctx, 0, t, taps - 1));
        return bf16(mul(add(acc, xf), 1.0 / Math.sqrt(taps + 1)));
    }

    /**
     * Dynamic kernel: taps are a static per-channel part plus a per-token, per-head part projected from the layer input.
     *
     * @param x       {@code [T, C]} projected keys or values (bf16).
     * @param input   {@code [T, D]} the layer input the per-token taps are projected from (bf16).
     * @param staticW {@code [taps, C]} float32, tap index = lag.
     * @param proj    {@code [heads * taps, D]} bf16.
     * @param heads   number of key/value heads.
     * @param state   {@code [taps - 1, C]} float32; updated.
     * @return bf16 {@code [T, C]}.
     */
    static Tensor dynamic(Tensor x, Tensor input, Tensor staticW, Tensor proj, long heads, Tensor state) {
        long taps = staticW.size(0);
        long t = x.size(0);
        long c = x.size(1);
        long headDim = c / heads;
        Tensor xf = f32(x);
        Tensor dyn = reshape(f32(linear(input, proj)), t, heads, taps);
        Tensor ctx = cat(0, state, xf);
        Tensor acc = null;
        for (int lag = 0; lag < taps; lag++) {
            Tensor stat = reshape(o("aten::select.int", staticW, 0L, (long) lag), 1, heads, headDim);
            Tensor weight = add(stat, unsqueeze(o("aten::select.int", dyn, 2L, (long) lag), -1));
            Tensor term = mul(reshape(narrow(ctx, 0, taps - 1 - lag, t), t, heads, headDim), weight);
            acc = acc == null ? term : add(acc, term);
        }
        record(state, ctx);
        o("aten::copy_", state, narrow(ctx, 0, t, taps - 1));
        return bf16(mul(add(reshape(acc, t, c), xf), 1.0 / Math.sqrt(2 * taps + 1)));
    }
}
