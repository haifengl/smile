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

import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;

import static smile.llm.model.inkling.Ix.*;

/**
 * The state of one sequence in the reference model: for every layer application a key and a value cache
 * {@code [kvHeads, capacity, headDim]} (bf16) and the last three inputs of its four short convolutions (float32).
 * Applications of a looped layer in different passes have separate state.
 *
 * @author Haifeng Li
 */
final class InklingState implements AutoCloseable {
    /** Convolution slots of an application. */
    static final int CONV_K = 0, CONV_V = 1, CONV_ATTN = 2, CONV_MLP = 3;

    /** Extra tokens a sliding-window cache holds beyond the window, i.e. the largest chunk one forward may process. */
    static final int LOCAL_SLACK = Integer.getInteger("smile.inkling.chunk", 2048);

    final int capacity;
    /** Cache capacity (tokens) of every application. */
    final int[] cacheCapacity;
    /** Absolute position of cache index 0 (sliding-window caches slide forward). */
    final int[] base;
    final Tensor[] k;
    final Tensor[] v;
    final Tensor[][] conv;
    /** Tokens processed so far. */
    int length;

    InklingState(InklingModelArgs args, int capacity, Device device) {
        this.capacity = capacity;
        int apps = args.numApplications();
        cacheCapacity = new int[apps];
        base = new int[apps];
        k = new Tensor[apps];
        v = new Tensor[apps];
        conv = new Tensor[apps][4];
        for (int a = 0; a < apps; a++) {
            int layer = args.layerOfApplication(a);
            int heads = args.isLocal(layer) ? args.swaKvHeads() : args.numKvHeads();
            int dim = args.isLocal(layer) ? args.swaHeadDim() : args.headDim();
            // a sliding-window layer only needs the last window - 1 tokens plus the chunk being processed
            cacheCapacity[a] = args.isLocal(layer) ? Math.min(capacity, args.slidingWindow() + LOCAL_SLACK) : capacity;
            k[a] = keep(zeros(device, ScalarType.BFloat16, heads, cacheCapacity[a], dim));
            v[a] = keep(zeros(device, ScalarType.BFloat16, heads, cacheCapacity[a], dim));
            int rows = args.sconvKernel() - 1;
            conv[a][CONV_K] = keep(zeros(device, ScalarType.Float, rows, heads * dim));
            conv[a][CONV_V] = keep(zeros(device, ScalarType.Float, rows, heads * dim));
            conv[a][CONV_ATTN] = keep(zeros(device, ScalarType.Float, rows, args.dim()));
            conv[a][CONV_MLP] = keep(zeros(device, ScalarType.Float, rows, args.dim()));
        }
    }

    /**
     * The most tokens one forward may process: the window of a sliding cache plus its slack, minus the history it keeps.
     *
     * @param args the model hyperparameters.
     * @return the chunk limit.
     */
    int maxChunk(InklingModelArgs args) {
        return Math.max(1, Math.min(capacity, args.slidingWindow() + LOCAL_SLACK - (args.slidingWindow() - 1)));
    }

    /**
     * A copy of the state after {@code length} tokens: the whole history of the global caches, the last
     * {@code window - 1} tokens of the sliding caches, and the convolution states.
     *
     * @param k       per application, keys {@code [heads, n, dim]} (bf16, owned by the snapshot).
     * @param v       per application, values.
     * @param conv    per application, the four convolution states.
     * @param start   per application, the absolute position of the first cached token.
     * @param length  tokens processed.
     * @param bytes   device memory held.
     */
    record Snapshot(Tensor[] k, Tensor[] v, Tensor[][] conv, int[] start, int length, long bytes) implements AutoCloseable {
        @Override
        public void close() {
            for (int a = 0; a < k.length; a++) {
                k[a].close();
                v[a].close();
                for (Tensor c : conv[a]) {
                    c.close();
                }
            }
        }
    }

    private static long elements(Tensor t) {
        long n = 1;
        for (long d : t.shape()) {
            n *= d;
        }
        return n;
    }

    /** Device bytes a snapshot of {@code tokens} tokens takes. */
    static long snapshotBytes(InklingModelArgs args, int tokens) {
        long bytes = 0;
        for (int a = 0; a < args.numApplications(); a++) {
            int layer = args.layerOfApplication(a);
            boolean local = args.isLocal(layer);
            int heads = local ? args.swaKvHeads() : args.numKvHeads();
            int dim = local ? args.swaHeadDim() : args.headDim();
            long n = local ? Math.min(tokens, args.slidingWindow() - 1) : tokens;
            bytes += 2L * heads * n * dim * 2 + 4L * (args.sconvKernel() - 1) * (2L * heads * dim + 2L * args.dim()) / 2;
        }
        return bytes;
    }

    /**
     * Copies the state out.
     *
     * @param args the model hyperparameters.
     * @return the snapshot (the caller closes it).
     */
    Snapshot snapshot(InklingModelArgs args) {
        int apps = k.length;
        Tensor[] ks = new Tensor[apps];
        Tensor[] vs = new Tensor[apps];
        Tensor[][] cs = new Tensor[apps][4];
        int[] start = new int[apps];
        long bytes = 0;
        for (int a = 0; a < apps; a++) {
            boolean local = args.isLocal(args.layerOfApplication(a));
            int n = local ? Math.min(length, args.slidingWindow() - 1) : length;
            start[a] = length - n;
            int from = start[a] - base[a];
            ks[a] = keep(contiguous(narrow(k[a], 1, from, n)).copy());
            vs[a] = keep(contiguous(narrow(v[a], 1, from, n)).copy());
            for (int c = 0; c < 4; c++) {
                cs[a][c] = keep(conv[a][c].copy());
            }
            bytes += (elements(ks[a]) + elements(vs[a])) * 2;
            for (Tensor c : cs[a]) {
                bytes += elements(c) * 4;
            }
        }
        return new Snapshot(ks, vs, cs, start, length, bytes);
    }

    /**
     * Loads a snapshot into this (empty) state.
     *
     * @param s the snapshot.
     */
    void restore(Snapshot s) {
        if (length != 0 || s.length > capacity) {
            throw new IllegalStateException("cannot restore a snapshot of " + s.length + " tokens into a state at " + length);
        }
        for (int a = 0; a < k.length; a++) {
            long n = s.k[a].size(1);
            // a sliding cache that is smaller than the snapshot keeps the newest tokens
            long skip = Math.max(0, n - cacheCapacity[a]);
            o("aten::copy_", narrow(k[a], 1, 0, n - skip), narrow(s.k[a], 1, skip, n - skip));
            o("aten::copy_", narrow(v[a], 1, 0, n - skip), narrow(s.v[a], 1, skip, n - skip));
            base[a] = s.start[a] + (int) skip;
            for (int c = 0; c < 4; c++) {
                o("aten::copy_", conv[a][c], s.conv[a][c]);
            }
        }
        length = s.length;
    }

    @Override
    public void close() {
        for (int a = 0; a < k.length; a++) {
            k[a].close();
            v[a].close();
            for (Tensor c : conv[a]) {
                c.close();
            }
        }
    }
}
