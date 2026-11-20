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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.llm.model.deepseek.DeepSeekCheckpoint;

import static smile.llm.model.glm.Gx.*;

/**
 * Multi-head latent attention of GLM-5.3-Flash without rotary embedding, restricted to the tokens chosen by the
 * k-pooled indexer.
 *
 * <p>Nothing is rotated, so {@code kv_b_proj} is absorbed exactly: the query of head h is mapped into the latent
 * space ({@code q W_UK[h]}), attention runs as multi-query attention over the gathered 512-wide latents (shared by
 * all heads), and the weighted latent is mapped to the value space ({@code W_UV[h]}). The cache holds the
 * normalized latents ({@code kvLoraRank} per token) and the pooled indexer keys ({@code indexHeadDim} per
 * {@code indexKpool} tokens); the raw indexer keys of the (at most {@code kpool - 1}) tokens of the open pool are
 * kept until the pool completes. Per step the cost is independent of the context length except for scoring the
 * pooled keys.
 *
 * <p>Indexer: a pool's key is the softmax-weighted mean of its tokens' keys (weights from a per-channel gate plus
 * a learned position offset). A query scores every complete pool whose last token it can see, keeps the best
 * {@code indexTopk / indexKpool} pools, and always attends the tokens of its own incomplete pool.
 *
 * @author Haifeng Li
 */
final class GlmMla {
    /** Bytes of the {@code [n, heads, tile]} float32 score tensor per tile (decode scores every pool in one tile). */
    private static final long SCORE_BUDGET = Long.getLong("smile.glm.index.budget", 512L << 20);
    /** Queries per attention tile (bounds the gathered {@code [q, topk, kvLoraRank]} keys). */
    private static final int QUERY_TILE = Integer.getInteger("smile.glm.attn.queries", 64);

    /** Per-request state of one MLA layer. */
    static final class State {
        final GlmRows latent;
        final GlmRows pool;
        /**
         * Keys (columns 0-127) and gates (128-255) of the tokens of the open pool: the token at position p is in
         * row {@code p % 4}, so the {@code length % 4} pending tokens occupy rows 0.. and the decode kernel can
         * address the ring with the position alone.
         */
        final Tensor ring;
        private long ringPtr;
        long length;

        State(GlmRows latent, GlmRows pool, Tensor ring) {
            this.latent = latent;
            this.pool = pool;
            this.ring = ring;
        }

        /** The device address of the ring (it never moves). */
        long ringPointer() {
            if (ringPtr == 0) {
                ringPtr = smile.torch.Native.dsDataPtrs(new Tensor[]{ring})[0];
            }
            return ringPtr;
        }

        void close() {
            latent.close();
            pool.close();
            ring.close();
        }
    }

    private final GlmModelArgs args;
    /** Heads of this rank's share (tensor parallelism splits the heads; the latent and indexer caches are replicated). */
    private final int heads;
    private final Tensor wQa;
    private final Tensor qaNorm;
    private final Tensor wQb;
    private final Tensor wKva;
    private final Tensor kvaNorm;
    private final Tensor wUk;      // [heads, nope, kvLoraRank]
    private final Tensor wUv;      // [heads, v, kvLoraRank]
    private final Tensor wUvT;     // [heads, kvLoraRank, v]
    private final Tensor wo;
    private final Tensor wIdxQ;
    private final Tensor wIdxK;
    private final Tensor idxNormW;
    private final Tensor idxNormB;
    private final Tensor wIdxW;
    private final Tensor idxApe;
    private final Tensor idxGateW;
    private Tensor wProj1;     // [2336, dim]: q_a | kv_a | indexer k | compress gate | indexer weights, one GEMM
    private Tensor wProj2;     // [heads * 256 + 4096, q_lora]: q_b | indexer q, one GEMM

    GlmMla(GlmModelArgs args, DeepSeekCheckpoint ck, int layer) {
        this.args = args;
        String p = args.layerPrefix(layer) + ".self_attn.";
        this.wQa = weight(ck, p + "q_a_proj");
        this.qaNorm = vector(ck, p + "q_a_layernorm.weight");
        this.heads = args.numHeads() / tpWorld();
        this.wQb = shard(ck, p + "q_b_proj", 0);
        this.wKva = weight(ck, p + "kv_a_proj_with_mqa");
        this.kvaNorm = vector(ck, p + "kv_a_layernorm.weight");
        int nope = args.qkHeadDim();
        int vd = args.vHeadDim();
        Tensor kvb = shard(ck, p + "kv_b_proj", 0);     // [heads * (nope + v), kvLoraRank]
        Tensor[] split = new Tensor[3];
        scoped(() -> {
            Tensor per = o("aten::reshape", kvb, new long[]{heads, nope + vd, args.kvLoraRank()});
            split[0] = keep(o("aten::contiguous", narrow(per, 1, 0, nope)));
            split[1] = keep(o("aten::contiguous", narrow(per, 1, nope, vd)));
            split[2] = keep(o("aten::contiguous", o("aten::transpose.int", narrow(per, 1, nope, vd), 1L, 2L)));
            return split[0];
        });
        kvb.close();
        this.wUk = split[0];
        this.wUv = split[1];
        this.wUvT = split[2];
        this.wo = shard(ck, p + "o_proj", 1);
        this.wIdxQ = weight(ck, p + "indexer.wq_b");
        this.wIdxK = weight(ck, p + "indexer.wk");
        this.idxNormW = vector(ck, p + "indexer.k_norm.weight");
        this.idxNormB = vector(ck, p + "indexer.k_norm.bias");
        this.wIdxW = weight(ck, p + "indexer.weights_proj");
        this.idxApe = vector(ck, p + "indexer.index_kpool_compress_ape");
        this.idxGateW = vector(ck, p + "indexer.index_kpool_compress_gate");
        if (device().isCUDA() && smile.torch.Native.glmMlaEpilogueAvailable() && args.qLoraRank() == 1536 && args.kvLoraRank() == 512
                && args.indexHeads() * args.indexHeadDim() == 4096 && args.indexHeadDim() == 128 && args.indexHeads() == 32) {
            this.wProj1 = keep(cat(0, wQa, wKva, wIdxK, idxGateW.to(wQa.dtype()), wIdxW));
            this.wProj2 = keep(cat(0, wQb, wIdxQ));
        }
    }

    /** dtype of the caches: float32 on the CPU reference path, bfloat16 on a GPU. */
    private static ScalarType cacheType() {
        return weightType() == ScalarType.Float ? ScalarType.Float : ScalarType.BFloat16;
    }

    State newState() {
        return new State(new GlmRows(args.kvLoraRank(), cacheType()), new GlmRows(args.indexHeadDim(), cacheType()),
                keep(zeros(ScalarType.Float, args.indexKpool(), 2L * args.indexHeadDim())));
    }

    /** {@code a b} accumulated and returned in float32 for any input dtype (batched). */
    private static Tensor bmmF32(Tensor a, Tensor b) {
        return a.dtype() == ScalarType.Float ? o("aten::bmm", a, b) : o("aten::bmm.dtype", a, b, ScalarType.Float);
    }

    private static Tensor mmF32(Tensor a, Tensor b) {
        return a.dtype() == ScalarType.Float ? o("aten::mm", a, b) : o("aten::mm.dtype", a, b, ScalarType.Float);
    }

    /**
     * Runs the layer on {@code n} new tokens (positions {@code state.length ..}) and appends their latents.
     *
     * @param x     {@code [n, dim]} normalized hidden rows.
     * @param state the request's state.
     * @param trace optional capture of intermediates ({@code topk} gets the selected token ids).
     * @return {@code [n, dim]}.
     */
    Tensor forward(Tensor x, State state, Map<String, Tensor> trace) {
        Proj p = project(x);
        return allReduce(linear(core(p, 0, x.size(0), state, trace), wo));
    }

    /**
     * One decode step of several requests (one token each): the projections run once over all rows, the caches and
     * the sparse attention are per request.
     *
     * @param x      {@code [B, dim]} normalized hidden rows.
     * @param states the requests' states, one per row.
     * @return {@code [B, dim]}.
     */
    Tensor forwardBatch(Tensor x, State[] states) {
        Proj p = project(x);
        if (decodeKernels()) {
            Tensor merged = decodeKernel(p, states);
            Tensor o1 = linear(merged, wo);
            stage("mla.o_proj", o1);
            Tensor red = allReduce(o1);
            stage("mla.allreduce", red);
            return red;
        }
        Tensor[] rows = new Tensor[states.length];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = core(p, i, 1, states[i], null);
        }
        return allReduce(linear(rows.length == 1 ? rows[0] : cat(0, rows), wo));
    }

    /** Whether the decode step can run on the graph-safe kernels (CUDA, bf16 caches, the real head layout). */
    private boolean decodeKernels() {
        return device().isCUDA() && cacheType() == ScalarType.BFloat16 && args.indexHeads() == 32
                && args.indexHeadDim() == 128 && heads % 8 == 0 && args.kvLoraRank() == 512
                && args.indexKpool() == 4 && args.indexTopk() == 2048 && smile.torch.Native.mlaDecodeAvailable()
                && !"0".equals(System.getenv("GLM_MLA_KERNELS"));
    }

    /**
     * The decode step of {@code B} requests (one token each) on the kernels: append, score, select, attend. Returns the
     * merged heads {@code [B, heads * v]} before the output projection.
     */
    private Tensor decodeKernel(Proj p, State[] states) {
        int b = states.length;
        long maxLen = 0;
        int[] lens = new int[b];
        Tensor[] lat = new Tensor[b];
        for (int i = 0; i < b; i++) {
            State st = states[i];
            lens[i] = (int) st.length;
            maxLen = Math.max(maxLen, st.length + 1);
            st.latent.ensure(st.length + 1);
            st.pool.ensure((st.length >> 2) + 1);
        }
        long[] latP = new long[b];
        long[] poolP = new long[b];
        Tensor[] rings = new Tensor[b];
        for (int i = 0; i < b; i++) {
            latP[i] = states[i].latent.pointer();
            poolP[i] = states[i].pool.pointer();
            rings[i] = states[i].ring;
        }
        long[] ringP = smile.torch.Native.dsDataPtrs(rings);
        Tensor latPtrs = Tensor.of(latP, b).to(device());
        Tensor poolPtrs = Tensor.of(poolP, b).to(device());
        Tensor ringPtrs = Tensor.of(ringP, b).to(device());
        Tensor lensT = Tensor.of(lens, b).to(device());
        long maxPools = Math.max(512, (maxLen / 4 + 511) / 512 * 512);
        Tensor merged = decodeCore(p, latPtrs, poolPtrs, ringPtrs, lensT, maxPools, b);
        for (int i = 0; i < b; i++) {
            State st = states[i];
            st.length++;
            st.latent.setLength(st.length);
            st.pool.setLength(st.length >> 2);
        }
        return merged;
    }

    /**
     * The decode step on device tables only (for CUDA-graph capture): no host reads, fixed shapes. The caller owns the
     * host-side bookkeeping ({@link State#length}, the caches' row counts) and the capacity of the caches.
     *
     * @param x        {@code [B, dim]} normalized hidden rows.
     * @param latPtrs  latent cache addresses, int64 {@code [B]}.
     * @param poolPtrs pooled-key cache addresses, int64 {@code [B]}.
     * @param ringPtrs pending-ring addresses, int64 {@code [B]}.
     * @param lens     tokens before this step, int32 {@code [B]}.
     * @param maxPools row stride of the score buffer (at least {@code maxSeqLen / 4}).
     * @return {@code [B, dim]}.
     */
    Tensor forwardGraph(Tensor x, Tensor latPtrs, Tensor poolPtrs, Tensor ringPtrs, Tensor lens, long maxPools) {
        Proj p = project(x);
        return allReduce(linear(decodeCore(p, latPtrs, poolPtrs, ringPtrs, lens, maxPools, (int) x.size(0)), wo));
    }

    /**
     * The graph-safe part of the decode step: only device tensors, fixed shapes.
     *
     * @param p         the projections of the {@code b} rows.
     * @param latPtrs   latent cache addresses, int64 {@code [b]}.
     * @param poolPtrs  pooled-key cache addresses, int64 {@code [b]}.
     * @param ringPtrs  pending-ring addresses, int64 {@code [b]}.
     * @param lens      tokens before this step, int32 {@code [b]}.
     * @param maxPools  row stride of the score buffer.
     * @param b         rows.
     * @return {@code [b, heads * v]} the merged heads before the output projection.
     */
    private Tensor decodeCore(Proj p, Tensor latPtrs, Tensor poolPtrs, Tensor ringPtrs, Tensor lens, long maxPools, int b) {
        smile.torch.Native.mlaDecodeAppend(p.latent(), p.key(), p.gate(), idxApe, latPtrs, poolPtrs, ringPtrs, lens);
        stage("mla.append", p.latent());
        return decodeAttend(p, latPtrs, poolPtrs, lens, maxPools, b);
    }

    /** The scoring, selection and attention of the decode kernels, after the new rows are in the caches. */
    private Tensor decodeAttend(Proj p, Tensor latPtrs, Tensor poolPtrs, Tensor lens, long maxPools, int b) {
        int nope = args.qkHeadDim();
        Tensor[] fused = p.qi() != null && wUk.dtype() == ScalarType.BFloat16 && wUvT.dtype() == ScalarType.BFloat16
                && nope == 256 && args.vHeadDim() == 256 && args.kvLoraRank() == 512
                ? smile.torch.Native.glmMlaAbsorb(p.qi(), wUk, heads) : null;
        Tensor q;
        Tensor qAbs;
        if (fused != null) {
            qAbs = fused[0];
            q = fused[1];
        } else {
            q = o("aten::contiguous", o("aten::reshape", p.idxQ(), new long[]{b, args.indexHeads(), args.indexHeadDim()})).to(ScalarType.BFloat16);
            qAbs = null;
        }
        stage("mla.absorb+idxq", q);
        Tensor scores = smile.torch.Native.mlaDecodeScores(q, p.weights(), poolPtrs, lens, maxPools, Math.pow(args.indexHeadDim(), -0.5));
        stage("mla.scores", scores);
        Tensor idx = smile.torch.Native.mlaDecodeTopk(scores, lens, args.indexPools());
        stage("mla.topk", idx);
        if (qAbs == null) {
            Tensor qh = o("aten::permute", p.q(), new long[]{1, 0, 2}).to(wUk.dtype());                    // [heads, b, nope]
            qAbs = o("aten::contiguous", o("aten::permute", bmmF32(qh, wUk), new long[]{1, 0, 2})).to(ScalarType.BFloat16);   // [b, heads, r]
        }
        Tensor ctx = smile.torch.Native.mlaDecodeAttend(qAbs, latPtrs, idx, lens, args.indexPools(), Math.pow(nope, -0.5));    // [b, heads, r] f32
        stage("mla.attend", ctx);
        if (fused != null) {
            Tensor merged = smile.torch.Native.glmMlaWuv(ctx, wUvT);
            if (merged != null) {
                stage("mla.w_uv", merged);
                return merged;
            }
        }
        Tensor hv = o("aten::permute", ctx, new long[]{1, 0, 2}).to(wUvT.dtype());                    // [heads, b, r]
        Tensor v = bmmF32(hv, wUvT);                                                                   // [heads, b, v]
        Tensor merged = o("aten::reshape", o("aten::permute", v, new long[]{1, 0, 2}), new long[]{b, (long) heads * args.vHeadDim()});
        stage("mla.w_uv", merged);
        return merged;
    }

    /**
     * The verify window of a speculative round on device tensors only (CUDA-graph capture): {@code rows} consecutive
     * tokens of each of {@code B} requests (request-major rows). The window is appended in one kernel (the ring is only
     * written by {@link #windowCommit}), then every row attends as a decode row with its own length over its request's
     * caches.
     *
     * @param x        {@code [B * rows, dim]} normalized hidden rows.
     * @param req      latent, pooled-key and ring addresses per request, int64 {@code [3, B]}.
     * @param rowPtrs  latent and pooled-key addresses per row, int64 {@code [2, B * rows]}.
     * @param lensReq  tokens before the window, int32 {@code [B]}.
     * @param lensRow  tokens before each row, int32 {@code [B * rows]}.
     * @param maxPools row stride of the score buffer.
     * @param rows     window length.
     * @param keySave  float32 {@code [B * rows, 128]} receives the indexer keys (for the ring commit).
     * @param gateSave float32 {@code [B * rows, 128]} receives the gates.
     * @return {@code [B * rows, dim]}.
     */
    Tensor forwardWindow(Tensor x, Tensor req, Tensor rowPtrs, Tensor lensReq, Tensor lensRow, long maxPools, int rows,
                         Tensor keySave, Tensor gateSave) {
        Proj p = project(x);
        smile.torch.Aten.call("aten::copy_", keySave, p.key(), false);
        smile.torch.Aten.call("aten::copy_", gateSave, p.gate(), false);
        smile.torch.Native.mlaWindowAppend(p.latent(), p.key(), p.gate(), idxApe, o("aten::select.int", req, 0L, 0L),
                o("aten::select.int", req, 0L, 1L), o("aten::select.int", req, 0L, 2L), lensReq, rows);
        Tensor merged = decodeAttend(p, o("aten::select.int", rowPtrs, 0L, 0L), o("aten::select.int", rowPtrs, 0L, 1L),
                lensRow, maxPools, (int) x.size(0));
        return allReduce(linear(merged, wo));
    }

    /** The batched projections of the new rows. */
    private record Proj(Tensor q, Tensor latent, Tensor idxQ, Tensor key, Tensor gate, Tensor weights, Tensor qi) {
        Proj(Tensor q, Tensor latent, Tensor idxQ, Tensor key, Tensor gate, Tensor weights) {
            this(q, latent, idxQ, key, gate, weights, null);
        }
    }

    private Proj project(Tensor x) {
        long n = x.size(0);
        stageStart(x);
        if (wProj1 != null) {
            Tensor[] e = smile.torch.Native.glmMlaEpilogue(linear(x, wProj1), qaNorm, kvaNorm, idxNormW, idxNormB, args.normEps(), 1e-6,
                    Math.pow(args.indexHeads(), -0.5));
            if (e != null) {
                Tensor qi = linear(e[0], wProj2);                                    // [n, heads * 256 + 4096]
                long qw = (long) heads * args.qkHeadDim();
                Tensor q = o("aten::reshape", narrow(qi, 1, 0, qw), new long[]{n, heads, args.qkHeadDim()});
                Tensor idxQ = o("aten::reshape", narrow(qi, 1, qw, 4096), new long[]{n, args.indexHeads(), args.indexHeadDim()});
                return new Proj(q, e[1], idxQ, e[2], e[3], e[4], qi);
            }
        }
        Tensor qa = linear(x, wQa);
        stage("mla.qa", qa);
        Tensor qResid = rms(qa, qaNorm, args.normEps());
        stage("mla.qa_norm", qResid);
        Tensor q = o("aten::reshape", linear(qResid, wQb), new long[]{n, heads, args.qkHeadDim()});
        stage("mla.q_b", q);
        Tensor latent = rms(linear(x, wKva), kvaNorm, args.normEps());
        stage("mla.kv_a", latent);
        Tensor idxQ = o("aten::reshape", linear(qResid, wIdxQ), new long[]{n, args.indexHeads(), args.indexHeadDim()});
        stage("mla.idx_q", idxQ);
        Tensor key = o("aten::layer_norm", linear(x, wIdxK), new long[]{args.indexHeadDim()}, idxNormW, idxNormB, 1e-6, true);
        stage("mla.idx_k", key);
        Tensor gate = linear(x, idxGateW);
        stage("mla.idx_gate", gate);
        Tensor weights = o("aten::contiguous", o("aten::mul.Scalar", linear(x, wIdxW), Math.pow(args.indexHeads(), -0.5)));
        stage("mla.idx_w", weights);
        return new Proj(q, latent, idxQ, key, gate, weights);
    }

    /** Appends rows {@code [row0, row0 + n)} to the request's caches and attends; returns {@code [n, heads * v]}. */
    private Tensor core(Proj p, long row0, long n, State state, Map<String, Tensor> trace) {
        long p0 = state.length;
        state.latent.append(narrow(p.latent(), 0, row0, n));
        completePools(state, narrow(p.key(), 0, row0, n), narrow(p.gate(), 0, row0, n));
        state.length += n;
        Tensor idxQ = o("aten::contiguous", narrow(p.idxQ(), 0, row0, n));
        Tensor wFlat = o("aten::contiguous", narrow(p.weights(), 0, row0, n));
        if (prefillKernelsEnabled && decodeKernels() && state.pool.length() > 0 && smile.torch.Native.indexerScoresAvailable()) {
            long t0 = PROFILE ? sync(wFlat) : 0;
            Tensor idx = selectKernel(wFlat, idxQ, state, p0, n);                      // [n, width] int32
            long t1 = PROFILE ? sync(idx) : 0;
            if (trace != null) {
                trace.put("topk", keep(host(idx)));
            }
            // chunks: the tensor-core batched matmuls beat the one-block-per-query kernel; small steps: the kernel
            Tensor out = n <= ATTEND_KERNEL_ROWS ? attendKernel(narrow(p.q(), 0, row0, n), idx, state)
                    : attend(narrow(p.q(), 0, row0, n), idx.to(ScalarType.Int64), state);
            if (PROFILE) {
                long t2 = sync(out);
                if (n >= 64) {
                    PROFILE_NS[0] += t1 - t0;
                    PROFILE_NS[1] += t2 - t1;
                    PROFILE_NS[2]++;
                }
            }
            return out;
        }
        Tensor topk = select(wFlat, idxQ, state, p0, n);                                // [n, width] int64
        if (trace != null) {
            trace.put("topk", keep(host(topk)));
        }
        return attend(narrow(p.q(), 0, row0, n), topk, state);
    }

    static final boolean PROFILE = System.getenv("GLM_PROFILE") != null;
    /** Accumulated nanoseconds of selection and attention, and the number of chunks (profiling only). */
    static final long[] PROFILE_NS = new long[3];
    /** Accumulated nanoseconds of the query map, the attention kernel, the value map, and the count (profiling only). */
    static final long[] PROFILE_PARTS = new long[4];

    private static long sync(Tensor t) {
        smile.torch.Aten.one("aten::sum", t.to(ScalarType.Float)).doubleValue();
        return System.nanoTime();
    }

    /** Largest chunk whose attention runs on the latent attention kernel (larger ones use batched matmuls). */
    private static final int ATTEND_KERNEL_ROWS = Integer.getInteger("smile.glm.attend.kernel.rows", 16);

    /** Switch of the prefill kernels (tests flip it to compare with the torch-op path). */
    static volatile boolean prefillKernelsEnabled = !"0".equals(System.getenv("GLM_PREFILL_KERNELS"));

    /**
     * The indexer for a chunk of queries on the decode kernels: the fused scoring kernel writes the {@code [n, pools]}
     * scores (visibility masked), the radix top-k kernel selects per row with the row's own pool count, which for a query at
     * position {@code q} is {@code (q + 1) / 4}: exactly its {@code lens} argument.
     *
     * @return {@code [n, indexTopk + kpool - 1]} int32 token ids, {@code -1} unused.
     */
    private Tensor selectKernel(Tensor wFlat, Tensor idxQ, State st, long p0, long n) {
        int heads = args.indexHeads();
        int d = args.indexHeadDim();
        long pools = st.pool.length();
        Tensor poolKeys = st.pool.view();
        Tensor qFused = o("aten::contiguous", o("aten::reshape", idxQ, new long[]{n, heads, d}).to(ScalarType.BFloat16));
        // the [rows, pools] score matrix is the large temporary: bound it
        long rows = Math.max(16, Math.min(n, SCORE_BUDGET / (4L * pools)));
        List<Tensor> parts = new ArrayList<>();
        for (long q0 = 0; q0 < n; q0 += rows) {
            final long from = q0;
            final long qn = Math.min(rows, n - q0);
            parts.add(scopedAll(() -> {
                Tensor scores = smile.torch.Native.indexerScores(narrow(qFused, 0, from, qn), narrow(wFlat, 0, from, qn),
                        poolKeys, 0, pools, p0 + from, Math.pow(d, -0.5));
                Tensor lens = o("aten::arange.start", p0 + from, p0 + from + qn, ScalarType.Int64).to(ScalarType.Int32).to(device());
                return new Tensor[]{smile.torch.Native.mlaDecodeTopk(scores, lens, args.indexPools())};
            })[0]);
        }
        return parts.size() == 1 ? parts.get(0) : cat(0, parts.toArray(Tensor[]::new));
    }

    /**
     * Multi-query latent attention of a chunk on the decode attention kernel: every row reads the request's one latent
     * cache (the same base pointer in every row), then the value map.
     */
    private Tensor attendKernel(Tensor q, Tensor idx, State st) {
        long n = q.size(0);
        int vd = args.vHeadDim();
        long t0 = PROFILE ? sync(q) : 0;
        Tensor qh = o("aten::permute", q, new long[]{1, 0, 2}).to(wUk.dtype());                        // [heads, n, nope]
        Tensor qAbs = o("aten::contiguous", o("aten::permute", bmmF32(qh, wUk), new long[]{1, 0, 2})).to(ScalarType.BFloat16);
        long t1 = PROFILE ? sync(qAbs) : 0;
        long[] ptr = new long[(int) n];
        java.util.Arrays.fill(ptr, st.latent.pointer());
        Tensor latPtrs = Tensor.of(ptr, n).to(device());
        Tensor ctx = smile.torch.Native.mlaDecodeAttend(qAbs, latPtrs, idx, Math.pow(args.qkHeadDim(), -0.5));   // [n, heads, r]
        long t2 = PROFILE ? sync(ctx) : 0;
        Tensor hv = o("aten::permute", ctx, new long[]{1, 0, 2}).to(wUvT.dtype());
        Tensor v = bmmF32(hv, wUvT);                                                                   // [heads, n, v]
        Tensor out = o("aten::reshape", o("aten::permute", v, new long[]{1, 0, 2}), new long[]{n, (long) heads * vd});
        if (PROFILE && n >= 64) {
            long t3 = sync(out);
            PROFILE_PARTS[0] += t1 - t0;
            PROFILE_PARTS[1] += t2 - t1;
            PROFILE_PARTS[2] += t3 - t2;
            PROFILE_PARTS[3]++;
        }
        return out;
    }

    /** Pools the tokens that complete a group of {@code kpool}; the rest stays in the ring. */
    private void completePools(State st, Tensor keyNew, Tensor gateNew) {
        int kpool = args.indexKpool();
        int d = args.indexHeadDim();
        int pending = (int) (st.length % kpool);
        Tensor keys = pending == 0 ? keyNew : cat(0, narrow(narrow(st.ring, 1, 0, d), 0, 0, pending), keyNew);
        Tensor gates = pending == 0 ? gateNew : cat(0, narrow(narrow(st.ring, 1, d, d), 0, 0, pending), gateNew);
        long m = keys.size(0);
        long full = m / kpool * kpool;
        if (full > 0) {
            long pools = full / kpool;
            Tensor k3 = o("aten::reshape", narrow(keys, 0, 0, full), new long[]{pools, kpool, d});
            Tensor g3 = o("aten::reshape", narrow(gates, 0, 0, full), new long[]{pools, kpool, d});
            Tensor probs = o("aten::softmax.int", add(g3, unsqueeze(idxApe, 0)), 1L);
            st.pool.append(sumDim(mul(probs, k3), 1, false));
        }
        if (m > full) {
            long r = m - full;
            Tensor rows = narrow(st.ring, 0, 0, r);
            smile.torch.Aten.call("aten::copy_", narrow(rows, 1, 0, d), narrow(keys, 0, full, r), false);
            smile.torch.Aten.call("aten::copy_", narrow(rows, 1, d, d), narrow(gates, 0, full, r), false);
        }
    }

    /**
     * The indexer: the token ids each new query attends to.
     *
     * @return {@code [n, indexTopk + kpool - 1]} int64 token ids, {@code -1} where unused.
     */
    private Tensor select(Tensor wFlat, Tensor idxQ, State st, long p0, long n) {
        int kpool = args.indexKpool();
        int heads = args.indexHeads();
        int d = args.indexHeadDim();
        long pools = st.pool.length();
        int tailWidth = kpool - 1;
        Tensor pooled = null;
        if (pools > 0) {
            ScalarType ct = cacheType();
            Tensor poolKeys = st.pool.view();                                         // [P, d]
            boolean fused = device().isCUDA() && ct == ScalarType.BFloat16 && heads == 32 && d == 128
                    && smile.torch.Native.indexerScoresAvailable() && !"0".equals(System.getenv("GLM_INDEXER_KERNEL"));
            Tensor qi = o("aten::reshape", idxQ, new long[]{n * heads, d}).to(ct);
            Tensor w = unsqueeze(wFlat, 1);                                                // [n, 1, H]
            Tensor qFused = fused ? o("aten::contiguous", o("aten::reshape", qi, new long[]{n, heads, d})) : null;
            Tensor qpos = unsqueeze(o("aten::arange.start", p0, p0 + n, ScalarType.Int64).to(device()), 1);
            long k = Math.min(args.indexPools(), pools);
            List<Tensor> vals = new ArrayList<>();
            List<Tensor> ids = new ArrayList<>();
            // the fused kernel never builds [n, heads, tile]: tiles only bound the top-k's working set
            long tile = fused ? (n >= 64 ? 16384 : Math.max(pools, 1)) : Math.max(1024, SCORE_BUDGET / (4L * n * heads));
            for (long t0 = 0; t0 < pools; t0 += tile) {
                final long from = t0;
                final long len = Math.min(tile, pools - t0);
                // the [n, heads, tile] scores are large: free them tile by tile
                Tensor[] best = scopedAll(() -> {
                    Tensor index;
                    if (fused) {
                        // scores, relu, head-weighted sum and the visibility mask in one kernel
                        index = smile.torch.Native.indexerScores(qFused, wFlat, poolKeys, from, len, p0, Math.pow(d, -0.5));
                    } else {
                        Tensor pk = narrow(poolKeys, 0, from, len);
                        Tensor scores = o("aten::relu", o("aten::mul.Scalar", mmF32(qi, o("aten::t", pk)), Math.pow(d, -0.5)));
                        scores = o("aten::reshape", scores, new long[]{n, heads, len});
                        index = o("aten::reshape", o("aten::bmm", w, scores), new long[]{n, len});          // [n, tile]
                        // selectable: the pool's last token is visible to the query (pools are complete by construction)
                        Tensor poolEnd = unsqueeze(o("aten::add.Scalar", o("aten::mul.Scalar",
                                o("aten::arange.start", from, from + len, ScalarType.Int64).to(device()), (long) kpool), (long) (kpool - 1)), 0);
                        Tensor visible = o("aten::le.Tensor", poolEnd, qpos);                              // [n, tile]
                        index = o("aten::masked_fill.Scalar", index, o("aten::logical_not", visible), -3.4028234663852886e38);
                    }
                    Tensor[] top = smile.torch.Aten.call("aten::topk", index, Math.min(k, len), -1L, true, true);
                    return new Tensor[]{top[0], o("aten::add.Scalar", top[1], from)};
                });
                vals.add(best[0]);
                ids.add(best[1]);
            }
            Tensor sel;
            if (vals.size() == 1) {
                sel = ids.get(0);
            } else {
                Tensor allVals = cat(1, vals.toArray(Tensor[]::new));
                Tensor allIds = cat(1, ids.toArray(Tensor[]::new));
                Tensor best = smile.torch.Aten.call("aten::topk", allVals, k, -1L, true, true)[1];
                sel = o("aten::gather", allIds, 1L, best);
            }
            // a pool chosen only because fewer than k pools are visible has its end token beyond the query
            Tensor selValid = o("aten::le.Tensor", o("aten::add.Scalar", o("aten::mul.Scalar", sel, (long) kpool), (long) (kpool - 1)), qpos);
            Tensor tokens = add(o("aten::mul.Scalar", unsqueeze(sel, -1), (long) kpool),
                    o("aten::arange", (long) kpool).to(device()));                                  // [n, k, kpool]
            tokens = o("aten::where.ScalarOther", unsqueeze(selValid, -1), tokens, -1L);
            pooled = o("aten::reshape", tokens, new long[]{n, k * kpool});
        }
        // the open pool of every query, as raw tokens
        long[] tail = new long[(int) (n * tailWidth)];
        for (int i = 0; i < n; i++) {
            long visibleCount = p0 + i + 1;
            long count = visibleCount % kpool;
            long start = visibleCount - count;
            for (int j = 0; j < tailWidth; j++) {
                tail[i * tailWidth + j] = j < count ? start + j : -1;
            }
        }
        Tensor tailT = Tensor.of(tail, n, tailWidth).to(device());
        return pooled == null ? tailT : cat(1, pooled, tailT);
    }

    /**
     * Multi-query attention in the latent space over the gathered tokens, then the value map and nothing else
     * (the output projection is the caller's).
     *
     * @param q    {@code [n, heads, nope]} queries.
     * @param topk {@code [n, width]} token ids, {@code -1} unused.
     * @return {@code [n, heads * v]}.
     */
    private Tensor attend(Tensor q, Tensor topk, State st) {
        long n = q.size(0);
        int nope = args.qkHeadDim();
        int vd = args.vHeadDim();
        ScalarType ct = cacheType();
        double scale = Math.pow(nope, -0.5);
        Tensor latent = st.latent.view();                                                 // [T, r]
        long total = latent.size(0);
        // queries into the latent space: [heads, n, nope] x [heads, nope, r] -> [heads, n, r]
        Tensor qh = o("aten::permute", q, new long[]{1, 0, 2});
        Tensor qAbs = bmmF32(qh.to(wUk.dtype()), wUk);
        long width = topk.size(1);
        List<Tensor> outs = new ArrayList<>();
        for (long s0 = 0; s0 < n; s0 += QUERY_TILE) {
            final long from = s0;
            final long qn = Math.min(QUERY_TILE, n - s0);
            // gathered keys and scores of a tile are freed with the tile
            outs.add(scopedAll(() -> {
                Tensor idx = narrow(topk, 0, from, qn);                                       // [qn, width]
                Tensor safe = o("aten::clamp", idx, 0L, total - 1);
                Tensor keys = o("aten::reshape", o("aten::index_select", latent, 0L, o("aten::reshape", safe, new long[]{-1L})),
                        new long[]{qn, width, st.latent.width});                              // [qn, width, r]
                Tensor qa = o("aten::permute", narrow(qAbs, 1, from, qn), new long[]{1, 0, 2}).to(ct);   // [qn, heads, r]
                Tensor scores = o("aten::mul.Scalar", bmmF32(qa, o("aten::transpose.int", keys, 1L, 2L)), scale);   // [qn, heads, width]
                scores = o("aten::masked_fill.Scalar", scores, unsqueeze(o("aten::lt.Scalar", idx, 0L), 1), Double.NEGATIVE_INFINITY);
                Tensor probs = o("aten::softmax.int", scores, -1L).to(ct);
                Tensor ctx = bmmF32(probs, keys);                                             // [qn, heads, r]
                Tensor hv = o("aten::permute", ctx, new long[]{1, 0, 2});                     // [heads, qn, r]
                Tensor v = bmmF32(hv.to(wUvT.dtype()), wUvT);                                 // [heads, qn, v]
                return new Tensor[]{o("aten::reshape", o("aten::permute", v, new long[]{1, 0, 2}), new long[]{qn, (long) heads * vd})};
            })[0]);
        }
        return outs.size() == 1 ? outs.get(0) : cat(0, outs.toArray(Tensor[]::new));
    }
}
