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

import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Aten;
import smile.torch.Native;

import static smile.llm.model.deepseek.Dsv.assign;
import static smile.llm.model.deepseek.Dsv.cat;
import static smile.llm.model.deepseek.Dsv.narrow;
import static smile.llm.model.deepseek.Dsv.o;
import static smile.llm.model.deepseek.Dsv.ropeTail;

/**
 * DeepSeek-V4.1 attention: one shared key/value head of width 512, low-rank queries, an attention sink, a sliding
 * window of raw KV plus (layers with a compression ratio) compressed KV chosen by an indexer, and a grouped
 * low-rank output projection.
 *
 * <p>Layers do not all own their compressed KV: only the KV source layers run the {@link Compressor}, only the index
 * source layers run the {@link Indexer}, and the others read what the last source published in {@link Shared}.
 * Single sequence (batch 1); the caches live in this object, positions are absolute.
 *
 * @author Haifeng Li
 */
final class DeepSeekAttention {
    /** What source layers publish for the layers after them. */
    static final class Shared {
        Tensor compressKv;
        Tensor indexK;
        Tensor topkIdxs;
        Tensor candidates;

        void reset() {
            compressKv = null;
            indexK = null;
            topkIdxs = null;
            candidates = null;
        }
    }

    /**
     * What the layers of one batched decode pass publish for the layers after them, for all sequences at once (the
     * counterpart of {@link Shared}, which holds one sequence's).
     */
    static final class BatchShared {
        /** Data pointers of each sequence's compressed KV cache, from the last KV source layer. */
        Tensor compressPtrs;
        /** Data pointers of each sequence's indexer key cache, from the last layer that owns one. */
        Tensor indexKPtrs;
        /** Chosen compressed positions {@code [1, n, k]} (int32, already offset past the window). */
        Tensor topkIdxs;
        Tensor candidates;
    }

    /** Device tables of the data pointers of one layer's per-sequence caches, for one batch composition. */
    private static final class PtrTables {
        Tensor window;
        Tensor compress;
        Tensor kvState;
        Tensor score;
        Tensor indexK;
    }

    /**
     * The cache pointers of every layer and sequence of a captured decode graph in one static device block laid out
     * {@code [layer][kind][row]}, so the graph reads them from fixed addresses and a change of batch composition is a
     * single upload.
     */
    static final class GraphPtrs {
        static final int WINDOW = 0;
        static final int COMPRESS = 1;
        static final int KV_STATE = 2;
        static final int SCORE = 3;
        static final int INDEX_K = 4;
        static final int KINDS = 5;

        private final Tensor block;
        private final int nb;
        /** A verify step: the rows of a sequence follow each other, {@code jIdx} gives each row's index in its block. */
        boolean verify;
        Tensor jIdx;
        /** Per compressor layer: every row's projection (kv and score, float32 {@code [rows, headDim]}) for the state write after acceptance. */
        Tensor[] rowKv;
        Tensor[] rowScore;

        GraphPtrs(Tensor block, int nb) {
            this.block = block;
            this.nb = nb;
        }

        Tensor block() {
            return block;
        }

        /** {@code int64 [nb]}: the {@code kind} pointers of {@code layer}'s sequences. */
        Tensor view(int layer, int kind) {
            return narrow(block, 0, ((long) layer * KINDS + kind) * nb, nb);
        }
    }

    /** Identity of an ordered batch of sequence states. */
    private static final class BatchKey {
        private final State[] states;
        private final int hash;

        BatchKey(State[] states) {
            this.states = states.clone();
            int h = 1;
            for (State s : states) {
                h = h * 31 + System.identityHashCode(s);
            }
            this.hash = h;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof BatchKey k) || k.states.length != states.length) {
                return false;
            }
            for (int i = 0; i < states.length; i++) {
                if (k.states[i] != states[i]) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private final java.util.LinkedHashMap<BatchKey, PtrTables> tables = new java.util.LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<BatchKey, PtrTables> e) {
            if (size() > 16) {
                PtrTables t = e.getValue();
                for (Tensor x : new Tensor[]{t.window, t.compress, t.kvState, t.score, t.indexK}) {
                    if (x != null) {
                        x.close();
                    }
                }
                return true;
            }
            return false;
        }
    };

    private PtrTables tables(State[] states) {
        BatchKey key = new BatchKey(states);
        PtrTables t = tables.get(key);
        if (t == null) {
            t = new PtrTables();
            int n = states.length;
            Tensor[] w = new Tensor[n];
            for (int i = 0; i < n; i++) {
                w[i] = states[i].window();
            }
            t.window = Native.dsPtrTable(w);
            if (states[0].compress() != null) {
                Tensor[] c = new Tensor[n];
                for (int i = 0; i < n; i++) {
                    c[i] = states[i].compress();
                }
                t.compress = Native.dsPtrTable(c);
            }
            if (states[0].kvState() != null) {
                Tensor[] a = new Tensor[n];
                Tensor[] b = new Tensor[n];
                for (int i = 0; i < n; i++) {
                    a[i] = states[i].kvState();
                    b[i] = states[i].score();
                }
                t.kvState = Native.dsPtrTable(a);
                t.score = Native.dsPtrTable(b);
            }
            if (states[0].indexK() != null) {
                Tensor[] k = new Tensor[n];
                for (int i = 0; i < n; i++) {
                    k[i] = states[i].indexK();
                }
                t.indexK = Native.dsPtrTable(k);
            }
            for (Tensor x : new Tensor[]{t.window, t.compress, t.kvState, t.score, t.indexK}) {
                if (x != null) {
                    x.detachFromScopes();
                }
            }
            tables.put(key, t);
        }
        return t;
    }

    private final DeepSeekModelArgs args;
    private final int layer;
    private Shared shared;
    private final int heads;
    private final int headDim;
    private final int rd;
    private final int window;
    /** Slots of the window ring: the window plus room for the rows of one verify step, so rows that get rejected never overwrite a key a later query still needs. */
    private final int ring;
    private final int ratio;
    private final int groups;
    private final int oRank;
    private final double eps;
    private final double scale;
    private final Tensor sink;
    private final DeepSeekLinear wqA;
    private final DeepSeekLinear wqB;
    private final DeepSeekLinear wkv;
    /** DSpark stages: {@code wkv} on its own, because the fused projection consumes {@link #wkv} (it also reads the main hidden state). */
    private final DeepSeekLinear wkvMain;
    private final DeepSeekLinear woB;
    /** wq_a and wkv fused (they read the same input), or {@code null}. */
    private final DeepSeekLinear wqaKv;
    private final int qRank;
    private final Tensor qNorm;
    private final Tensor kvNorm;
    private final Tensor woA;
    private final Tensor freqs;
    private Tensor windowCache;
    private final Compressor compressor;
    private final Indexer indexer;
    private Tensor compressCache;

    /** Extra ring slots beyond the window (at least the rows of a verify step minus one). */
    static final int RING_EXTRA = 8;

    DeepSeekAttention(DeepSeekModelArgs args, DeepSeekCheckpoint ck, int layer) {
        String p = args.layerPrefix(layer) + ".attn";
        this.args = args;
        this.layer = layer;
        this.heads = args.numHeads();
        this.headDim = args.headDim();
        this.rd = args.ropeHeadDim();
        this.window = args.windowSize();
        this.ring = window + RING_EXTRA;
        this.ratio = args.compressRatio(layer);
        this.groups = args.oGroups();
        this.oRank = args.oLoraRank();
        this.eps = args.normEps();
        this.scale = Math.pow(headDim, -0.5);
        this.sink = DeepSeekOps.f32(ck.dev(p + ".attn_sink"));
        this.wqA = new DeepSeekLinear(ck, p + ".wq_a", null);
        this.wqB = new DeepSeekLinear(ck, p + ".wq_b", null);
        this.wkv = new DeepSeekLinear(ck, p + ".wkv", null);
        this.wkvMain = layer >= args.numLayers() ? new DeepSeekLinear(ck, p + ".wkv", null) : null;
        this.woB = new DeepSeekLinear(ck, p + ".wo_b", null);
        this.qRank = args.qLoraRank();
        this.wqaKv = wqA.packed() && wkv.packed() ? DeepSeekLinear.fuse(wqA, wkv) : null;
        this.qNorm = ck.dev(p + ".q_norm.weight");
        this.kvNorm = ck.dev(p + ".kv_norm.weight");
        // wo_a is an einsum, not a Linear: the reference converter dequantizes it to bf16
        this.woA = Dsv.scoped(() -> DeepSeekOps.dequantFp8(ck.dev(p + ".wo_a.weight"), ck.dev(p + ".wo_a.scale")).to(ScalarType.BFloat16));
        int original = ratio > 0 ? args.originalSeqLen() : 0;
        double theta = ratio > 0 ? args.compressRopeTheta() : args.ropeTheta();
        this.freqs = DeepSeekOps.precomputeFreqsCis(rd, args.maxSeqLen(), original, theta, args.ropeFactor(),
                args.betaFast(), args.betaSlow());
        this.compressor = args.isKvSource(layer) ? new Compressor(args, ck, layer) : null;
        this.indexer = args.isIndexSource(layer) ? new Indexer(args, ck, layer, freqs) : null;
        use(newState(new Shared()));
    }

    /**
     * The mutable state of one sequence in this layer: window ring, compressed KV, compressor group state and indexer
     * keys. Entries that the layer does not have are {@code null}.
     *
     * @param window   window ring {@code [1, window, headDim]}.
     * @param compress compressed KV (KV source layers).
     * @param kvState  compressor partial group (ratio above one).
     * @param score    compressor partial group scores.
     * @param indexK   indexer keys (index layers that own keys).
     * @param shared   what source layers publish to later layers during one pass (one per sequence, because rows of a
     *                 batched pass are processed one after the other inside each layer).
     */
    record State(Tensor window, Tensor compress, Tensor kvState, Tensor score, Tensor indexK, Shared shared) {
        /** Keeps the caches out of the enclosing tensor scopes (they outlive a forward pass). */
        void detach() {
            for (Tensor x : new Tensor[]{window, compress, kvState, score, indexK}) {
                if (x != null) {
                    x.detachFromScopes();
                }
            }
        }

        /** Returns the caches to the state of an empty sequence (cheaper than allocating new ones). */
        void reset() {
            for (Tensor t : new Tensor[]{window, compress, kvState, indexK}) {
                if (t != null) {
                    Aten.call("aten::zero_", t);
                }
            }
            if (score != null) {
                Aten.call("aten::fill_.Scalar", score, Double.NEGATIVE_INFINITY);
            }
            shared.reset();
        }

        void close() {
            for (Tensor t : new Tensor[]{window, compress, kvState, score, indexK}) {
                if (t != null) {
                    t.close();
                }
            }
        }
    }

    /** Allocates the state of a fresh (empty) sequence; the caller owns it. */
    State newState(Shared shared) {
        Tensor[] c = compressor == null ? new Tensor[2] : compressor.newState();
        return new State(Dsv.zeros(ScalarType.BFloat16, 1, ring, headDim),
                args.isKvSource(layer) ? Dsv.zeros(ScalarType.BFloat16, 1, args.maxSeqLen() / ratio, headDim) : null,
                c[0], c[1], indexer == null ? null : indexer.newState(), shared);
    }

    /** Makes {@code s} the sequence this layer reads and writes. */
    void use(State s) {
        shared = s.shared();
        windowCache = s.window();
        compressCache = s.compress();
        if (compressor != null) {
            compressor.use(s.kvState(), s.score());
        }
        if (indexer != null) {
            indexer.use(s.indexK());
        }
    }

    /**
     * Attention over the new tokens.
     *
     * @param x        normalized hidden states {@code [1, s, dim]} (bf16).
     * @param startPos absolute position of the first new token.
     * @param trace    optional map receiving intermediates, or {@code null}.
     * @return the output {@code [1, s, dim]}.
     */
    Tensor forward(Tensor x, int startPos) {
        long seqLen = x.size(1);
        Dsv.probeNext();
        Dsv.probe("x", x);
        Tensor f = narrow(freqs, 0, startPos, seqLen);
        long t = System.nanoTime();
        Tensor[] proj = projectQkv(x);
        Tensor qr = DeepSeekOps.rmsNorm(proj[0], qNorm, eps);
        Dsv.probe("qr", qr);
        Tensor q = ropeTail(wqB.forward(qr).reshape(1, seqLen, heads, headDim), f, rd, false);
        Dsv.probe("q", q);
        t = Dsv.tick("attn.qproj", t);

        Tensor[] w = windowKv(proj[1], x.size(1), f, startPos);
        t = Dsv.tick("attn.window", t);
        Tensor kv = w[0];
        Tensor topk = w[1];
        if (ratio > 0) {
            Tensor[] c = compressPart(x, qr, startPos, kv.size(1));
            kv = cat(1, kv, c[0]);
            topk = cat(-1, topk, c[1]);
        }
        Dsv.probe("kv", kv);
        t = Dsv.tick("attn.cmp", t);
        Tensor out = DeepSeekOps.sparseAttention(q, kv, sink, topk, scale);
        Dsv.probe("sattn", out);
        t = Dsv.tick("attn.sparse", t);
        Tensor res = output(out, f, seqLen);
        Dsv.tick("attn.output", t);
        return res;
    }

    /**
     * Attention over the prompts of several new sequences at once (every sequence starts at position 0). What does not
     * depend on the sequence (projections, norms, rope, the output projection) runs once over the concatenated tokens; the
     * window ring, compressor, indexer and the attention itself run per sequence.
     *
     * @param x      normalized hidden states {@code [1, total, dim]}, the prompts one after the other.
     * @param lens   each prompt's length.
     * @param states the sequences' states of this layer.
     * @return the output {@code [1, total, dim]}.
     */
    Tensor forwardPrefillBatch(Tensor x, int[] lens, State[] states) {
        long total = x.size(1);
        long[] posAll = new long[(int) total];
        int at = 0;
        for (int len : lens) {
            for (int j = 0; j < len; j++) {
                posAll[at++] = j;
            }
        }
        Tensor f = o("aten::index_select", freqs, 0L, Dsv.dev(Tensor.of(posAll, total)));
        Tensor[] proj = projectQkv(x);
        Tensor qr = DeepSeekOps.rmsNorm(proj[0], qNorm, eps);
        Tensor q = ropeTail(wqB.forward(qr).reshape(1, total, heads, headDim), f, rd, false);
        Tensor kvAll = DeepSeekOps.rmsNorm(proj[1], kvNorm, eps);
        kvAll = DeepSeekOps.fakeQuantFp8(ropeTail(kvAll, f, rd, false), 32);
        Tensor[] outs = new Tensor[lens.length];
        int off = 0;
        for (int i = 0; i < lens.length; i++) {
            int len = lens[i];
            use(states[i]);
            Tensor kv = narrow(kvAll, 1, off, len);
            writeWindow(kv, len);
            Tensor topk = DeepSeekOps.windowTopkIdxs(window, ring, len, 0);
            if (ratio > 0) {
                Tensor[] c = compressPart(narrow(x, 1, off, len), narrow(qr, 1, off, len), 0, kv.size(1));
                kv = cat(1, kv, c[0]);
                topk = cat(-1, topk, c[1]);
            }
            outs[i] = DeepSeekOps.sparseAttention(narrow(q, 1, off, len), kv, sink, topk, scale);
            off += len;
        }
        return output(lens.length == 1 ? outs[0] : cat(1, outs), f, total);
    }

    /** The window-ring key of a main-model hidden row: {@code kv_norm(wkv(x))} with rope at its position, fp8 fake-quantized. */
    private Tensor mainKv(Tensor mainX, long[] positions) {
        return mainKv(mainX, Dsv.dev(Tensor.of(positions, positions.length)));
    }

    private Tensor mainKv(Tensor mainX, Tensor positions) {
        Tensor f = o("aten::index_select", freqs, 0L, positions);
        Tensor kv = DeepSeekOps.rmsNorm(wkvMain.forward(mainX), kvNorm, eps);
        return DeepSeekOps.fakeQuantFp8(ropeTail(kv, f, rd, false), 32);
    }

    /**
     * DSpark: seeds this stage's window ring with the keys of the main model's hidden states of a whole prompt
     * (positions {@code 0 ..} {@code len - 1}), like the reference's {@code start_pos == 0} pass.
     *
     * @param mainX {@code [1, len, dim]} the projected and normalized main hidden states.
     */
    Tensor draftMainKv(Tensor mainX, long[] positions) {
        return mainKv(mainX, positions);
    }

    void seedDraftWindow(State s, Tensor mainX) {
        use(s);
        long len = mainX.size(1);
        long[] pos = new long[(int) len];
        for (int i = 0; i < len; i++) {
            pos[i] = i;
        }
        writeRing(mainKv(mainX, pos), len, window);
    }

    /** Whether this layer keeps a compressor partial group (and so must be rolled back after a rejected verify tail). */
    boolean hasCompressorState() {
        return compressor != null && ratio > 1;
    }

    void commitCompressor(Tensor kvPtrs, Tensor scorePtrs, Tensor rowKv, Tensor rowScore, Tensor base, Tensor p0, Tensor last) {
        compressor.commit(kvPtrs, scorePtrs, rowKv, rowScore, base, p0, last);
    }

    /** DSpark: writes the window-ring key of the main hidden state {@code mainX [1, 1, dim]} of position {@code pos}. */
    void absorbDraftWindow(State s, Tensor mainX, int pos) {
        use(s);
        assign(narrow(windowCache, 1, pos % window, 1), mainKv(mainX, new long[]{pos}));
    }

    /**
     * DSpark attention of one stage for several sequences, each a block of {@code block} draft tokens at positions
     * {@code startPos + 1 ..} (the reference {@code DSparkAttention}): the main model's hidden state at {@code startPos}
     * is written into the window ring, then every block row attends to the ring and to all rows of its own block.
     *
     * @param x        normalized block rows {@code [1, n * block, dim]}.
     * @param mainX    projected main hidden states of the last processed position of each sequence {@code [1, n, dim]}.
     * @param startPos position of that last processed token, per sequence ({@code >= 0}).
     */
    /**
     * DSpark attention of one stage for {@code n} sequences, every input a device tensor so the pass can be captured in a
     * CUDA graph: the main hidden state of each sequence's last position goes into its ring (modulus: the window itself),
     * the block's own keys into the ring's spare slots, and one pass of the pointer-table attention kernel serves all rows.
     *
     * @param x        normalized block rows {@code [1, n * block, dim]}.
     * @param mainX    projected main hidden states of the last processed positions {@code [1, n, dim]}.
     * @param startPos int64 {@code [n]}: those positions.
     * @param qpos     int64 {@code [n * block]}: the positions of the block rows.
     * @param idx      int32 {@code [1, n * block, window + block]}: ring slots (-1 for empty) then the block's own slots.
     * @param slots    int64 {@code [n * block]}: the spare slot of each block row.
     * @param perSeq   int64 {@code [n]}: each sequence's ring pointer.
     * @param rowPtrs  int64 {@code [n * block]}: the ring pointer of each row's sequence.
     */
    Tensor forwardDraft(Tensor x, Tensor mainX, Tensor startPos, Tensor qpos, Tensor idx, Tensor slots, Tensor perSeq,
                        Tensor rowPtrs, int n, int block) {
        long total = (long) n * block;
        Tensor f = o("aten::index_select", freqs, 0L, qpos);
        Native.dsWindowWrite(mainKv(mainX, startPos).contiguous(), perSeq, startPos, window);
        Tensor[] proj = projectQkv(x);
        Tensor qr = DeepSeekOps.rmsNorm(proj[0], qNorm, eps);
        Tensor q = ropeTail(wqB.forward(qr).reshape(1, total, heads, headDim), f, rd, false);
        Tensor kv = DeepSeekOps.fakeQuantFp8(ropeTail(DeepSeekOps.rmsNorm(proj[1], kvNorm, eps), f, rd, false), 32);
        Native.dsCacheScatter(kv.reshape(total, -1).contiguous(), rowPtrs, slots, 1, null);
        Tensor out = Native.dsSparseAttentionPtrs(q, rowPtrs, null, ring, sink, idx, scale);
        return output(out, f, total);
    }

    /** The raw (before norm) low-rank query and key/value of the new tokens: one fused kernel when the weights are packed. */
    private Tensor[] projectQkv(Tensor x) {
        if (wqaKv == null) {
            return new Tensor[]{wqA.forward(x), wkv.forward(x)};
        }
        Tensor y = wqaKv.forward(x);
        int last = y.dim() - 1;
        return new Tensor[]{o("aten::contiguous", narrow(y, last, 0, qRank)),
                o("aten::contiguous", narrow(y, last, qRank, headDim))};
    }

    /** Inverse rope, fp8 fake quantization, grouped low-rank output projection. */
    private Tensor output(Tensor out, Tensor f, long seqLen) {
        out = ropeTail(out, f, rd, true);
        Dsv.probe("rope_out", out);
        out = DeepSeekOps.fakeQuantFp8(out.reshape(1, seqLen, groups, -1), 32);
        Tensor wo = woA.reshape(groups, oRank, -1);
        // fp8 fake-quantized activations and the bf16 weight multiply exactly in bf16 (float32 accumulation), and the
        // result is rounded to bf16 anyway: no float32 copy of the large weight per step
        Tensor mid = out.dtype() == ScalarType.BFloat16
                ? o("aten::einsum", "bsgd,grd->bsgr", new Tensor[]{out, wo})
                : o("aten::einsum", "bsgd,grd->bsgr", new Tensor[]{DeepSeekOps.f32(out), DeepSeekOps.f32(wo)})
                .to(ScalarType.BFloat16);
        Dsv.probe("mid", mid);
        return woB.forward(mid.reshape(1, seqLen, groups * oRank));
    }

    /**
     * One decode token for each of several sequences at different positions. Everything that does not depend on the
     * sequence (projections, norms, rope, the output projection) runs once over the rows, which are laid out along the
     * sequence axis; the window ring, the compressor, the indexer and the attention itself run per row.
     *
     * @param x      normalized hidden states {@code [1, n, dim]}, one row per sequence.
     * @param pos    absolute position of each row's token ({@code >= 1}: a row at 0 is a prefill).
     * @param states the sequences' states of this layer.
     * @return the output {@code [1, n, dim]}.
     */
    Tensor forwardBatch(Tensor x, int[] pos, State[] states, BatchShared batch) {
        if (canFuse(x, pos, states)) {
            return forwardBatchFused(x, pos, states, batch);
        }
        return forwardBatchRows(x, pos, states);
    }

    /** Whether the whole batch can run through the pointer-table kernels (otherwise sequence after sequence). */
    private boolean canFuse(Tensor x, int[] pos, State[] states) {
        if (!Native.dsBatchAvailable() || !x.device().isCUDA() || x.dtype() != ScalarType.BFloat16 || headDim != 512
                || pos.length > 4096) {
            return false;
        }
        for (int p : pos) {
            if (p < 1 || (ratio > 0 && (p + 1) / ratio < 1)) {
                return false;
            }
        }
        if (states[0].window().dtype() != ScalarType.BFloat16) {
            return false;
        }
        return true;
    }

    /** Sequence after sequence: the fallback of {@link #forwardBatch(Tensor, int[], State[], BatchShared)}. */
    private Tensor forwardBatchRows(Tensor x, int[] pos, State[] states) {
        int n = pos.length;
        long[] p = new long[n];
        for (int i = 0; i < n; i++) {
            if (pos[i] < 1) {
                throw new IllegalArgumentException("batched decode needs positions >= 1");
            }
            p[i] = pos[i];
        }
        long t = System.nanoTime();
        Tensor f = o("aten::index_select", freqs, 0L,
                Dsv.cached("pos:" + java.util.Arrays.toString(p), () -> Dsv.dev(Tensor.of(p, n))));
        Tensor[] proj = projectQkv(x);
        Tensor qr = DeepSeekOps.rmsNorm(proj[0], qNorm, eps);
        Tensor q = ropeTail(wqB.forward(qr).reshape(1, n, heads, headDim), f, rd, false);
        Tensor kvAll = DeepSeekOps.rmsNorm(proj[1], kvNorm, eps);
        kvAll = DeepSeekOps.fakeQuantFp8(ropeTail(kvAll, f, rd, false), 32);
        t = Dsv.tick("attn.project", t);
        Tensor[] outs = new Tensor[n];
        for (int i = 0; i < n; i++) {
            use(states[i]);
            assign(narrow(windowCache, 1, pos[i] % ring, 1), narrow(kvAll, 1, i, 1));
            Tensor kv = windowCache;
            Tensor topk = DeepSeekOps.windowTopkIdxs(window, ring, 1, pos[i]);
            t = Dsv.tick("attn.window", t);
            if (ratio > 0) {
                Tensor[] c = compressPart(narrow(x, 1, i, 1), narrow(qr, 1, i, 1), pos[i], kv.size(1));
                kv = cat(1, kv, c[0]);
                topk = cat(-1, topk, c[1]);
                t = Dsv.tick("attn.compress+indexer", t);
            }
            outs[i] = DeepSeekOps.sparseAttention(narrow(q, 1, i, 1), kv, sink, topk, scale);
            t = Dsv.tick("attn.sparse", t);
        }
        Tensor res = output(n == 1 ? outs[0] : cat(1, outs), f, n);
        Dsv.tick("attn.output", t);
        return res;
    }

    /**
     * {@link #forwardBatchFused} for a CUDA graph: nothing is built on the host. The positions are the static device
     * tensor {@code posT}, the caches are reached through the static pointer block, and every length-dependent size
     * is the capacity {@code lenCap} (positions past a row's own length are masked with {@code -inf} and dropped).
     */
    Tensor forwardBatchGraph(Tensor x, Tensor posT, int nb, int lenCap, GraphPtrs gp, BatchShared bs) {
        Tensor f = o("aten::index_select", freqs, 0L, posT);
        Tensor[] proj = projectQkv(x);
        Tensor qr = DeepSeekOps.rmsNorm(proj[0], qNorm, eps);
        Tensor q = ropeTail(wqB.forward(qr).reshape(1, nb, heads, headDim), f, rd, false);
        Tensor kvAll = DeepSeekOps.rmsNorm(proj[1], kvNorm, eps);
        kvAll = DeepSeekOps.fakeQuantFp8(ropeTail(kvAll, f, rd, false), 32);
        Dsv.stamp("attn.project");
        Tensor windowPtrs = gp.view(layer, GraphPtrs.WINDOW);
        Native.dsWindowWrite(kvAll.contiguous(), windowPtrs, posT, ring);
        Tensor topk = Native.dsWindowTopk(posT, ring, window);
        Dsv.stamp("attn.window");
        Tensor compressPtrs = null;
        if (ratio > 0) {
            final int r = ratio;
            long tcap = Math.max(1, lenCap / r);
            Tensor lens = o("aten::floor_divide.Scalar", o("aten::add.Scalar", posT, 1L), (long) r).reshape(nb, 1);
            Tensor gf = o("aten::index_select", freqs, 0L, o("aten::add.Scalar", posT, (long) (1 - r)));
            Tensor latent = null;
            if (compressor != null) {
                latent = !gp.verify
                        ? compressor.forwardBatch(x, posT, gp.view(layer, GraphPtrs.KV_STATE), gp.view(layer, GraphPtrs.SCORE))
                        : compressor.forwardVerify(x, posT, gp.view(layer, GraphPtrs.KV_STATE),
                                gp.view(layer, GraphPtrs.SCORE), gp.jIdx, gp.rowKv[layer], gp.rowScore[layer]);
                bs.compressPtrs = Dsv.keep(gp.view(layer, GraphPtrs.COMPRESS));
            }
            Dsv.stamp("attn.compressor");
            if (indexer != null) {
                bs.topkIdxs = Dsv.keep(indexer.forwardBatchGraph(x, qr, latent, f, gf, posT, gp, bs, tcap, lens, ring));
            }
            Dsv.stamp("attn.indexer");
            if (latent != null) {
                Tensor rotated = ropeTail(latent, gf, rd, false);
                rotated = DeepSeekOps.fakeQuantFp4(rotated, 16, true);
                Native.dsCacheScatterGated(rotated.contiguous(), gp.view(layer, GraphPtrs.COMPRESS), posT, r);
            }
            compressPtrs = bs.compressPtrs;
            topk = cat(-1, topk, bs.topkIdxs);
            Dsv.stamp("attn.cache");
        }
        Tensor out = nb >= DeepSeekOps.GEMM_MIN_SEQS
                ? DeepSeekOps.sparseAttentionPtrsGemm(q, windowPtrs, compressPtrs, ring, sink, topk, scale) : null;
        if (out == null) {
            out = Native.dsSparseAttentionPtrs(q, windowPtrs, compressPtrs, ring, sink, topk, scale);
        }
        Dsv.stamp("attn.sparse");
        return output(out, f, nb);
    }

    /**
     * One decode token for each of several sequences with the per-sequence work (window ring, compressor, indexer,
     * sparse attention) done by kernels that address each sequence's caches through pointer tables: the same
     * computation as {@link #forwardBatchRows}, in a handful of launches independent of the number of sequences.
     */
    private Tensor forwardBatchFused(Tensor x, int[] pos, State[] states, BatchShared bs) {
        final int n = pos.length;
        final long[] p = new long[n];
        for (int i = 0; i < n; i++) {
            p[i] = pos[i];
        }
        final String pk = java.util.Arrays.toString(p);
        Tensor posT = Dsv.cached("pos:" + pk, () -> Dsv.dev(Tensor.of(p, n)));
        Tensor f = o("aten::index_select", freqs, 0L, posT);
        Tensor[] proj = projectQkv(x);
        Tensor qr = DeepSeekOps.rmsNorm(proj[0], qNorm, eps);
        Tensor q = ropeTail(wqB.forward(qr).reshape(1, n, heads, headDim), f, rd, false);
        Tensor kvAll = DeepSeekOps.rmsNorm(proj[1], kvNorm, eps);
        kvAll = DeepSeekOps.fakeQuantFp8(ropeTail(kvAll, f, rd, false), 32);
        PtrTables t = tables(states);
        Native.dsWindowWrite(kvAll.contiguous(), t.window, posT, ring);
        Tensor topk = Dsv.cached("wtopk:" + window + ":" + ring + ":" + pk, () -> {
            int[] data = new int[n * window];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < window; j++) {
                    int qp = pos[i] - (window - 1) + j;
                    data[i * window + j] = qp < 0 ? -1 : qp % ring;
                }
            }
            return Dsv.dev(Tensor.of(data, 1, n, window));
        });
        Tensor compressPtrs = null;
        if (ratio > 0) {
            final int r = ratio;
            long[] tl = new long[n];
            long tmax = 0;
            for (int i = 0; i < n; i++) {
                tl[i] = (pos[i] + 1) / r;
                tmax = Math.max(tmax, tl[i]);
            }
            final long[] lensArr = tl;
            Tensor lens = Dsv.cached("lens2:" + r + ":" + pk, () -> Dsv.dev(Tensor.of(lensArr, n, 1)));
            Tensor emit = null;
            if (r > 1) {
                emit = Dsv.cached("emit:" + r + ":" + pk, () -> {
                    int[] e = new int[n];
                    for (int i = 0; i < n; i++) {
                        e[i] = (pos[i] + 1) % r == 0 ? 1 : 0;
                    }
                    return Dsv.dev(Tensor.of(e, n)).to(ScalarType.UInt8);
                });
            }
            Tensor gf = o("aten::index_select", freqs, 0L, Dsv.cached("gfi:" + r + ":" + pk, () -> {
                long[] g = new long[n];
                for (int i = 0; i < n; i++) {
                    g[i] = pos[i] + 1 - r;
                }
                return Dsv.dev(Tensor.of(g, n));
            }));
            Tensor latent = null;
            if (compressor != null) {
                latent = compressor.forwardBatch(x, posT, t.kvState, t.score);
                bs.compressPtrs = t.compress;
            }
            if (indexer != null) {
                bs.topkIdxs = indexer.forwardBatch(x, qr, latent, f, gf, posT, emit, t, bs, tmax, lens, ring);
            }
            if (latent != null) {
                Tensor rotated = ropeTail(latent, gf, rd, false);
                rotated = DeepSeekOps.fakeQuantFp4(rotated, 16, true);
                Native.dsCacheScatter(rotated.contiguous(), t.compress, posT, r, emit);
            }
            compressPtrs = bs.compressPtrs;
            topk = cat(-1, topk, bs.topkIdxs);
        }
        Tensor out = n >= DeepSeekOps.GEMM_MIN_SEQS
                ? DeepSeekOps.sparseAttentionPtrsGemm(q, t.window, compressPtrs, ring, sink, topk, scale) : null;
        if (out == null) {
            out = Native.dsSparseAttentionPtrs(q, t.window, compressPtrs, ring, sink, topk, scale);
        }
        return output(out, f, n);
    }

    /** Stores the last keys of a prompt from position 0 in the window ring (position {@code p} in slot {@code p % ring}). */
    private void writeWindow(Tensor kv, long seqLen) {
        writeRing(kv, seqLen, ring);
    }

    /** {@link #writeWindow} for a ring of {@code size} slots (the DSpark stages use the window itself). */
    private void writeRing(Tensor kv, long seqLen, int size) {
        if (seqLen <= size) {
            assign(narrow(windowCache, 1, 0, seqLen), kv);
        } else {
            long cutoff = seqLen % size;
            Tensor last = narrow(kv, 1, seqLen - size, size);
            assign(narrow(windowCache, 1, cutoff, size - cutoff), narrow(last, 1, 0, size - cutoff));
            assign(narrow(windowCache, 1, 0, cutoff), narrow(last, 1, size - cutoff, cutoff));
        }
    }

    private Tensor[] windowKv(Tensor kvRaw, long seqLen, Tensor f, int startPos) {
        Tensor kv = DeepSeekOps.rmsNorm(kvRaw, kvNorm, eps);
        kv = DeepSeekOps.fakeQuantFp8(ropeTail(kv, f, rd, false), 32);
        Tensor windowKv;
        if (startPos == 0) {
            writeWindow(kv, seqLen);
            windowKv = kv;
        } else {
            assign(narrow(windowCache, 1, startPos % ring, 1), kv);
            windowKv = windowCache;
        }
        return new Tensor[]{windowKv, DeepSeekOps.windowTopkIdxs(window, ring, (int) seqLen, startPos)};
    }

    /** Frequencies at the first position of each compression group completed by this call. */
    private Tensor groupFreqs(int startPos, long seqLen) {
        if (startPos == 0) {
            long groupsDone = seqLen / ratio;
            long[] pos = new long[(int) groupsDone];
            for (int i = 0; i < pos.length; i++) {
                pos[i] = (long) i * ratio;
            }
            return o("aten::index_select", freqs, 0L, Dsv.dev(Tensor.of(pos, pos.length)));
        }
        return narrow(freqs, 0, startPos + 1 - ratio, 1);
    }

    private Tensor[] compressPart(Tensor x, Tensor qr, int startPos, long offset) {
        long seqLen = x.size(1);
        long compressLen = (startPos + seqLen) / ratio;
        Tensor latent = null;
        long t = System.nanoTime();
        if (compressor != null) {
            latent = compressor.forward(x, startPos);
            shared.compressKv = compressCache;
        }
        t = Dsv.tick("cmp.compressor", t);
        Tensor idxs;
        if (indexer == null) {
            idxs = shared.topkIdxs;
        } else {
            idxs = compressLen == 0
                    ? Dsv.zeros(ScalarType.Int32, 1, seqLen, 0)
                    : indexer.forward(x, qr, latent, startPos, offset, shared, groupFreqs(startPos, seqLen));
            shared.topkIdxs = Dsv.keep(idxs);
        }
        t = Dsv.tick("cmp.indexer", t);
        if (latent != null) {
            Tensor rotated = ropeTail(latent, groupFreqs(startPos, seqLen), rd, false);
            rotated = DeepSeekOps.fakeQuantFp4(rotated, 16, true);
            assign(narrow(compressCache, 1, startPos / ratio, rotated.size(1)), rotated);
        }
        Dsv.tick("cmp.cache", t);
        return new Tensor[]{narrow(shared.compressKv, 1, 0, compressLen), idxs};
    }

    /**
     * Pools {@code ratio} consecutive tokens into one KV latent with a learned softmax gate. Returns the latent before
     * RoPE, or {@code null} while a group is still filling (during decode it only yields every {@code ratio} steps).
     */
    static final class Compressor {
        private final int ratio;
        private final int headDim;
        private final double eps;
        private final Tensor normW;
        private final DeepSeekLinear wkv;
        private final DeepSeekLinear wgate;
        private Tensor kvState;
        private Tensor scoreState;

        Compressor(DeepSeekModelArgs args, DeepSeekCheckpoint ck, int layer) {
            String p = "layers." + layer + ".attn.compressor";
            this.ratio = args.compressRatio(layer);
            this.headDim = args.headDim();
            this.eps = args.normEps();
            this.normW = ck.dev(p + ".norm.weight");
            if (ratio > 1) {
                wkv = new DeepSeekLinear(ck, p + ".wkv", ScalarType.Float);
                wgate = new DeepSeekLinear(ck, p + ".wgate", ScalarType.Float);
            } else {
                wkv = new DeepSeekLinear(ck, p + ".wkv", null);
                wgate = null;
            }
        }

        Tensor[] newState() {
            if (ratio <= 1) {
                return new Tensor[2];
            }
            return new Tensor[]{Dsv.zeros(ScalarType.Float, 1, ratio, headDim),
                    Dsv.full(ScalarType.Float, Double.NEGATIVE_INFINITY, 1, ratio, headDim)};
        }

        void use(Tensor kv, Tensor score) {
            kvState = kv;
            scoreState = score;
        }

        /**
         * One decode step for each of several sequences ({@code x} {@code [1, n, dim]}, one row each). Rows whose group
         * is not complete yet get an unspecified latent: the caller only stores the rows that emit.
         */
        /**
         * {@link #forwardBatch} for a verify step: every row is independent (a row that completes a group reads the
         * positions before the step from the sequence's saved partial group and the others from the rows that hold them),
         * and the projections are kept for {@link #commit}, which writes the saved group once the accepted rows are known.
         */
        Tensor forwardVerify(Tensor x, Tensor pos, Tensor kvPtrs, Tensor scorePtrs, Tensor jIdx, Tensor rowKv, Tensor rowScore) {
            if (ratio == 1) {
                return DeepSeekOps.rmsNorm(wkv.forward(x), normW, eps);
            }
            ScalarType dtype = x.dtype();
            Tensor xf = DeepSeekOps.f32(x);
            Tensor kv = wkv.forward(xf).contiguous();
            Tensor score = wgate.forward(xf).contiguous();
            Aten.call("aten::copy_", rowKv, kv.reshape(-1, headDim), false);
            Aten.call("aten::copy_", rowScore, score.reshape(-1, headDim), false);
            Tensor pooled = Native.dsCompressRows(kv, score, kvPtrs, scorePtrs, pos, jIdx, ratio);
            return DeepSeekOps.rmsNorm(pooled.to(dtype), normW, eps);
        }

        /** After a verify step: writes each sequence's unfinished group from the rows it processed. */
        void commit(Tensor kvPtrs, Tensor scorePtrs, Tensor rowKv, Tensor rowScore, Tensor base, Tensor p0, Tensor last) {
            Native.dsCompressCommit(rowKv, rowScore, kvPtrs, scorePtrs, base, p0, last, ratio);
        }

        Tensor forwardBatch(Tensor x, Tensor pos, Tensor kvPtrs, Tensor scorePtrs) {
            if (ratio == 1) {
                return DeepSeekOps.rmsNorm(wkv.forward(x), normW, eps);
            }
            ScalarType dtype = x.dtype();
            Tensor xf = DeepSeekOps.f32(x);
            Tensor kv = wkv.forward(xf).contiguous();
            Tensor score = wgate.forward(xf).contiguous();
            Tensor pooled = Native.dsCompressState(kv, score, kvPtrs, scorePtrs, pos, ratio);
            return DeepSeekOps.rmsNorm(pooled.to(dtype), normW, eps);
        }

        Tensor forward(Tensor x, int startPos) {
            if (ratio == 1) {
                return DeepSeekOps.rmsNorm(wkv.forward(x), normW, eps);
            }
            ScalarType dtype = x.dtype();
            long seqLen = x.size(1);
            Tensor xf = DeepSeekOps.f32(x);
            Tensor kv = wkv.forward(xf);
            Tensor score = wgate.forward(xf);
            boolean should;
            if (startPos == 0) {
                should = seqLen >= ratio;
                long rem = seqLen % ratio;
                long cutoff = seqLen - rem;
                if (rem > 0) {
                    assign(narrow(kvState, 1, 0, rem), narrow(kv, 1, cutoff, rem));
                    assign(narrow(scoreState, 1, 0, rem), narrow(score, 1, cutoff, rem));
                    kv = narrow(kv, 1, 0, cutoff);
                    score = narrow(score, 1, 0, cutoff);
                }
                if (!should) {
                    return null;
                }
                kv = kv.reshape(1, cutoff / ratio, ratio, headDim);
                score = score.reshape(1, cutoff / ratio, ratio, headDim);
                kv = o("aten::sum.dim_IntList", o("aten::mul.Tensor", kv, o("aten::softmax.int", score, 2L)), new long[]{2});
            } else {
                should = (startPos + 1) % ratio == 0;
                long slot = startPos % ratio;
                assign(narrow(kvState, 1, slot, 1), kv);
                assign(narrow(scoreState, 1, slot, 1), score);
                if (!should) {
                    return null;
                }
                kv = o("aten::sum.dim_IntList", o("aten::mul.Tensor", kvState, o("aten::softmax.int", scoreState, 1L)),
                        new long[]{1}, true);
            }
            return DeepSeekOps.rmsNorm(kv.to(dtype), normW, eps);
        }
    }

    /**
     * Scores the compressed positions with a small side attention (fp4 queries against one shared key per position)
     * and keeps the {@code indexTopk} best per query. With a candidate source layer this is the second level of a
     * two-level top-k. The candidate path is a faithful port but is not covered by the slice fixtures.
     */
    static final class Indexer {
        private final DeepSeekModelArgs args;
        private final int layer;
        private final int ratio;
        private final boolean ownsK;
        private final boolean candidateSource;
        private final boolean usesCandidates;
        private final int heads;
        private final int hd;
        private final int rd;
        private final Tensor rotary;
        private final DeepSeekLinear wqB;
        private final DeepSeekLinear weightsProj;
        private DeepSeekLinear wk;
        private Tensor kNorm;
        private Tensor kCache;

        Indexer(DeepSeekModelArgs args, DeepSeekCheckpoint ck, int layer, Tensor rotary) {
            String p = "layers." + layer + ".attn.indexer";
            this.args = args;
            this.layer = layer;
            this.ratio = args.compressRatio(layer);
            this.ownsK = args.isKvSource(layer);
            this.candidateSource = args.isCandidateSource(layer);
            this.usesCandidates = args.usesCandidates(layer);
            this.heads = args.indexNumHeads();
            this.hd = args.indexHeadDim();
            this.rd = args.ropeHeadDim();
            this.rotary = rotary;
            this.wqB = new DeepSeekLinear(ck, p + ".wq_b", null);
            this.weightsProj = new DeepSeekLinear(ck, p + ".weights_proj", null);
            if (ownsK) {
                this.wk = new DeepSeekLinear(ck, p + ".wk", null);
                this.kNorm = ck.dev(p + ".k_norm.weight");
            }
        }

        Tensor newState() {
            return ownsK ? Dsv.zeros(ScalarType.BFloat16, 1, args.maxSeqLen() / ratio, hd) : null;
        }

        void use(Tensor k) {
            kCache = k;
        }

        /**
         * {@link #forwardBatch} for a CUDA graph (see {@link DeepSeekAttention#forwardBatchGraph}): key positions are
         * scored up to the static capacity {@code tcap}, the pointer tables come from the static block and a group
         * completes where {@code (pos + 1) % ratio == 0} (decided on the device).
         */
        Tensor forwardBatchGraph(Tensor x, Tensor qr, Tensor latent, Tensor rowFreqs, Tensor groupFreqs, Tensor pos,
                                 GraphPtrs gp, BatchShared bs, long tcap, Tensor lens, int offset) {
            int n = (int) x.size(1);
            if (ownsK && latent != null) {
                Tensor k = DeepSeekOps.rmsNorm(wk.forward(latent), kNorm, args.normEps());
                k = DeepSeekOps.fakeQuantFp4(ropeTail(k, groupFreqs, rd, false), 32, false);
                Native.dsCacheScatterGated(k.contiguous(), gp.view(layer, GraphPtrs.INDEX_K), pos, ratio);
            }
            if (ownsK) {
                bs.indexKPtrs = Dsv.keep(gp.view(layer, GraphPtrs.INDEX_K));
            }
            Tensor q = wqB.forward(qr).reshape(1, n, heads, hd);
            q = ropeTail(q, rowFreqs, rd, false);
            q = DeepSeekOps.fakeQuantFp4(q, 32, false);
            Tensor weights = o("aten::mul.Scalar", weightsProj.forward(x),
                    Math.pow(hd, -0.5) * Math.pow(heads, -0.5));
            Tensor score = Native.dsIndexScorePtrs(q.contiguous(), bs.indexKPtrs, lens, weights.contiguous(), tcap);
            if (candidateSource) {
                bs.candidates = Dsv.keep(selectCandidateBlocks(score, lens, args.candidateTopkBlocks(), args.candidateBlockSize()));
            } else if (usesCandidates) {
                score = o("aten::masked_fill.Scalar", score, o("aten::logical_not", bs.candidates), Double.NEGATIVE_INFINITY);
            }
            long topk = Math.min(args.indexTopk(), tcap);
            Tensor top = Aten.call("aten::topk", score, topk, -1L, true, false)[1];
            Tensor idxs = Aten.call("aten::sort", top, -1L, false)[0];
            return Native.dsIndexFinalize(idxs, lens, offset);
        }

        /**
         * The decode step of {@link #forward} for several sequences at once ({@code x} {@code [1, n, dim]}): each row is
         * scored against its own sequence's key cache. Returns the int32 positions {@code [1, n, k]} (-1 for none).
         */
        Tensor forwardBatch(Tensor x, Tensor qr, Tensor latent, Tensor rowFreqs, Tensor groupFreqs, Tensor pos,
                            Tensor emit, PtrTables t, BatchShared bs, long tmax, Tensor lens, int offset) {
            int n = (int) x.size(1);
            if (ownsK && latent != null) {
                Tensor k = DeepSeekOps.rmsNorm(wk.forward(latent), kNorm, args.normEps());
                k = DeepSeekOps.fakeQuantFp4(ropeTail(k, groupFreqs, rd, false), 32, false);
                Native.dsCacheScatter(k.contiguous(), t.indexK, pos, ratio, emit);
            }
            if (ownsK) {
                bs.indexKPtrs = t.indexK;
            }
            Tensor q = wqB.forward(qr).reshape(1, n, heads, hd);
            q = ropeTail(q, rowFreqs, rd, false);
            q = DeepSeekOps.fakeQuantFp4(q, 32, false);
            Tensor weights = o("aten::mul.Scalar", weightsProj.forward(x),
                    Math.pow(hd, -0.5) * Math.pow(heads, -0.5));
            Tensor score = Native.dsIndexScorePtrs(q.contiguous(), bs.indexKPtrs, lens, weights.contiguous(), tmax);
            if (candidateSource) {
                bs.candidates = selectCandidateBlocks(score, lens, args.candidateTopkBlocks(), args.candidateBlockSize());
            } else if (usesCandidates) {
                score = o("aten::masked_fill.Scalar", score, o("aten::logical_not", bs.candidates), Double.NEGATIVE_INFINITY);
            }
            long topk = Math.min(args.indexTopk(), tmax);
            Tensor top = Aten.call("aten::topk", score, topk, -1L, true, false)[1];
            Tensor idxs = Aten.call("aten::sort", top, -1L, false)[0];
            return Native.dsIndexFinalize(idxs, lens, offset);
        }

        Tensor forward(Tensor x, Tensor qr, Tensor latent, int startPos, long offset, Shared shared, Tensor groupFreqs) {
            long seqLen = x.size(1);
            long tt = System.nanoTime();
            long endPos = startPos + seqLen;
            if (ownsK && latent != null) {
                Tensor k = DeepSeekOps.rmsNorm(wk.forward(latent), kNorm, args.normEps());
                k = DeepSeekOps.fakeQuantFp4(ropeTail(k, groupFreqs, rd, false), 32, false);
                assign(narrow(kCache, 1, startPos / ratio, k.size(1)), k);
            }
            if (ownsK) {
                shared.indexK = kCache;
            }
            tt = Dsv.tick("idx.k", tt);
            Tensor q = wqB.forward(qr).reshape(1, seqLen, heads, hd);
            q = ropeTail(q, narrow(rotary, 0, startPos, seqLen), rd, false);
            q = DeepSeekOps.fakeQuantFp4(q, 32, false);
            Tensor indexK = narrow(shared.indexK, 1, 0, endPos / ratio);
            Tensor weights = o("aten::mul.Scalar", weightsProj.forward(x),
                    Math.pow(hd, -0.5) * Math.pow(heads, -0.5));
            tt = Dsv.tick("idx.q", tt);
            Tensor score = seqLen >= DeepSeekOps.GEMM_MIN_ROWS && DeepSeekOps.gemmPrefill && q.device().isCUDA() ? scoreGemm(q, indexK, weights) : null;
            if (score == null) {
                score = smile.torch.Native.dsIndexScore(q, indexK, weights);
            }
            if (score == null) {
                score = o("aten::einsum", "bshd,btd->bsht", new Tensor[]{q, indexK});
                score = o("aten::sum.dim_IntList", o("aten::mul.Tensor", o("aten::relu", score), weights.unsqueeze(-1)),
                        new long[]{2});
            }
            tt = Dsv.tick("idx.score", tt);
            Tensor lens;
            if (startPos == 0) {
                long n = seqLen / ratio;
                long[] cl = new long[(int) seqLen];
                for (int i = 0; i < cl.length; i++) {
                    cl[i] = (i + 1) / ratio;
                }
                lens = Dsv.dev(Tensor.of(cl, seqLen, 1));
                Tensor mask = o("aten::ge.Tensor", Dsv.dev(o("aten::arange", n)), lens);
                score = o("aten::masked_fill.Scalar", score, mask, Double.NEGATIVE_INFINITY);
            } else {
                final long compressed = endPos / ratio;
                lens = Dsv.cached("lens:" + compressed, () -> Dsv.dev(Tensor.of(new long[]{compressed}, 1)));
            }
            if (candidateSource) {
                shared.candidates = Dsv.keep(selectCandidateBlocks(score, lens, args.candidateTopkBlocks(), args.candidateBlockSize()));
            } else if (usesCandidates) {
                score = o("aten::masked_fill.Scalar", score, o("aten::logical_not", shared.candidates), Double.NEGATIVE_INFINITY);
            }
            tt = Dsv.tick("idx.mask", tt);
            long topk = Math.min(args.indexTopk(), endPos / ratio);
            Tensor top = Aten.call("aten::topk", score, topk, -1L, true, false)[1];
            Tensor idxs = Aten.call("aten::sort", top, -1L, false)[0];
            tt = Dsv.tick("idx.topk", tt);
            Tensor fin = smile.torch.Native.dsIndexFinalize(idxs, lens, offset);
            if (fin != null) {
                return fin;
            }
            Tensor shifted = o("aten::add.Scalar", idxs, offset);
            return o("aten::where.ScalarOther", o("aten::lt.Tensor", idxs, lens), shifted, -1.0).to(ScalarType.Int32);
        }

        /**
         * Index scores {@code sum_h w[s,h] * relu(q[s,h] . k[t])} of many query rows as one GEMM per chunk of rows (the
         * per-head kernel re-reads every key for each head), float32 throughout. {@code null} if {@code mm.dtype} is missing.
         */
        private Tensor scoreGemm(Tensor q, Tensor indexK, Tensor weights) {
            if (scoreGemmUnavailable || q.dtype() != ScalarType.BFloat16 || indexK.dtype() != ScalarType.BFloat16) {
                return null;
            }
            long rows = q.size(1);
            long n = indexK.size(1);
            long h = q.size(2);
            Tensor kt = o("aten::t", o("aten::select.int", indexK, 0L, 0L));                     // [hd, n]
            long chunk = Math.max(16, Math.min(rows, (64L << 20) / Math.max(1, h * n)));
            Tensor q2 = o("aten::select.int", q, 0L, 0L);                                          // [rows, h, hd]
            Tensor w2 = o("aten::select.int", weights, 0L, 0L);                                    // [rows, h]
            Tensor[] parts = new Tensor[(int) ((rows + chunk - 1) / chunk)];
            try {
                for (int c = 0; c < parts.length; c++) {
                    long r0 = c * chunk;
                    long m = Math.min(chunk, rows - r0);
                    Tensor qc = o("aten::narrow", q2, 0L, r0, m).reshape(m * h, hd);
                    Tensor sc = o("aten::mm.dtype", qc, kt, ScalarType.Float).reshape(m, h, n);
                    Tensor wc = o("aten::narrow", w2, 0L, r0, m);
                    parts[c] = o("aten::sum.dim_IntList", o("aten::mul.Tensor", o("aten::relu", sc),
                            DeepSeekOps.f32(wc).unsqueeze(-1)), new long[]{1});               // [m, n]
                }
            } catch (RuntimeException e) {
                scoreGemmUnavailable = true;
                return null;
            }
            Tensor all = parts.length == 1 ? parts[0] : o("aten::cat", parts, 0L);
            return all.unsqueeze(0);
        }

        private boolean scoreGemmUnavailable;

        /** Level one of the two-level top-k: keeps the best {@code topkBlocks} blocks of {@code blockSize} positions. */
        static Tensor selectCandidateBlocks(Tensor logits, Tensor compressLens, int topkBlocks, int blockSize) {
            long width = logits.size(logits.dim() - 1);
            long pad = (blockSize - width % blockSize) % blockSize;
            Tensor s = o("aten::constant_pad_nd", logits, new long[]{0, pad}, Double.NEGATIVE_INFINITY);
            long[] shape = s.shape();
            long[] blocked = new long[shape.length + 1];
            System.arraycopy(shape, 0, blocked, 0, shape.length - 1);
            blocked[shape.length - 1] = shape[shape.length - 1] / blockSize;
            blocked[shape.length] = blockSize;
            Tensor scores = o("aten::amax", s.reshape(blocked), new long[]{-1});
            long numBlocks = scores.size(scores.dim() - 1);
            Tensor last = o("aten::floor_divide.Scalar", o("aten::sub.Scalar", compressLens, 1), (long) blockSize);
            Tensor isLast = o("aten::eq.Tensor", Dsv.iota(scores, numBlocks), last);
            scores = o("aten::masked_fill.Scalar", scores, isLast, Double.POSITIVE_INFINITY);
            Tensor[] top = Aten.call("aten::topk", scores, Math.min(topkBlocks, numBlocks), -1L, true, true);
            Tensor valid = o("aten::gt.Scalar", top[0], Double.NEGATIVE_INFINITY);
            Tensor keep = o("aten::scatter.value", o("aten::zeros_like", scores, ScalarType.Bool), -1L, top[1], 0.0);
            keep = o("aten::scatter.src", keep, -1L, top[1], valid);
            Tensor expanded = o("aten::repeat_interleave.self_int", keep, (long) blockSize, -1L);
            return narrow(expanded, expanded.dim() - 1, 0, width);
        }
    }
}
