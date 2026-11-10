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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Aten;

import static smile.llm.model.deepseek.Dsv.o;

/**
 * Engram: n-gram hash lookups written into the residual stream, gated by how well they match it.
 *
 * <p>{@link Hash} maps each position to the ids of the 2-, 3- and 4-grams ending there (over the compressed token
 * ids of {@link DeepSeekEngramTokenMap}); {@link Rows} fetches those rows of the huge per-layer table; the layer's
 * {@code wkv} turns the rows into one key per residual copy plus a shared value.
 *
 * @author Haifeng Li
 */
final class DeepSeekEngram {
    /** Source of table rows (the tables are ~100 GB each and may be sharded or fetched on demand). */
    interface Rows {
        /**
         * Looks rows up.
         *
         * @param ids int64 row ids of any shape.
         * @return bf16 rows {@code [..., headDim]}.
         */
        Tensor lookup(Tensor ids);
    }

    /** Rows held in memory: int64 ids {@code [R]}, fp8 weights {@code [R, 256]} and e8m0 scale bytes {@code [R, 8]}. */
    static final class InMemoryRows implements Rows {
        private final Map<Long, Integer> position = new HashMap<>();
        private final Tensor weights;
        private final Tensor scales;

        InMemoryRows(Tensor rows, Tensor weights, Tensor scales) {
            long[] ids = rows.longArray();
            for (int i = 0; i < ids.length; i++) {
                position.put(ids[i], i);
            }
            this.weights = weights;
            this.scales = scales;
        }

        @Override
        public Tensor lookup(Tensor ids) {
            long[] shape = ids.shape();
            long[] flat = Dsv.host(ids).longArray();
            long[] at = new long[flat.length];
            for (int i = 0; i < flat.length; i++) {
                Integer p = position.get(flat[i]);
                if (p == null) {
                    throw new IllegalStateException("engram row " + flat[i] + " is not available");
                }
                at[i] = p;
            }
            Tensor sel = Tensor.of(at, at.length);
            Tensor w = Aten.one("aten::view.dtype", o("aten::index_select", weights, 0L, sel), ScalarType.Float8e4m3fn);
            Tensor s = DeepSeekOps.e8m0ToFloat(o("aten::index_select", scales, 0L, sel));
            long width = w.size(1);
            Tensor v = o("aten::mul.Tensor", w.to(ScalarType.Float).reshape(at.length, -1, 32), s.unsqueeze(-1));
            long[] out = new long[shape.length + 1];
            System.arraycopy(shape, 0, out, 0, shape.length);
            out[shape.length] = width;
            return Dsv.dev(v.reshape(out).to(ScalarType.BFloat16));
        }
    }

    /**
     * Hash ids of the n-grams ending at each position. Look-back stops at the start of the sequence; the compressed
     * ids of the sequence are kept across the prefill/decode split.
     */
    static final class Hash {
        /** Hash multipliers of the released model, from {@code numpy.random.default_rng(10007 * layer)} (see below). */
        private static final Map<Integer, long[]> RELEASED_MULTIPLIERS = Map.of(
                1, new long[]{76632096046245L, 4839876093313L, 35959672319349L, 73987337458391L},
                14, new long[]{67716810739261L, 51510806800915L, 30921347202721L, 82619226485591L});

        private final int ngram;
        private final int heads;
        private final int layers;
        private final long[][][] primes;
        private final long[][] offsets;
        private final long[][] multipliers;
        private final DeepSeekEngramTokenMap tokenMap;
        private final int pad;
        private final long[] cache;

        /**
         * Constructor. The multipliers are the first {@code ngram} values of {@code numpy.random.default_rng(10007 *
         * layer).integers(0, bound, dtype=int64) * 2 + 1} with {@code bound = (2^63 - 1) / compressedVocab / 2}; they
         * are tabulated for the released model (compressed vocabulary 99092, layers 1 and 14) rather than re-deriving
         * numpy's PCG64 seeding.
         *
         * @param args     model arguments (a slice keeps the engram layers it contains).
         * @param tokenMap the compressed token map.
         */
        Hash(DeepSeekModelArgs args, DeepSeekEngramTokenMap tokenMap) {
            this.ngram = args.engramMaxNgram();
            this.heads = args.engramHeads();
            this.layers = args.engramLayers().length;
            this.tokenMap = tokenMap;
            this.pad = tokenMap.map(args.engramPadId());
            this.cache = new long[args.maxSeqLen()];
            if (args.engramCompressedVocab() != 99092 || tokenMap.size() != args.engramCompressedVocab()) {
                throw new UnsupportedOperationException("engram multipliers are only tabulated for compressed vocab 99092");
            }
            Set<Long> seen = new HashSet<>();
            primes = new long[layers][ngram - 1][heads];
            offsets = new long[layers][(ngram - 1) * heads];
            multipliers = new long[layers][];
            for (int l = 0; l < layers; l++) {
                for (int n = 0; n < ngram - 1; n++) {
                    long current = args.engramVocabSize() - 1;
                    for (int h = 0; h < heads; h++) {
                        current = nextPrime(current, seen);
                        seen.add(current);
                        primes[l][n][h] = current;
                    }
                }
                long sum = 0;
                for (int c = 0; c < (ngram - 1) * heads; c++) {
                    offsets[l][c] = sum;
                    sum += primes[l][c / heads][c % heads];
                }
                multipliers[l] = RELEASED_MULTIPLIERS.get(args.engramLayers()[l]);
                if (multipliers[l] == null) {
                    throw new UnsupportedOperationException("no tabulated multipliers for engram layer " + args.engramLayers()[l]);
                }
            }
        }

        private static long nextPrime(long start, Set<Long> seen) {
            long c = start + 1;
            while (!isPrime(c) || seen.contains(c)) {
                c++;
            }
            return c;
        }

        private static boolean isPrime(long n) {
            if (n < 2) {
                return false;
            }
            for (long d = 2; d * d <= n; d++) {
                if (n % d == 0) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Hash ids of new tokens.
         *
         * @param ids      the new token ids.
         * @param startPos absolute position of the first.
         * @return int64 {@code [1, len, layers, (ngram - 1) * heads]}.
         */
        Tensor forward(int[] ids, int startPos) {
            int cols = (ngram - 1) * heads;
            for (int i = 0; i < ids.length; i++) {
                cache[startPos + i] = tokenMap.map(ids[i]);
            }
            long[] out = new long[ids.length * layers * cols];
            long[] tok = new long[ngram];
            for (int i = 0; i < ids.length; i++) {
                int pos = startPos + i;
                boolean blocked = false;
                for (int shift = 0; shift < ngram; shift++) {
                    long src = cache[Math.max(pos - shift, 0)];
                    blocked |= pos < shift;
                    tok[shift] = blocked ? pad : src;
                }
                for (int l = 0; l < layers; l++) {
                    long rolling = tok[0] * multipliers[l][0];
                    for (int n = 1; n < ngram; n++) {
                        rolling ^= tok[n] * multipliers[l][n];
                        for (int h = 0; h < heads; h++) {
                            int col = (n - 1) * heads + h;
                            out[(i * layers + l) * cols + col] = Math.floorMod(rolling, primes[l][n - 1][h]) + offsets[l][col];
                        }
                    }
                }
            }
            return Dsv.dev(Tensor.of(out, 1, ids.length, layers, cols));
        }
    }

    private final Rows rows;
    private final DeepSeekLinear wkv;
    private final Tensor qWeight;
    private final Tensor kWeight;
    private final int hc;
    private final int dim;
    private final double eps;

    DeepSeekEngram(DeepSeekModelArgs args, DeepSeekCheckpoint ck, int layer, Rows rows) {
        String p = "layers." + layer + ".engram";
        this.rows = rows;
        this.wkv = new DeepSeekLinear(ck, p + ".wkv", null);
        this.qWeight = DeepSeekOps.f32(ck.dev(p + ".q_weight"));
        this.kWeight = DeepSeekOps.f32(ck.dev(p + ".k_weight"));
        this.hc = args.hcMult();
        this.dim = args.dim();
        this.eps = args.normEps();
    }

    /**
     * Adds the gated n-gram value to the residual copies.
     *
     * @param x       the stream {@code [1, s, hc, dim]} (bf16).
     * @param hashIds this layer's hash ids {@code [1, s, columns]} (int64).
     * @return the updated stream.
     */
    Tensor forward(Tensor x, Tensor hashIds) {
        long s = x.size(1);
        Tensor emb = rows.lookup(hashIds);                                          // [1, s, cols, headDim]
        Tensor kv = wkv.forward(emb.reshape(1, s, -1));
        Tensor key = DeepSeekOps.f32(Dsv.narrow(kv, -1, 0, (long) hc * dim)).reshape(1, s, hc, dim);
        Tensor value = DeepSeekOps.f32(Dsv.narrow(kv, -1, (long) hc * dim, dim));
        Tensor weight = o("aten::mul.Tensor", qWeight, kWeight);
        Tensor h = DeepSeekOps.f32(x);
        Tensor rstd = o("aten::mul.Tensor",
                o("aten::rsqrt", o("aten::add.Scalar", o("aten::mean.dim", o("aten::mul.Tensor", h, h), new long[]{-1}), eps)),
                o("aten::rsqrt", o("aten::add.Scalar", o("aten::mean.dim", o("aten::mul.Tensor", key, key), new long[]{-1}), eps)));
        Tensor dot = o("aten::mul.Scalar", o("aten::mul.Tensor",
                o("aten::sum.dim_IntList", o("aten::mul.Tensor", o("aten::mul.Tensor", h, weight), key), new long[]{-1}), rstd),
                Math.pow(dim, -0.5));
        Tensor gate = o("aten::sigmoid", o("aten::copysign.Tensor",
                o("aten::sqrt", o("aten::clamp_min", o("aten::abs", dot), 1e-6)), dot));
        return o("aten::add.Tensor", h, o("aten::mul.Tensor", gate.unsqueeze(-1), value.unsqueeze(-2))).to(x.dtype());
    }
}
