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
import java.util.function.Consumer;
import smile.deep.tensor.Device;
import smile.util.AutoScope;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.InklingNative;

import static smile.llm.model.inkling.Ix.*;

/**
 * One decode step of {@code nb} requests (a token each, at their own positions) through the 48 layer applications with
 * the fused kernels of {@code smile_inkling_ops.cu}, cuBLAS projections and the Marlin MoE kernels. Everything the step
 * reads that changes between steps lives in three small device tensors (tokens, positions, the caches' address table), so
 * the step can be captured in a CUDA graph and replayed; the structure (which application is global, which has messages,
 * how many AttnRes blocks exist) is static.
 *
 * @author Haifeng Li
 */
final class InklingDecoder {
    private static final int OP_ATTNRES = 1, OP_RMS = 2, OP_GATE_DOWN = 3, OP_GATE_APPLY = 4, OP_CONV = 5, OP_PREP = 6,
            OP_ATTN = 7, OP_MERGE = 8, OP_ROUTE = 9, OP_EMBED = 11, OP_SOFTCAP = 12;
    /** Timing experiments only ({@code -Dsmile.inkling.debug.skip=moe,attn,proj}): the results are wrong. */
    private static final String SKIP = System.getProperty("smile.inkling.debug.skip", "");
    private static final int LOCAL_SPLITS = Integer.getInteger("smile.inkling.local.splits", 8);
    private static final int GLOBAL_SPLITS = Integer.getInteger("smile.inkling.global.splits", 64);

    private final InklingModel model;
    private final InklingModelArgs args;
    private final Device device;
    private final int dim;
    /** True while {@link #prefill} runs: one shared state-pointer row, state updated after the chunk. */
    private boolean prefillMode;
    private InklingState curState;
    /**
     * Global layers of a prefill chunk hand the keys more than the relative-bias reach behind the chunk's first query to a
     * tensor-core flash-attention call once there are this many of them; the row kernel covers the rest. (The row kernel costs
     * O(keys) per row on CUDA cores, flash 20-40 times less at 100k keys.)
     */
    private static final int FLASH_MIN_KEYS = Integer.getInteger("smile.inkling.prefill.flash.min", 2048);
    /** Use the MXFP8 copies of the dense weights where they exist (switchable for A/B tests). */
    boolean fp8 = true;

    InklingDecoder(InklingModel model) {
        this.model = model;
        this.args = model.args;
        this.device = model.device;
        this.dim = args.dim();
    }

    static boolean available() {
        return InklingNative.isAvailable();
    }

    private static long p(Tensor t) {
        return InklingNative.dataPtr(t);
    }

    private Tensor empty(ScalarType type, long... shape) {
        return Tensor.empty(new Tensor.Options().dtype(type).device(device), shape);
    }

    private static boolean isF32(Tensor t) {
        return t.dtype() == ScalarType.Float;
    }

    /** AttnRes read of the states (bf16 or float32 {@code [nb, D]}): float32 {@code [nb, nq, D]}. */
    private Tensor attnRes(int nb, List<Tensor> states, Tensor query, int nq) {
        Tensor out = empty(ScalarType.Float, nb, nq, dim);
        int n = states.size();
        long[] ptrs = new long[2 + 3 * nq + n];          // no norm: the weight / output slots stay empty
        ptrs[0] = p(out);
        ptrs[1] = p(query);
        long mask = 0;
        for (int i = 0; i < n; i++) {
            ptrs[2 + 3 * nq + i] = p(states.get(i));
            if (isF32(states.get(i))) {
                mask |= 1L << i;
            }
        }
        InklingNative.op(14, ptrs, new long[]{nb, nq, dim, n, mask, 0}, new double[0]);
        return out;
    }

    /**
     * AttnRes read and the weighted RMSNorm of each query's result in one launch.
     *
     * @param w     per-query norm weights.
     * @param want32 also produce float32 copies of the rounded outputs (into {@code out32}).
     * @return the bf16 outputs {@code [nb, D]} per query.
     */
    private Tensor[] attnResRms(int nb, List<Tensor> states, Tensor query, Tensor[] w, boolean want32, Tensor[] out32) {
        int nq = w.length;
        int n = states.size();
        Tensor[] y = new Tensor[nq];
        long[] ptrs = new long[2 + 3 * nq + n];
        ptrs[0] = 0;
        ptrs[1] = p(query);
        long mask = 0;
        for (int j = 0; j < nq; j++) {
            y[j] = empty(ScalarType.BFloat16, nb, dim);
            ptrs[2 + j] = p(w[j]);
            ptrs[2 + nq + j] = p(y[j]);
            if (want32) {
                out32[j] = empty(ScalarType.Float, nb, dim);
                ptrs[2 + 2 * nq + j] = p(out32[j]);
            }
        }
        for (int i = 0; i < n; i++) {
            ptrs[2 + 3 * nq + i] = p(states.get(i));
            if (isF32(states.get(i))) {
                mask |= 1L << i;
            }
        }
        InklingNative.op(14, ptrs, new long[]{nb, nq, dim, n, mask, 1}, new double[0]);
        return y;
    }

    /**
     * Weighted RMSNorm of {@code n} row sets into bf16 (and optionally a float32 copy of the rounded result).
     *
     * @param x    base addresses of the inputs.
     * @param rowStride elements between the rows of one input.
     */
    private Tensor[] rms(int nb, long[] x, Tensor[] w, long rowStride, boolean xbf16, boolean want32, Tensor[] out32) {
        int n = x.length;
        Tensor[] y = new Tensor[n];
        long[] ptrs = new long[4 * n];
        for (int i = 0; i < n; i++) {
            y[i] = empty(ScalarType.BFloat16, nb, dim);
            ptrs[i] = x[i];
            ptrs[n + i] = p(w[i]);
            ptrs[2 * n + i] = p(y[i]);
            if (want32) {
                out32[i] = empty(ScalarType.Float, nb, dim);
                ptrs[3 * n + i] = p(out32[i]);
            }
        }
        InklingNative.op(OP_RMS, ptrs, new long[]{nb, dim, n, rowStride, 0, xbf16 ? 1 : 0}, new double[]{args.normEps()});
        return y;
    }

    /**
     * Message gates: {@code out_g = (1 - tanh(forget_g . gd)) * h_g + tanh(gain_g . gd) * m_g}.
     *
     * @param h       destination base addresses (float32 rows).
     * @param m       message base addresses.
     */
    private Tensor[] gates(int nb, InklingLayer layer, int[] which, long[] h, long hStride, long[] m, long mStride) {
        int n = which.length;
        long[] ptrs = new long[7 * n];
        Tensor[] out = new Tensor[n];
        for (int i = 0; i < n; i++) {
            Tensor[] g = layer.gates[which[i]];
            out[i] = empty(ScalarType.Float, nb, dim);
            Tensor gd = empty(ScalarType.Float, nb, args.gateRank());
            ptrs[i] = h[i];
            ptrs[n + i] = m[i];
            ptrs[2 * n + i] = p(g[0]);
            ptrs[3 * n + i] = p(g[1]);
            ptrs[4 * n + i] = p(g[2]);
            ptrs[5 * n + i] = p(out[i]);
            ptrs[6 * n + i] = p(gd);
        }
        long[] iv = {nb, dim, args.gateRank(), n, hStride, mStride};
        double[] dv = {args.normEps()};
        InklingNative.op(OP_GATE_DOWN, ptrs, iv, dv);
        InklingNative.op(OP_GATE_APPLY, ptrs, iv, dv);
        return out;
    }

    /**
     * The decode step.
     *
     * @param nb    rows.
     * @param ids   int64 {@code [nb]} input tokens.
     * @param pos   int32 {@code [nb]} positions of the tokens.
     * @param tbl   int64 {@code [apps, nb, 8]} cache addresses (see {@link InklingState#table()}).
     * @param trace receives the output of every layer application (bf16 {@code [nb, D]}), or {@code null}.
     * @return float32 logits {@code [nb, vocab]}, soft-capped.
     */
    Tensor forward(int nb, Tensor ids, Tensor pos, Tensor tbl, Consumer<Tensor> trace) {
        return forward(nb, 1, ids, pos, tbl, trace, null);
    }

    /**
     * The decode step, or a speculative verify chunk.
     *
     * @param rowsPer rows per request: {@code nb} rows are {@code nb / rowsPer} requests of {@code rowsPer} consecutive
     *                tokens at consecutive positions. With {@code rowsPer > 1} the convolution states are not updated
     *                ({@link #commit} does it for the accepted rows).
     * @param taps    bf16 {@code [nb, 4, D]} receiving the draft's target features, or {@code null}.
     */
    Tensor forward(int nb, int rowsPer, Tensor ids, Tensor pos, Tensor tbl, Consumer<Tensor> trace, Tensor taps) {
        return forwardImpl(nb, rowsPer, ids, pos, tbl, trace, taps, null);
    }

    /**
     * Prefills a chunk of one request with the fused kernels: the rows are consecutive positions of {@code st}, the layers run
     * as in a verify chunk (the convolutions read the earlier rows of the chunk), the state is updated after each layer.
     *
     * @param st     the request's state (its length is the position of the first token; the caller advances it).
     * @param tokens the chunk.
     * @param rows   rows whose logits are wanted ({@code null}: the last one, empty: none).
     * @param taps   bf16 {@code [T, taps, D]} receiving the draft's target features, or {@code null}.
     * @param trace  receives the output of every layer application ({@code [T, D]} bf16 on the device), or {@code null}.
     * @return float32 logits {@code [rows, vocab]}, or {@code null} when no rows are wanted.
     */
    Tensor prefill(InklingState st, long[] tokens, int[] rows, Tensor taps, Consumer<Tensor> trace) {
        int nb = tokens.length;
        int start = st.length;
        int[] positions = new int[nb];
        for (int i = 0; i < nb; i++) {
            positions[i] = start + i;
        }
        Tensor ids = Tensor.of(tokens, nb).to(device);
        Tensor pos = Tensor.of(positions, nb).to(device);
        long[] table = st.table();
        Tensor tbl = Tensor.of(table, table.length).to(device);
        prefillMode = true;
        curState = st;
        InklingProf.begin(null);
        try {
            return forwardImpl(nb, nb, ids, pos, tbl, trace, taps, rows == null ? new int[]{nb - 1} : rows);
        } finally {
            prefillMode = false;
            curState = null;
            InklingProf.flush(nb);
        }
    }

    private void stateLast3(Tensor src, long ld, int off, Tensor state, int channels, int rows, boolean f32in) {
        InklingNative.op(21, new long[]{p(src), p(state)}, new long[]{rows, channels, ld, off, f32in ? 1 : 0}, new double[0]);
    }

    private Tensor forwardImpl(int nb, int rowsPer, Tensor ids, Tensor pos, Tensor tbl, Consumer<Tensor> trace, Tensor taps,
                               int[] headRows) {
        Tensor h = empty(ScalarType.BFloat16, nb, dim);
        InklingNative.op(OP_EMBED, new long[]{p(ids), p(model.embed), p(model.embedNorm), p(h)}, new long[]{nb, dim},
                new double[]{args.normEps()});
        List<Tensor> blocks = new ArrayList<>();
        Tensor preH = null;
        List<Tensor> preBlocks = null;
        List<Tensor> prevBlocks = null;
        Tensor prevH = null;
        int pre = 0;
        for (int app = 0; app < args.numApplications(); app++) {
            int layerIndex = args.layerOfApplication(app);
            int pass = args.passOfApplication(app);
            boolean inLoop = args.isLooped(layerIndex);
            boolean first = inLoop && layerIndex == args.coreStart();
            if (first && pass == 0) {
                preH = h;
                preBlocks = new ArrayList<>(blocks);
                pre = blocks.size();
            } else if (first) {
                prevBlocks = new ArrayList<>(blocks.subList(pre + 1, blocks.size()));
                prevH = h;
                h = preH;
                blocks = new ArrayList<>(preBlocks);
            }
            InklingLayer layer = model.layers[layerIndex];
            Tensor next;
            if (prefillMode) {
                // a prefill chunk's temporaries ([T, D] float32 and the like) must not pile up over 48 layers
                AutoScope layerScope = new AutoScope();
                Tensor.push(layerScope);
                try {
                    next = layerStep(nb, rowsPer, app, layer, inLoop && pass > 0, h, blocks, prevBlocks, prevH, pos, tbl);
                    next.promoteToParent();
                } finally {
                    Tensor.pop();
                }
            } else {
                next = layerStep(nb, rowsPer, app, layer, inLoop && pass > 0, h, blocks, prevBlocks, prevH, pos, tbl);
            }
            if (trace != null) {
                trace.accept(next);
            }
            if (taps != null) {
                for (int j = 0; j < model.tapIds.length - 1; j++) {
                    if (model.tapIds[j] - 1 == app) {
                        o("aten::copy_", o("aten::select.int", taps, 1L, (long) j), next);
                    }
                }
            }
            if (layer.blockBoundary) {
                blocks.add(h);
            }
            h = next;
        }
        List<Tensor> states = new ArrayList<>(blocks);
        states.add(h);
        Tensor x = attnResRms(nb, states, model.outputQuery, new Tensor[]{model.finalNorm}, false, null)[0];
        if (taps != null) {
            o("aten::copy_", o("aten::select.int", taps, 1L, (long) model.tapIds.length - 1), x);
        }
        if (headRows != null) {
            if (headRows.length == 0) {
                return null;
            }
            long[] sel = new long[headRows.length];
            for (int i = 0; i < sel.length; i++) {
                sel[i] = headRows[i];
            }
            x = o("aten::index_select", x, 0L, Tensor.of(sel, sel.length).to(device));
            nb = sel.length;                    // the head runs on the wanted rows only
        }
        boolean q = fp8 && model.unembed8 != null;
        Tensor raw = SKIP.contains("head") ? empty(ScalarType.BFloat16, nb, q ? model.unembed8.npad : args.unpaddedVocabSize())
                : q ? model.unembed8.forward(x) : linear(x, model.unembed);
        Tensor logits = empty(ScalarType.Float, nb, args.unpaddedVocabSize());
        InklingNative.op(OP_SOFTCAP, new long[]{p(raw), p(logits)},
                new long[]{(long) nb * args.unpaddedVocabSize(), args.unpaddedVocabSize(), q ? model.unembed8.npad : args.unpaddedVocabSize()},
                new double[]{args.finalLogitSoftcap()});
        return logits;
    }

    /**
     * Rolls the convolution states of {@code requests} requests forward after a verify chunk of {@code rowsPer} rows.
     *
     * @param tbl      the chunk's address table ({@code [apps, requests * rowsPer, 8]}).
     * @param accepted int32 {@code [requests]}: rows kept per request (0 leaves the state alone).
     */
    void commit(int requests, int rowsPer, Tensor tbl, Tensor accepted) {
        InklingNative.op(15, new long[]{p(tbl), p(accepted)},
                new long[]{args.numApplications(), requests, rowsPer, (long) requests * rowsPer, dim,
                        (long) model.layers[0].attention.kvHeads() * 128}, new double[0]);
    }

    private Tensor layerStep(int nb, int rowsPer, int app, InklingLayer layer, boolean messages, Tensor h, List<Tensor> blocks,
                             List<Tensor> prevBlocks, Tensor prevH, Tensor pos, Tensor tbl) {
        InklingAttention at = layer.attention;
        boolean local = layer.local;
        long tblApp = p(tbl) + (long) app * (prefillMode ? 1 : nb) * 8 * 8;
        int tstride = prefillMode ? 0 : 8;        // table row of each batch row: a prefill chunk shares one
        // messages of the previous pass
        Tensor msg = null;
        if (messages) {
            List<Tensor> st = new ArrayList<>(prevBlocks);
            st.add(prevH);
            msg = attnRes(nb, st, o("aten::select.int", model.messageQueries, 0L, (long) (layer.index - args.coreStart())), 4);
        }
        // attention inputs
        Tensor qIn, kIn, vIn;
        if (local) {
            if (layer.index == 0) {
                qIn = kIn = vIn = rms(nb, new long[]{p(h)}, new Tensor[]{layer.inputNorm}, dim, true, false, null)[0];
            } else if (!messages) {
                List<Tensor> st = new ArrayList<>(blocks);
                st.add(h);
                qIn = kIn = vIn = attnResRms(nb, st, layer.attnQuery, new Tensor[]{layer.inputNorm}, false, null)[0];
            } else {
                List<Tensor> st = new ArrayList<>(blocks);
                st.add(h);
                Tensor a = attnRes(nb, st, layer.attnQuery, 1);
                if (messages) {
                    long pa = p(a), pm = p(msg);
                    Tensor[] g = gates(nb, layer, new int[]{0, 1, 2}, new long[]{pa, pa, pa}, dim,
                            new long[]{pm, pm + 4L * dim, pm + 8L * dim}, 4L * dim);
                    Tensor[] y = rms(nb, new long[]{p(g[0]), p(g[1]), p(g[2])},
                            new Tensor[]{layer.inputNorm, layer.inputNorm, layer.inputNorm}, dim, false, false, null);
                    qIn = y[0];
                    kIn = y[1];
                    vIn = y[2];
                } else {
                    qIn = kIn = vIn = rms(nb, new long[]{p(a)}, new Tensor[]{layer.inputNorm}, dim, false, false, null)[0];
                }
            }
        } else if (!messages) {
            List<Tensor> st = new ArrayList<>(blocks);
            st.add(h);
            Tensor[] y = attnResRms(nb, st, layer.attnQuery, new Tensor[]{layer.qNorm, layer.kNorm, layer.vNorm}, false, null);
            qIn = y[0];
            kIn = y[1];
            vIn = y[2];
        } else {
            List<Tensor> st = new ArrayList<>(blocks);
            st.add(h);
            Tensor a = attnRes(nb, st, layer.attnQuery, 3);
            long pa = p(a);
            long[] src = {pa, pa + 4L * dim, pa + 8L * dim};
            long stride = 3L * dim;
            if (messages) {
                long pm = p(msg);
                Tensor[] g = gates(nb, layer, new int[]{0, 1, 2}, src, stride, new long[]{pm, pm + 4L * dim, pm + 8L * dim}, 4L * dim);
                src = new long[]{p(g[0]), p(g[1]), p(g[2])};
                stride = dim;
            }
            Tensor[] y = rms(nb, src, new Tensor[]{layer.qNorm, layer.kNorm, layer.vNorm}, stride, false, false, null);
            qIn = y[0];
            kIn = y[1];
            vIn = y[2];
        }
        InklingProf.lap("f: attnres+rms in", qIn);
        // projections
        Tensor yq, yk, yv;
        long ldq, ldk, ldv;
        int offK = at.offK(), offV = at.offV(), offKProj = local ? -1 : at.offKProj();
        boolean q8 = fp8 && at.f8Wo != null && nb <= 64;       // weight-only FP8 GEMMs are for few rows; big chunks use cuBLAS
        if (local && !messages) {
            yq = yk = yv = q8 ? at.f8All.forward(qIn) : linear(qIn, at.wAll);
            ldq = ldk = ldv = q8 ? at.f8All.npad : at.wAll.size(0);
        } else if (local) {
            // with messages the key and value inputs differ from the query input
            yq = q8 ? at.f8Qm.forward(qIn) : linear(qIn, narrow(at.wAll, 0, 0, at.offK()));
            yk = q8 ? at.f8Km.forward(kIn) : linear(kIn, narrow(at.wAll, 0, at.offK(), at.kRows));
            yv = q8 ? at.f8Vm.forward(vIn) : linear(vIn, narrow(at.wAll, 0, at.offV(), at.vRows));
            ldq = q8 ? at.f8Qm.npad : at.offK();
            ldk = at.kRows;
            ldv = at.vRows;
            offK = 0;
            offV = 0;
        } else {
            yq = q8 ? at.f8Q.forward(qIn) : linear(qIn, at.wQ);
            yk = q8 ? at.f8K.forward(kIn) : linear(kIn, at.wK);
            yv = q8 ? at.f8V.forward(vIn) : linear(vIn, at.wV);
            ldq = q8 ? at.f8Q.npad : at.wQ.size(0);
            ldk = q8 ? at.f8K.npad : at.wK.size(0);
            ldv = q8 ? at.f8V.npad : at.wV.size(0);
        }
        int heads = at.heads();                 // this rank's query heads (Mini: 24; Small on 8 ranks: 6)
        int kvHeads = at.kvHeads();
        int qd = heads * 128;
        Tensor qn = empty(ScalarType.BFloat16, nb, qd);
        Tensor rel = empty(ScalarType.Float, nb, heads * args.relDim());
        Tensor sink = empty(ScalarType.Float, nb, heads);
        InklingProf.lap("f: q/k/v projections", yq);
        InklingNative.op(6, new long[]{p(yq), p(yk), p(yv), p(at.qScale), p(at.relScale), p(at.kConv), p(at.vConv), tblApp,
                        p(pos), p(qn), p(rel), p(sink)},
                new long[]{nb, ldq, ldk, ldv, at.offQ(), at.offRel(), at.offSink(), offK, offKProj,
                        offV, offKProj, local ? 0 : 1, args.logScalingNFloor(), rowsPer, heads, kvHeads, tstride},
                new double[]{args.qkL2ClipThreshold(), args.relL2ClipThreshold(), args.normEps(), args.logScalingAlpha()});
        if (prefillMode) {
            // the next chunk's convolutions start from the last three raw key and value rows of this one
            int kvc = kvHeads * 128;
            stateLast3(yk, ldk, offK, curState.conv[app][InklingState.CONV_K], kvc, nb, false);
            stateLast3(yv, ldv, offV, curState.conv[app][InklingState.CONV_V], kvc, nb, false);
        }
        InklingProf.lap("f: prep (conv, clip, cache write)", qn);
        // attention
        int keys = prefillMode ? curState.length + nb : 0;
        // keys below `far` are more than the bias reach behind every query of the chunk: plain softmax attention, by flash
        int far = prefillMode && !local ? Math.max(0, curState.length - args.relExtent()) : 0;
        boolean flash = far >= FLASH_MIN_KEYS;
        int splits = local ? (nb >= 256 ? 1 : LOCAL_SPLITS)
                : flash ? 4 : (nb >= 256 ? Math.max(1, Math.min(8, keys / 2048)) : GLOBAL_SPLITS);
        Tensor extO = null, extLse = null;
        if (flash) {
            Tensor qh = unsqueeze(contiguous(transpose(reshape(qn, nb, heads, 128), 0, 1)), 0);            // [1, H, T, 128]
            Tensor kc = curState.k[app];
            Tensor vc = curState.v[app];
            Tensor kf = narrow(kc.dtype() == ScalarType.BFloat16 ? kc : kc.to(ScalarType.BFloat16), 1, 0, far);   // [kv, far, 128]
            Tensor vf = narrow(vc.dtype() == ScalarType.BFloat16 ? vc : vc.to(ScalarType.BFloat16), 1, 0, far);
            Tensor ke, ve;
            if (kvHeads == 1) {      // the one KV head is shared by every query head: a stride-0 view, no copy
                ke = o("aten::expand", unsqueeze(kf, 0), new long[]{1, heads, far, 128});
                ve = o("aten::expand", unsqueeze(vf, 0), new long[]{1, heads, far, 128});
            } else {
                int groups = heads / kvHeads;
                ke = unsqueeze(o("aten::repeat_interleave.self_int", kf, (long) groups, 0L), 0);
                ve = unsqueeze(o("aten::repeat_interleave.self_int", vf, (long) groups, 0L), 0);
            }
            Tensor[] fa = smile.torch.Aten.call("aten::_scaled_dot_product_flash_attention", qh, ke, ve);
            extO = reshape(contiguous(transpose(o("aten::select.int", fa[0], 0L, 0L), 0, 1)), nb, qd);        // [T, H * 128] bf16
            extLse = contiguous(transpose(o("aten::select.int", fa[1], 0L, 0L), 0, 1));                      // [T, H] float32
        }
        Tensor attnOut = empty(ScalarType.BFloat16, nb, qd);
        Tensor pm = null, pl = null, pacc = null;
        if (splits > 1) {
            pm = empty(ScalarType.Float, nb, heads, splits);
            pl = empty(ScalarType.Float, nb, heads, splits);
            pacc = empty(ScalarType.Float, nb, heads, splits, 128);
        }
        long[] ap = {p(qn), p(rel), p(sink), p(yq), p(at.projT), tblApp, p(pos), p(attnOut),
                pm == null ? 0 : p(pm), pl == null ? 0 : p(pl), pacc == null ? 0 : p(pacc),
                extO == null ? 0 : p(extO), extLse == null ? 0 : p(extLse)};
        long[] aiv = {nb, ldq, at.offGate(), local ? args.slidingWindow() : 0, splits, heads, kvHeads, tstride, flash ? far : 0};
        if (!SKIP.contains("attn")) {
            InklingNative.op(OP_ATTN, ap, aiv, new double[0]);
            if (splits > 1) {
                InklingNative.op(OP_MERGE, ap, aiv, new double[0]);
            }
        }
        InklingProf.lap(flash ? "f: attention core, global flash + kernel" : local ? "f: attention core, local kernel" : "f: attention core, global kernel", attnOut);
        InklingDebug.tap("qin", app, qIn);
        Tensor o = SKIP.contains("proj") ? empty(ScalarType.BFloat16, nb, dim) : q8 ? at.f8Wo.forward(attnOut) : linear(attnOut, at.wo);
        // sharded: each rank holds the partial sum of its heads; summed in float32 (the peer-to-peer all-reduce, graph-safe,
        // is 3-4x faster than NCCL for these small messages) and read by the convolution kernel as such
        // (a big prefill chunk reduces the bf16 output as the reference path does: half the bytes of a 75 MB message)
        boolean o32 = model.shard.size() > 1 && !(prefillMode && nb > 256);
        if (o32) {
            o = f32(o);
            model.shard.allReduce(o);
        }
        if (model.shard.size() > 1 && !o32) {
            model.shard.allReduce(o);
        }
        InklingProf.lap("f: wo + all-reduce", o);
        InklingDebug.tap("attn", app, o);
        // post-attention convolution and residual
        Tensor residual = empty(ScalarType.Float, nb, dim);
        boolean boundary = layer.blockBoundary;
        InklingNative.op(5, new long[]{p(o), p(h), p(layer.attnConv), tblApp, p(residual), 0, 0},
                new long[]{nb, dim, 4, boundary ? 1 : 0, o32 ? 2 : 0, rowsPer, tstride}, new double[0]);
        if (prefillMode) {
            stateLast3(o, dim, 0, curState.conv[app][InklingState.CONV_ATTN], dim, nb, o32);
        }
        InklingProf.lap("f: conv after attn", residual);
        // MLP input
        List<Tensor> st = new ArrayList<>(blocks);
        if (boundary) {
            st.add(h);
        }
        st.add(residual);
        Tensor[] x32 = new Tensor[1];
        Tensor x;
        if (!messages) {
            x = attnResRms(nb, st, layer.mlpQuery, new Tensor[]{layer.postNorm}, true, x32)[0];
        } else {
            Tensor m = attnRes(nb, st, layer.mlpQuery, 1);
            Tensor[] g = gates(nb, layer, new int[]{3}, new long[]{p(m)}, dim, new long[]{p(msg) + 12L * dim}, 4L * dim);
            InklingDebug.tap("mlpin", app, g[0]);
            x = rms(nb, new long[]{p(g[0])}, new Tensor[]{layer.postNorm}, dim, false, true, x32)[0];
        }
        InklingProf.lap("f: attnres+rms mlp", x);
        // routing and experts
        Tensor idx = empty(ScalarType.Int32, nb, args.numExpertsPerTok());
        Tensor wts = empty(ScalarType.Float, nb, args.numExpertsPerTok());
        Tensor logits = layer.routerLogits(x32[0]);
        if (!SKIP.contains("route")) InklingNative.op(OP_ROUTE, new long[]{p(logits), p(layer.routerBias), p(idx), p(wts)},
                new long[]{nb, args.numExperts(), args.numExpertsPerTok()}, new double[]{args.routeScale()});
        InklingProf.lap("f: router", wts);
        Tensor moe = SKIP.contains("moe") ? empty(ScalarType.Float, nb, dim)
                : nb > 256 ? layer.experts.forwardFused(x, idx, wts) : layer.experts.decode(x, idx, wts);
        InklingProf.lap("f: experts", moe);
        model.shard.allReduce(moe);             // sharded: float32 partial sum over this rank's experts
        InklingProf.lap("f: all-reduce moe", moe);
        InklingDebug.tap("moe", app, bf16(moe));
        // post-MLP convolution and output
        Tensor out = empty(ScalarType.BFloat16, nb, dim);
        InklingNative.op(5, new long[]{p(moe), 0, p(layer.mlpConv), tblApp, p(residual), 0, p(out)},
                new long[]{nb, dim, 5, 0, 1, rowsPer, tstride}, new double[0]);
        if (prefillMode) {
            stateLast3(moe, dim, 0, curState.conv[app][InklingState.CONV_MLP], dim, nb, true);
        }
        InklingProf.lap("f: conv mlp + state", out);
        return out;
    }
}
