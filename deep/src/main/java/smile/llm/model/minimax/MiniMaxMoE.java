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
package smile.llm.model.minimax;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import smile.deep.activation.Sigmoid;
import smile.deep.layer.LayerBlock;
import smile.deep.tensor.Device;
import smile.deep.tensor.Index;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.llm.parallel.TensorParallelGroup;
import smile.util.AutoScope;

import static smile.torch.smile_torch_h.smile_module_free;
import static smile.torch.smile_torch_h.smile_module_register_parameter;

/**
 * MiniMax-M3 sparse mixture of experts: sigmoid router with a selection bias, top-k routed
 * experts and an always-on shared expert.
 *
 * <p>Routing: {@code scores = sigmoid(x Wg^T)}; experts are chosen by the top-k of
 * {@code scores + e_score_correction_bias}, but the combine weights are the unbiased
 * {@code scores}, renormalized over the chosen experts and multiplied by
 * {@code routed_scaling_factor}. The router runs in float32.
 *
 * <p>Routed experts are stored stacked, one tensor per projection ({@code experts_w1},
 * {@code experts_w3}: {@code [local, I, D]}; {@code experts_w2}: {@code [local, D, I]}), so a rank
 * allocates only the experts it owns and no per-expert modules exist. Under expert parallelism
 * every rank routes all tokens identically, computes only the tokens assigned to its own
 * experts, and the partial outputs are summed with an all-reduce; the shared expert is replicated
 * and added afterwards.
 *
 * <p>This is the reference (eager) implementation: routing decisions are read back to the host
 * and each active expert runs on its own gathered rows, so it is not CUDA-graph capturable.
 *
 * @author Haifeng Li
 */
public class MiniMaxMoE extends LayerBlock {
    final int dim;
    final int numExperts;
    final int topK;
    final double scale;
    final double alpha;
    final double limit;
    final MiniMaxShard shard;
    final TensorParallelGroup group;
    final int localExperts;
    final int expertStart;
    final Tensor gateWeight;
    final Tensor bias;
    /** Stacked routed-expert weights for the experts owned by this rank ({@code null} unless the weight mode is {@link MiniMaxWeightMode#DENSE}). */
    final Tensor expertW1, expertW2, expertW3;
    /** How routed experts are stored and multiplied. */
    final MiniMaxWeightMode mode;
    final int expertInter;
    final int totalExperts;
    /**
     * Quantized gate+up and down weights with their scales: Marlin-packed ({@code [local, D/16, 2I*4]},
     * {@code [local, I/16, D*4]}) or block FP8 ({@code [local, 2I, D]}, {@code [local, D, I]} with 128x128 scales).
     */
    private Tensor w13q, w13s, w2q, w2s;
    /** Raw FP8 staging for the experts of this layer while the checkpoint is being read. */
    private Tensor stage13w, stage13s, stage2w, stage2s;
    private int staged;
    /** NVFP4 experts: float32 per-expert kernel scales of the gate+up and down GEMMs, and the host copy of the checkpoint's. */
    private Tensor w13g, w2g;
    private float[] globalGate, globalUp, globalDown;
    final boolean nvfp4;
    final MiniMaxMlp shared;
    final Sigmoid sigmoid = new Sigmoid(false);
    /** Full-precision router copies (the checkpoint stores them in float32); {@code null} uses the module's. */
    Tensor routerWeight;
    Tensor routerBias;

    /**
     * Constructor for the unsharded layout.
     *
     * @param args model hyperparameters.
     */
    public MiniMaxMoE(MiniMaxModelArgs args) {
        this(args, MiniMaxShard.single(), null);
    }

    /**
     * Constructor.
     *
     * @param args  model hyperparameters.
     * @param shard this rank's slice of the routed experts; their weights are allocated on its device.
     * @param group all-reduce group for {@code shard.size() > 1}, otherwise {@code null}.
     */
    public MiniMaxMoE(MiniMaxModelArgs args, MiniMaxShard shard, TensorParallelGroup group) {
        super();
        if (shard.size() > 1 && group == null) {
            throw new IllegalArgumentException("expert parallelism requires a TensorParallelGroup");
        }
        this.dim = args.dim();
        this.numExperts = args.numExperts();
        this.topK = args.numExpertsPerTok();
        this.scale = args.routedScalingFactor();
        this.alpha = args.swigluAlpha();
        this.limit = args.swigluLimit();
        this.shard = shard;
        this.group = group;
        this.localExperts = shard.localExperts(numExperts);
        this.expertStart = shard.expertStart(numExperts);

        var opts = new Tensor.Options().device(shard.device()).requireGradients(false);
        int inter = args.expertIntermediateSize();
        this.mode = args.weightMode();
        this.nvfp4 = args.nvfp4Experts();
        if (nvfp4 && mode != MiniMaxWeightMode.MARLIN && mode != MiniMaxWeightMode.DENSE) {
            throw new IllegalArgumentException("NVFP4 experts run through Marlin or dequantized, got weight mode " + mode);
        }
        this.expertInter = inter;
        this.totalExperts = numExperts;
        this.gateWeight = Tensor.zeros(opts, numExperts, dim);
        this.bias = Tensor.zeros(opts, numExperts);
        if (mode.quantized()) {
            this.expertW1 = null;
            this.expertW3 = null;
            this.expertW2 = null;
        } else {
            this.expertW1 = Tensor.zeros(opts, localExperts, inter, dim);
            this.expertW3 = Tensor.zeros(opts, localExperts, inter, dim);
            this.expertW2 = Tensor.zeros(opts, localExperts, dim, inter);
        }

        MemorySegment gateModule = smile.torch.Native.check(
                smile.torch.smile_torch_h.smile_module_create(MemorySegment.NULL));
        try (Arena arena = Arena.ofConfined()) {
            smile_module_register_parameter(gateModule, arena.allocateFrom("weight"), gateWeight.handle());
            smile_module_register_parameter(module, arena.allocateFrom("e_score_correction_bias"), bias.handle());
            if (!mode.quantized()) {
                smile_module_register_parameter(module, arena.allocateFrom("experts_w1"), expertW1.handle());
                smile_module_register_parameter(module, arena.allocateFrom("experts_w3"), expertW3.handle());
                smile_module_register_parameter(module, arena.allocateFrom("experts_w2"), expertW2.handle());
            }
        }
        add("gate", gateModule);
        smile_module_free(gateModule);

        if (args.numSharedExperts() > 0) {
            this.shared = new MiniMaxMlp(dim, shard.part(args.sharedIntermediateSize() * args.numSharedExperts()),
                    args.swigluAlpha(), args.swigluLimit(), mode);
            add("shared_experts", shared.module());
        } else {
            this.shared = null;
        }
    }

    /**
     * Installs float32 router parameters so routing is not rounded to the model's compute dtype.
     *
     * @param weight router weight {@code [numExperts, dim]}, float32, on the model's device.
     * @param bias   selection bias {@code [numExperts]}, float32, on the model's device.
     */
    public void setRouterFp32(Tensor weight, Tensor bias) {
        this.routerWeight = weight;
        this.routerBias = bias;
    }

    /**
     * Returns the shared expert.
     * @return the shared expert, or {@code null} when none is configured.
     */
    public MiniMaxMlp sharedExpert() {
        return shared;
    }

    /**
     * Returns the first global expert index owned by this rank.
     * @return first local expert.
     */
    public int expertStart() {
        return expertStart;
    }

    /**
     * Returns the number of experts owned by this rank.
     * @return local expert count.
     */
    public int localExperts() {
        return localExperts;
    }

    /**
     * Returns the stacked weight tensor of a projection.
     *
     * @param projection {@code 1} (gate), {@code 2} (down) or {@code 3} (up).
     * @return the stacked tensor {@code [localExperts, ...]}.
     */
    public Tensor expertWeights(int projection) {
        return switch (projection) {
            case 1 -> expertW1;
            case 2 -> expertW2;
            case 3 -> expertW3;
            default -> throw new IllegalArgumentException("projection must be 1, 2 or 3");
        };
    }

    /**
     * Adds the shared expert's FP8-resident projections to {@code out}.
     *
     * @param prefix name prefix, e.g. {@code layers.3.moe.}.
     * @param out    destination map.
     */
    void collect(String prefix, java.util.Map<String, smile.llm.quant.MxFp8Layer> out) {
        if (shared != null) {
            shared.collect(prefix + "shared_experts.", out);
        }
    }

    /**
     * Receives one routed-expert projection from the checkpoint in a quantized mode. Experts are staged as
     * raw FP8 until the layer is complete, then repacked once for the Marlin MoE kernel or converted to
     * block FP8.
     *
     * @param projection {@code 1} gate, {@code 2} down or {@code 3} up.
     * @param local      expert index within this rank.
     * @param fp8        FP8 weight on the rank's device: {@code [I, D]} for gate/up, {@code [D, I]} for down.
     * @param scale      uint8 E8M0 scales {@code [N, K / 32]}.
     */
    void loadExpertFp8(int projection, int local, Tensor fp8, Tensor scale) {
        if (!mode.quantized()) {
            throw new IllegalStateException("not in a quantized weight mode");
        }
        Device device = fp8.device();
        if (stage13w == null) {
            var f8 = new Tensor.Options().device(device).dtype(ScalarType.Float8e4m3fn).requireGradients(false);
            var u8 = new Tensor.Options().device(device).dtype(ScalarType.UInt8).requireGradients(false);
            stage13w = Tensor.empty(f8, localExperts, 2L * expertInter, dim);
            stage13s = Tensor.empty(u8, localExperts, 2L * expertInter, dim / smile.llm.quant.MxFp8.BLOCK);
            stage2w = Tensor.empty(f8, localExperts, dim, expertInter);
            stage2s = Tensor.empty(u8, localExperts, dim, expertInter / smile.llm.quant.MxFp8.BLOCK);
        }
        try (var noGrad = Tensor.noGradGuard();
             var e = Index.of(local)) {
            Tensor w;
            Tensor sc;
            Index rows = null;
            if (projection == 2) {
                w = stage2w.get(e);
                sc = stage2s.get(e);
            } else {
                int from = projection == 1 ? 0 : expertInter;
                rows = Index.slice(from, from + expertInter);
                w = stage13w.get(e, rows);
                sc = stage13s.get(e, rows);
            }
            try {
                smile.torch.Native.copy_(w, fp8);
                smile.torch.Native.copy_(sc, scale);
            } finally {
                w.close();
                sc.close();
                if (rows != null) {
                    rows.close();
                }
            }
        }
        if (++staged == 3 * localExperts) {
            if (mode == MiniMaxWeightMode.MARLIN) {
                w13q = smile.torch.Native.marlinFp8RepackWeightStack(stage13w);
                w13s = smile.torch.Native.marlinFp8RepackScalesStack(stage13s);
                w2q = smile.torch.Native.marlinFp8RepackWeightStack(stage2w);
                w2s = smile.torch.Native.marlinFp8RepackScalesStack(stage2s);
            } else {
                Tensor[] gu = smile.torch.Native.mxfp8ToBlockFp8(stage13w, stage13s);
                Tensor[] dn = smile.torch.Native.mxfp8ToBlockFp8(stage2w, stage2s);
                w13q = gu[0];
                w13s = gu[1];
                w2q = dn[0];
                w2s = dn[1];
            }
            stage13w.close();
            stage13s.close();
            stage2w.close();
            stage2s.close();
            stage13w = stage13s = stage2w = stage2s = null;
        }
    }

    /**
     * Receives one NVFP4 routed-expert projection from the checkpoint. Experts are staged raw until the layer is
     * complete, then repacked once for the Marlin MoE kernel.
     *
     * @param projection {@code 1} gate, {@code 2} down or {@code 3} up.
     * @param local      expert index within this rank.
     * @param packed     uint8 weight on the rank's device: {@code [I, D / 2]} for gate/up, {@code [D, I / 2]} for down.
     * @param scale      E4M3 scales {@code [N, K / 16]}.
     * @param global     the checkpoint's {@code weight_global_scale} (the weight is {@code fp4 * scale / global}).
     */
    void loadExpertNvfp4(int projection, int local, Tensor packed, Tensor scale, float global) {
        if (!nvfp4) {
            throw new IllegalStateException("not an NVFP4 model");
        }
        if (mode == MiniMaxWeightMode.DENSE) {
            // Reference path: expand to bfloat16 into this expert's slot of the stacked weight.
            try (var noGrad = Tensor.noGradGuard();
                 Tensor w = smile.torch.Native.nvfp4Dequantize(packed, scale, global);
                 var index = Index.of(local);
                 Tensor slot = expertWeights(projection).get(index)) {
                smile.torch.Native.copy_(slot, w);
            }
            return;
        }
        final int group = 16;
        Device device = packed.device();
        if (stage13w == null) {
            var u8 = new Tensor.Options().device(device).dtype(ScalarType.UInt8).requireGradients(false);
            var f8 = new Tensor.Options().device(device).dtype(ScalarType.Float8e4m3fn).requireGradients(false);
            stage13w = Tensor.empty(u8, localExperts, 2L * expertInter, dim / 2);
            stage13s = Tensor.empty(f8, localExperts, 2L * expertInter, dim / group);
            stage2w = Tensor.empty(u8, localExperts, dim, expertInter / 2);
            stage2s = Tensor.empty(f8, localExperts, dim, expertInter / group);
            globalGate = new float[localExperts];
            globalUp = new float[localExperts];
            globalDown = new float[localExperts];
        }
        try (var noGrad = Tensor.noGradGuard();
             var e = Index.of(local)) {
            Tensor w;
            Tensor sc;
            Index rows = null;
            if (projection == 2) {
                w = stage2w.get(e);
                sc = stage2s.get(e);
                globalDown[local] = global;
            } else {
                int from = projection == 1 ? 0 : expertInter;
                rows = Index.slice(from, from + expertInter);
                w = stage13w.get(e, rows);
                sc = stage13s.get(e, rows);
                (projection == 1 ? globalGate : globalUp)[local] = global;
            }
            try {
                smile.torch.Native.copy_(w, packed);
                smile.torch.Native.copy_(sc, scale);
            } finally {
                w.close();
                sc.close();
                if (rows != null) {
                    rows.close();
                }
            }
        }
        if (++staged == 3 * localExperts) {
            // The kernel takes one global scale per expert for the fused gate+up GEMM; vLLM uses the gate's and so do we.
            for (int i = 0; i < localExperts; i++) {
                if (globalGate[i] != globalUp[i]) {
                    throw new IllegalStateException("w1 and w3 global scales differ for local expert " + i + " ("
                            + globalGate[i] + " vs " + globalUp[i] + "); the fused gate+up GEMM needs one");
                }
            }
            Tensor gate = Tensor.of(globalGate, localExperts).to(device);
            Tensor down = Tensor.of(globalDown, localExperts).to(device);
            Tensor[] gu = smile.torch.Native.marlinNvfp4RepackScalesStack(stage13s, gate);
            Tensor[] dn = smile.torch.Native.marlinNvfp4RepackScalesStack(stage2s, down);
            w13s = gu[0];
            w13g = gu[1];
            w2s = dn[0];
            w2g = dn[1];
            w13q = smile.torch.Native.marlinNvfp4RepackWeightStack(stage13w);
            w2q = smile.torch.Native.marlinNvfp4RepackWeightStack(stage2w);
            gate.close();
            down.close();
            stage13w.close();
            stage13s.close();
            stage2w.close();
            stage2s.close();
            stage13w = stage13s = stage2w = stage2s = null;
            globalGate = globalUp = globalDown = null;
        }
    }

    /** Marlin MoE block size: small enough to limit padding, as chosen by vLLM. */
    private int moeBlockSize(int tokens) {
        int block = 8;
        for (int candidate : new int[]{8, 16, 32, 48, 64}) {
            block = candidate;
            if ((double) tokens * topK / numExperts / candidate < 0.9) {
                break;
            }
        }
        return block;
    }

    /**
     * Device-side routing: the chosen experts {@code [T, topK]} (int64) and their combine weights
     * {@code [T, topK]} (float32), with no host read-back.
     */
    private Tensor[] routeOnDevice(Tensor flat) {
        Tensor x = flat.to(ScalarType.Float);
        Tensor w = routerWeight != null ? routerWeight : gateWeight.to(ScalarType.Float);
        Tensor logits = x.matmul(w.transpose(0, 1));
        Tensor biasF = routerBias != null ? routerBias : bias.to(ScalarType.Float);
        Tensor[] fused = smile.torch.Native.moeRoute(logits, biasF, topK, scale);
        if (fused != null) {
            return fused;
        }
        Tensor scores = sigmoid.forward(logits);
        Tensor selection = scores.add(biasF);
        var top = selection.topk(topK, 1, true, true);
        Tensor idx = top._2();
        Tensor picked = scores.gather(1, idx);
        Tensor weights = picked.div(picked.sum(1, true)).mul(scale).contiguous();
        return new Tensor[]{idx.contiguous(), weights};
    }

    private Tensor forwardMarlin(Tensor input) {
        if (w13q == null) {
            throw new IllegalStateException("MXFP8 expert weights have not been loaded");
        }
        long[] shape = input.shape();
        long total = 1;
        for (long s : shape) {
            total *= s;
        }
        int tokens = (int) (total / dim);
        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        try {
            Tensor flat = input.reshape(tokens, dim);
            Tensor[] routed = routeOnDevice(flat);
            int block = moeBlockSize(tokens);
            Tensor align = smile.torch.Native.moeAlignBlockSize(routed[0], expertStart, localExperts, block);
            Tensor gateUp = nvfp4
                    ? smile.torch.Native.marlinMoeNvfp4Gemm(flat, w13q, w13s, w13g, align, routed[1], block, topK, false)
                    : smile.torch.Native.marlinMoeFp8Gemm(flat, w13q, w13s, align, routed[1], block, topK, false);
            Tensor gate = gateUp.get(Index.Colon, Index.slice(0, expertInter));
            Tensor up = gateUp.get(Index.Colon, Index.slice(expertInter, 2 * expertInter));
            Tensor act = MiniMaxMlp.swiglu(gate, up, alpha, limit).contiguous();
            Tensor down = nvfp4
                    ? smile.torch.Native.marlinMoeNvfp4Gemm(act, w2q, w2s, w2g, align, routed[1], block, 1, true)
                    : smile.torch.Native.marlinMoeFp8Gemm(act, w2q, w2s, align, routed[1], block, 1, true);
            Tensor out = down.reshape(tokens, topK, dim).to(ScalarType.Float).sum(1, false);
            if (shard.tp() && shared != null) {
                // The shared expert is split across ranks too: its partial sums ride the routed experts' all-reduce.
                out = out.add(shared.forward(flat).to(ScalarType.Float));
                group.allReduceSumInPlace(shard.rank(), out);
            } else {
                if (group != null) {
                    group.allReduceSumInPlace(shard.rank(), out);
                }
                if (shared != null) {
                    out = out.add(shared.forward(flat).to(ScalarType.Float));
                }
            }
            Tensor result = out.to(input.dtype()).reshape(shape);
            result.promoteToParent();
            return result;
        } finally {
            Tensor.pop();
        }
    }

    /** One routed expert applied to its gathered rows {@code [n, D]} (eager path). */
    private Tensor expertForward(Tensor rows, int e) {
        if (mode.blockFp8()) {
            boolean emulate = mode == MiniMaxWeightMode.FP8_EMULATED;
            try (var idx = Index.of(e)) {
                Tensor gateUp = smile.llm.quant.Fp8BlockGemm.mm(rows, w13q.get(idx), w13s.get(idx), rows.dtype(), emulate);
                Tensor gate = gateUp.get(Index.Colon, Index.slice(0, expertInter));
                Tensor up = gateUp.get(Index.Colon, Index.slice(expertInter, 2 * expertInter));
                Tensor act = MiniMaxMlp.swiglu(gate, up, alpha, limit).contiguous();
                return smile.llm.quant.Fp8BlockGemm.mm(act, w2q.get(idx), w2s.get(idx), rows.dtype(), emulate);
            }
        }
        Tensor gate = rows.matmul(expertW1.get(Index.of(e)).transpose(0, 1));
        Tensor up = rows.matmul(expertW3.get(Index.of(e)).transpose(0, 1));
        Tensor act = MiniMaxMlp.swiglu(gate, up, alpha, limit);
        return act.matmul(expertW2.get(Index.of(e)).transpose(0, 1));
    }

    /** Routing result: chosen expert ids and combine weights, both row-major {@code [T, topK]}. */
    record Routing(long[] experts, float[] weights) { }

    /**
     * Computes top-k routing for a flat token batch.
     *
     * @param flat hidden states {@code [T, D]}.
     * @return expert ids and combine weights, row-major {@code [T, topK]}.
     */
    Routing route(Tensor flat) {
        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        try {
            Tensor x = flat.to(ScalarType.Float);
            Tensor w = routerWeight != null ? routerWeight : gateWeight.to(ScalarType.Float);
            Tensor scores = sigmoid.forward(x.matmul(w.transpose(0, 1)));
            Tensor selection = scores.add(routerBias != null ? routerBias : bias.to(ScalarType.Float));
            var top = selection.topk(topK, 1, true, true);
            Tensor idx = top._2();
            Tensor picked = scores.gather(1, idx);
            Tensor norm = picked.div(picked.sum(1, true)).mul(scale);
            long[] ids = idx.to(Device.CPU()).longArray();
            float[] ws = norm.to(Device.CPU()).floatArray();
            return new Routing(ids, ws);
        } finally {
            Tensor.pop();
        }
    }

    @Override
    public Tensor forward(Tensor input) {
        if (mode == MiniMaxWeightMode.MARLIN) {
            return forwardMarlin(input);
        }
        if (mode.blockFp8() && w13q == null) {
            throw new IllegalStateException("MXFP8 expert weights have not been loaded");
        }
        long[] shape = input.shape();
        long total = 1;
        for (long s : shape) {
            total *= s;
        }
        int tokens = (int) (total / dim);
        Device device = input.device();
        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        try {
            Tensor flat = input.reshape(tokens, dim);
            Routing routing = route(flat);

            // Group (token, weight) pairs by local expert; pairs owned by other ranks are skipped.
            long[][] toks = new long[localExperts][];
            float[][] wts = new float[localExperts][];
            int[] count = new int[localExperts];
            for (long e : routing.experts) {
                int local = (int) e - expertStart;
                if (local >= 0 && local < localExperts) {
                    count[local]++;
                }
            }
            for (int e = 0; e < localExperts; e++) {
                toks[e] = new long[count[e]];
                wts[e] = new float[count[e]];
                count[e] = 0;
            }
            for (int t = 0; t < tokens; t++) {
                for (int j = 0; j < topK; j++) {
                    int local = (int) routing.experts[t * topK + j] - expertStart;
                    if (local >= 0 && local < localExperts) {
                        toks[local][count[local]] = t;
                        wts[local][count[local]] = routing.weights[t * topK + j];
                        count[local]++;
                    }
                }
            }

            Tensor out = Tensor.zeros(new Tensor.Options().device(device).dtype(ScalarType.Float), tokens, dim);
            for (int e = 0; e < localExperts; e++) {
                int n = toks[e].length;
                if (n == 0) {
                    continue;
                }
                Tensor rows = flat.get(Tensor.of(toks[e], n).to(device));
                Tensor y = expertForward(rows, e).to(ScalarType.Float);
                y = y.mul(Tensor.of(wts[e], n, 1).to(device));
                Tensor scatterIdx = Tensor.of(toks[e], n, 1).to(device).expand(n, dim).contiguous();
                out.scatterReduce_(0, scatterIdx, y, "sum");
            }
            if (shard.tp() && shared != null) {
                // The shared expert is split across ranks too: its partial sums ride the routed experts' all-reduce.
                out = out.add(shared.forward(flat).to(ScalarType.Float));
                group.allReduceSumInPlace(shard.rank(), out);
            } else {
                if (group != null) {
                    group.allReduceSumInPlace(shard.rank(), out);
                }
                if (shared != null) {
                    out = out.add(shared.forward(flat).to(ScalarType.Float));
                }
            }
            Tensor result = out.to(input.dtype()).reshape(shape);
            result.promoteToParent();
            return result;
        } finally {
            Tensor.pop();
        }
    }
}
