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

    /** Most rows of one request a verify chunk may carry. */
    static final int VERIFY_ROWS = Integer.getInteger("smile.inkling.verify.rows", 8);

    /** Global-layer keys and values are stored as E4M3 (half the memory; they are L2-clipped / RMS-normalized, so no scale is needed). */
    static final boolean KV_FP8 = Boolean.getBoolean("smile.inkling.kv.fp8") || "1".equals(System.getenv("SMILE_INKLING_KV_FP8"));

    /** Device whose memory holds the caches of the last {@link #SPILL_APPS} global applications (-1: all on the model's device). */
    static final int SPILL_DEVICE = Integer.getInteger("smile.inkling.kv.spill.device", -1);
    static final int SPILL_APPS = Integer.getInteger("smile.inkling.kv.spill.apps", 6);

    final int capacity;
    /** Cache capacity (tokens) of every application. */
    final int[] cacheCapacity;
    /** Per application and request: the pointer table row read by the decode kernels (see {@link #table()}). */
    private long[] table;
    final Tensor[] k;
    final Tensor[] v;
    /** The four convolution states of every application: the first {@code sconvKernel - 1} rows of the full buffers. */
    final Tensor[][] conv;
    /** The same buffers with {@link #VERIFY_ROWS} extra rows that hold a speculative chunk's inputs until the commit. */
    private final Tensor[][] convFull;
    /** Tokens processed so far. */
    int length;

    InklingState(InklingModelArgs args, int capacity, Device device) {
        this.capacity = capacity;
        int apps = args.numApplications();
        cacheCapacity = new int[apps];
        k = new Tensor[apps];
        v = new Tensor[apps];
        conv = new Tensor[apps][4];
        convFull = new Tensor[apps][4];
        int globals = 0;
        for (int a = 0; a < apps; a++) {
            if (!args.isLocal(args.layerOfApplication(a))) {
                globals++;
            }
        }
        int seenGlobals = 0;
        for (int a = 0; a < apps; a++) {
            int layer = args.layerOfApplication(a);
            boolean spill = SPILL_DEVICE >= 0 && !args.isLocal(layer) && seenGlobals++ >= globals - SPILL_APPS;
            Device cacheDevice = spill ? Device.CUDA((byte) SPILL_DEVICE) : device;
            int heads = args.isLocal(layer) ? args.swaKvHeads() : args.numKvHeads();
            int dim = args.isLocal(layer) ? args.swaHeadDim() : args.headDim();
            // a sliding-window layer only needs the last window - 1 tokens plus the chunk being processed
            cacheCapacity[a] = args.isLocal(layer) ? Math.min(capacity, args.slidingWindow() + LOCAL_SLACK) : capacity;
            ScalarType kvType = KV_FP8 && !args.isLocal(layer) ? ScalarType.Float8e4m3fn : ScalarType.BFloat16;
            k[a] = keep(zeros(cacheDevice, kvType, heads, cacheCapacity[a], dim));
            v[a] = keep(zeros(cacheDevice, kvType, heads, cacheCapacity[a], dim));
            int rows = args.sconvKernel() - 1;
            int[] channels = {heads * dim, heads * dim, args.dim(), args.dim()};
            for (int c = 0; c < 4; c++) {
                convFull[a][c] = keep(zeros(device, ScalarType.Float, rows + VERIFY_ROWS, channels[c]));
                conv[a][c] = keep(narrow(convFull[a][c], 0, 0, rows));
            }
        }
    }

    /**
     * The raw addresses the decode kernels need, 8 per application: key cache, value cache, the four convolution
     * states, the cache capacity (slots) and a pad. Constant for the life of the state.
     *
     * @return {@code apps * 8} values.
     */
    long[] table() {
        if (table == null) {
            long[] t = new long[k.length * 8];
            for (int a = 0; a < k.length; a++) {
                t[a * 8] = smile.torch.InklingNative.dataPtr(k[a]);
                t[a * 8 + 1] = smile.torch.InklingNative.dataPtr(v[a]);
                for (int c = 0; c < 4; c++) {
                    t[a * 8 + 2 + c] = smile.torch.InklingNative.dataPtr(conv[a][c]);
                }
                t[a * 8 + 6] = cacheCapacity[a];
                t[a * 8 + 7] = k[a].dtype() == ScalarType.Float8e4m3fn ? 1 : 0;
            }
            table = t;
        }
        return table;
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
            bytes += 2L * heads * n * dim * (KV_FP8 && !local ? 1 : 2) + 4L * (args.sconvKernel() - 1) * (2L * heads * dim + 2L * args.dim()) / 2;
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
            ks[a] = keep(read(k[a], cacheCapacity[a], start[a], n));
            vs[a] = keep(read(v[a], cacheCapacity[a], start[a], n));
            for (int c = 0; c < 4; c++) {
                cs[a][c] = keep(conv[a][c].copy());
            }
            bytes += (elements(ks[a]) + elements(vs[a])) * (k[a].dtype() == ScalarType.Float8e4m3fn ? 1 : 2);
            for (Tensor c : cs[a]) {
                bytes += elements(c) * 4;
            }
        }
        return new Snapshot(ks, vs, cs, start, length, bytes);
    }

    /** Copies the cache rows of positions {@code [from, from + n)} out ({@code [heads, n, dim]}); a ring when it wraps. */
    private Tensor read(Tensor cache, int cap, int from, int n) {
        if (from + n <= cap) {
            return contiguous(narrow(cache, 1, from, n)).copy();
        }
        long[] idx = new long[n];
        for (int i = 0; i < n; i++) {
            idx[i] = (from + i) % cap;
        }
        return o("aten::index_select", cache, 1L, Tensor.of(idx, n).to(cache.device()));
    }

    /** Writes rows for positions {@code [from, from + n)} into a cache. */
    private void write(Tensor cache, int cap, int from, Tensor rows) {
        long n = rows.size(1);
        if (from + n <= cap) {
            o("aten::copy_", narrow(cache, 1, from, n), rows);
            return;
        }
        long[] idx = new long[(int) n];
        for (int i = 0; i < n; i++) {
            idx[i] = (from + i) % cap;
        }
        o("aten::index_copy_", cache, 1L, Tensor.of(idx, n).to(cache.device()), rows);
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
            write(k[a], cacheCapacity[a], s.start[a], s.k[a]);
            write(v[a], cacheCapacity[a], s.start[a], s.v[a]);
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
            for (int c = 0; c < 4; c++) {
                conv[a][c].close();
                convFull[a][c].close();
            }
        }
    }
}
