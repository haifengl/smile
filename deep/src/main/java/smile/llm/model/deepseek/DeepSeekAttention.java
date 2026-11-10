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
    }

    private final DeepSeekModelArgs args;
    private final int layer;
    private final Shared shared;
    private final int heads;
    private final int headDim;
    private final int rd;
    private final int window;
    private final int ratio;
    private final int groups;
    private final int oRank;
    private final double eps;
    private final double scale;
    private final Tensor sink;
    private final DeepSeekLinear wqA;
    private final DeepSeekLinear wqB;
    private final DeepSeekLinear wkv;
    private final DeepSeekLinear woB;
    private final Tensor qNorm;
    private final Tensor kvNorm;
    private final Tensor woA;
    private final Tensor freqs;
    private Tensor windowCache;
    private final Compressor compressor;
    private final Indexer indexer;
    private Tensor compressCache;

    DeepSeekAttention(DeepSeekModelArgs args, DeepSeekCheckpoint ck, int layer, Shared shared) {
        String p = "layers." + layer + ".attn";
        this.args = args;
        this.layer = layer;
        this.shared = shared;
        this.heads = args.numHeads();
        this.headDim = args.headDim();
        this.rd = args.ropeHeadDim();
        this.window = args.windowSize();
        this.ratio = args.compressRatio(layer);
        this.groups = args.oGroups();
        this.oRank = args.oLoraRank();
        this.eps = args.normEps();
        this.scale = Math.pow(headDim, -0.5);
        this.sink = DeepSeekOps.f32(ck.dev(p + ".attn_sink"));
        this.wqA = new DeepSeekLinear(ck, p + ".wq_a", null);
        this.wqB = new DeepSeekLinear(ck, p + ".wq_b", null);
        this.wkv = new DeepSeekLinear(ck, p + ".wkv", null);
        this.woB = new DeepSeekLinear(ck, p + ".wo_b", null);
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
        use(newState());
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
     */
    record State(Tensor window, Tensor compress, Tensor kvState, Tensor score, Tensor indexK) {
        void close() {
            for (Tensor t : new Tensor[]{window, compress, kvState, score, indexK}) {
                if (t != null) {
                    t.close();
                }
            }
        }
    }

    /** Allocates the state of a fresh (empty) sequence; the caller owns it. */
    State newState() {
        Tensor[] c = compressor == null ? new Tensor[2] : compressor.newState();
        return new State(Dsv.zeros(ScalarType.BFloat16, 1, window, headDim),
                args.isKvSource(layer) ? Dsv.zeros(ScalarType.BFloat16, 1, args.maxSeqLen() / ratio, headDim) : null,
                c[0], c[1], indexer == null ? null : indexer.newState());
    }

    /** Makes {@code s} the sequence this layer reads and writes. */
    void use(State s) {
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
        Tensor qr = DeepSeekOps.rmsNorm(wqA.forward(x), qNorm, eps);
        Dsv.probe("qr", qr);
        Tensor q = ropeTail(wqB.forward(qr).reshape(1, seqLen, heads, headDim), f, rd, false);
        Dsv.probe("q", q);

        Tensor[] w = windowKv(x, f, startPos);
        Tensor kv = w[0];
        Tensor topk = w[1];
        if (ratio > 0) {
            Tensor[] c = compressPart(x, qr, startPos, kv.size(1));
            kv = cat(1, kv, c[0]);
            topk = cat(-1, topk, c[1]);
        }
        Dsv.probe("kv", kv);
        Tensor out = DeepSeekOps.sparseAttention(q, kv, sink, topk, scale);
        Dsv.probe("sattn", out);
        out = ropeTail(out, f, rd, true);
        Dsv.probe("rope_out", out);
        out = DeepSeekOps.fakeQuantFp8(out.reshape(1, seqLen, groups, -1), 32);
        Tensor wo = woA.reshape(groups, oRank, -1);
        Tensor mid = o("aten::einsum", "bsgd,grd->bsgr", new Tensor[]{DeepSeekOps.f32(out), DeepSeekOps.f32(wo)})
                .to(ScalarType.BFloat16);
        Dsv.probe("mid", mid);
        return woB.forward(mid.reshape(1, seqLen, groups * oRank));
    }

    private Tensor[] windowKv(Tensor x, Tensor f, int startPos) {
        long seqLen = x.size(1);
        Tensor kv = DeepSeekOps.rmsNorm(wkv.forward(x), kvNorm, eps);
        kv = DeepSeekOps.fakeQuantFp8(ropeTail(kv, f, rd, false), 32);
        Tensor windowKv;
        if (startPos == 0) {
            if (seqLen <= window) {
                assign(narrow(windowCache, 1, 0, seqLen), kv);
            } else {
                long cutoff = seqLen % window;
                Tensor last = narrow(kv, 1, seqLen - window, window);
                assign(narrow(windowCache, 1, cutoff, window - cutoff), narrow(last, 1, 0, window - cutoff));
                assign(narrow(windowCache, 1, 0, cutoff), narrow(last, 1, window - cutoff, cutoff));
            }
            windowKv = kv;
        } else {
            assign(narrow(windowCache, 1, startPos % window, 1), kv);
            windowKv = windowCache;
        }
        return new Tensor[]{windowKv, DeepSeekOps.windowTopkIdxs(window, (int) seqLen, startPos)};
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
        if (compressor != null) {
            latent = compressor.forward(x, startPos);
            shared.compressKv = compressCache;
        }
        Tensor idxs;
        if (indexer == null) {
            idxs = shared.topkIdxs;
        } else {
            idxs = compressLen == 0
                    ? Dsv.zeros(ScalarType.Int32, 1, seqLen, 0)
                    : indexer.forward(x, qr, latent, startPos, offset, shared, groupFreqs(startPos, seqLen));
            shared.topkIdxs = idxs;
        }
        if (latent != null) {
            Tensor rotated = ropeTail(latent, groupFreqs(startPos, seqLen), rd, false);
            rotated = DeepSeekOps.fakeQuantFp4(rotated, 16, true);
            assign(narrow(compressCache, 1, startPos / ratio, rotated.size(1)), rotated);
        }
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

        Tensor forward(Tensor x, Tensor qr, Tensor latent, int startPos, long offset, Shared shared, Tensor groupFreqs) {
            long seqLen = x.size(1);
            long endPos = startPos + seqLen;
            if (ownsK && latent != null) {
                Tensor k = DeepSeekOps.rmsNorm(wk.forward(latent), kNorm, args.normEps());
                k = DeepSeekOps.fakeQuantFp4(ropeTail(k, groupFreqs, rd, false), 32, false);
                assign(narrow(kCache, 1, startPos / ratio, k.size(1)), k);
            }
            if (ownsK) {
                shared.indexK = kCache;
            }
            Tensor q = wqB.forward(qr).reshape(1, seqLen, heads, hd);
            q = ropeTail(q, narrow(rotary, 0, startPos, seqLen), rd, false);
            q = DeepSeekOps.fakeQuantFp4(q, 32, false);
            Tensor indexK = narrow(shared.indexK, 1, 0, endPos / ratio);
            Tensor weights = o("aten::mul.Scalar", weightsProj.forward(x),
                    Math.pow(hd, -0.5) * Math.pow(heads, -0.5));
            Tensor score = o("aten::einsum", "bshd,btd->bsht", new Tensor[]{q, indexK});
            score = o("aten::sum.dim_IntList", o("aten::mul.Tensor", o("aten::relu", score), weights.unsqueeze(-1)),
                    new long[]{2});
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
                lens = Dsv.dev(Tensor.of(new long[]{endPos / ratio}, 1));
            }
            if (candidateSource) {
                shared.candidates = selectCandidateBlocks(score, lens, args.candidateTopkBlocks(), args.candidateBlockSize());
            } else if (usesCandidates) {
                score = o("aten::masked_fill.Scalar", score, o("aten::logical_not", shared.candidates), Double.NEGATIVE_INFINITY);
            }
            long topk = Math.min(args.indexTopk(), endPos / ratio);
            Tensor top = Aten.call("aten::topk", score, topk, -1L, true, false)[1];
            Tensor idxs = Aten.call("aten::sort", top, -1L, false)[0];
            Tensor shifted = o("aten::add.Scalar", idxs, offset);
            return o("aten::where.ScalarOther", o("aten::lt.Tensor", idxs, lens), shifted, -1.0).to(ScalarType.Int32);
        }

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
            Tensor isLast = o("aten::eq.Tensor", Dsv.dev(o("aten::arange", numBlocks)), last);
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
