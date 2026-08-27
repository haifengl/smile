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
package smile.llm.model.qwen;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import smile.deep.activation.Sigmoid;
import smile.deep.layer.LinearLayer;
import smile.deep.tensor.Index;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.llm.parallel.TensorParallelGroup;
import smile.llm.parallel.TensorShardSpec;
import smile.llm.quant.DenseLinearRelease;
import smile.llm.quant.LinearOp;
import smile.torch.Native;
import smile.util.AutoScope;

import static smile.torch.Native.check;
import static smile.torch.smile_torch_h.smile_module_create;
import static smile.torch.smile_torch_h.smile_module_free;
import static smile.torch.smile_torch_h.smile_module_register_module;
import static smile.torch.smile_torch_h.smile_module_register_parameter;

/**
 * Qwen3.5 Gated DeltaNet linear-attention mixer (reference / torch path).
 *
 * @author Haifeng Li
 */
public class GatedDeltaNet {
    final MemorySegment module;
    final int hiddenSize;
    final int numKHeads;
    final int numVHeads;
    final int headKDim;
    final int headVDim;
    final int keyDim;
    final int valueDim;
    final int convKernel;
    final int convDim;
    final int linearLayerId;

    LinearOp inProjQkv;
    LinearOp inProjZ;
    LinearOp inProjB;
    LinearOp inProjA;
    LinearOp outProj;
    /** Depthwise conv weights {@code [convDim, kernel]}. */
    final Tensor conv1dWeight;
    /** {@code log(A)} per value head. */
    final Tensor aLog;
    /** Discretization bias per value head. */
    final Tensor dtBias;
    /** Cached float views of A_log / dt_bias (filled lazily after device move). */
    private Tensor aLogF;
    private Tensor dtBiasF;
    final QwenRMSNormGated norm;
    final Sigmoid sigmoid = new Sigmoid(false);
    final TensorParallelGroup tpGroup;
    final int tpRank;

    DeltaNetStatePool statePool;

    /**
     * Constructor.
     *
     * @param args           model hyperparameters.
     * @param linearLayerId  ordinal among linear-attention layers.
     * @param statePool      shared DeltaNet state pool.
     */
    public GatedDeltaNet(QwenModelArgs args, int linearLayerId, DeltaNetStatePool statePool) {
        this(args, linearLayerId, statePool, null, null);
    }

    /**
     * Tensor-parallel constructor using local head counts from {@code shard}.
     *
     * @param args           model hyperparameters.
     * @param linearLayerId  ordinal among linear-attention layers.
     * @param statePool      shared DeltaNet state pool.
     * @param shard          local head / rank shard description, or {@code null} for full width.
     * @param tpGroup        tensor-parallel group, or {@code null} for single-device.
     */
    public GatedDeltaNet(QwenModelArgs args, int linearLayerId, DeltaNetStatePool statePool,
                         TensorShardSpec shard, TensorParallelGroup tpGroup) {
        this.hiddenSize = args.dim();
        this.numKHeads = shard != null ? shard.linearNumKeyHeads() : args.linearNumKeyHeads();
        this.numVHeads = shard != null ? shard.linearNumValueHeads() : args.linearNumValueHeads();
        this.headKDim = args.linearKeyHeadDim();
        this.headVDim = args.linearValueHeadDim();
        this.keyDim = headKDim * numKHeads;
        this.valueDim = headVDim * numVHeads;
        this.convKernel = args.linearConvKernelDim();
        this.convDim = keyDim * 2 + valueDim;
        this.linearLayerId = linearLayerId;
        this.statePool = statePool;
        this.tpGroup = tpGroup;
        this.tpRank = shard != null ? shard.tpRank() : 0;

        if (numVHeads % numKHeads != 0) {
            throw new IllegalArgumentException("linear_num_value_heads must be divisible by linear_num_key_heads");
        }

        this.inProjQkv = new LinearLayer(hiddenSize, keyDim * 2 + valueDim, false);
        this.inProjZ = new LinearLayer(hiddenSize, valueDim, false);
        this.inProjB = new LinearLayer(hiddenSize, numVHeads, false);
        this.inProjA = new LinearLayer(hiddenSize, numVHeads, false);
        this.outProj = new LinearLayer(valueDim, hiddenSize, false);
        this.conv1dWeight = Tensor.zeros(convDim, convKernel);
        this.aLog = Tensor.zeros(numVHeads);
        this.dtBias = Tensor.ones(numVHeads);
        this.norm = new QwenRMSNormGated(headVDim, args.normEps());

        try (Arena arena = Arena.ofConfined()) {
            this.module = check(smile_module_create(MemorySegment.NULL));
            registerDense(module, arena, "in_proj_qkv", inProjQkv);
            registerDense(module, arena, "in_proj_z", inProjZ);
            registerDense(module, arena, "in_proj_b", inProjB);
            registerDense(module, arena, "in_proj_a", inProjA);
            registerDense(module, arena, "out_proj", outProj);
            smile_module_register_module(module, arena.allocateFrom("norm"), norm.module());
            // LibTorch forbids '.' in parameter names; mirror HF as submodule conv1d.weight.
            MemorySegment conv1d = check(smile_module_create(arena.allocateFrom("conv1d")));
            smile_module_register_parameter(conv1d, arena.allocateFrom("weight"), conv1dWeight.handle());
            smile_module_register_module(module, arena.allocateFrom("conv1d"), conv1d);
            smile_module_free(conv1d);
            smile_module_register_parameter(module, arena.allocateFrom("A_log"), aLog.handle());
            smile_module_register_parameter(module, arena.allocateFrom("dt_bias"), dtBias.handle());
        }
        MemorySegment m = this.module;
        Native.CLEANER.register(this, () -> smile_module_free(m));
    }

    private static void registerDense(MemorySegment module, Arena arena, String name, LinearOp op) {
        if (op instanceof LinearLayer ll) {
            smile_module_register_module(module, arena.allocateFrom(name), ll.module());
        }
    }

    /**
     * Replaces the five DeltaNet projections with quantized ops (already sharded).
     *
     * @param qkv packed QKV projection
     * @param z   z projection
     * @param b   beta projection
     * @param a   a projection
     * @param out output projection
     */
    public void replaceProjections(LinearOp qkv, LinearOp z, LinearOp b, LinearOp a, LinearOp out) {
        if (qkv == null || z == null || b == null || a == null || out == null) {
            throw new IllegalArgumentException("all DeltaNet projections required");
        }
        LinearOp oldQkv = this.inProjQkv;
        LinearOp oldZ = this.inProjZ;
        LinearOp oldB = this.inProjB;
        LinearOp oldA = this.inProjA;
        LinearOp oldOut = this.outProj;
        this.inProjQkv = qkv;
        this.inProjZ = z;
        this.inProjB = b;
        this.inProjA = a;
        this.outProj = out;
        DenseLinearRelease.unregisterAndClose(module, "in_proj_qkv", oldQkv);
        DenseLinearRelease.unregisterAndClose(module, "in_proj_z", oldZ);
        if (oldB != b) {
            DenseLinearRelease.unregisterAndClose(module, "in_proj_b", oldB);
        }
        if (oldA != a) {
            DenseLinearRelease.unregisterAndClose(module, "in_proj_a", oldA);
        }
        DenseLinearRelease.unregisterAndClose(module, "out_proj", oldOut);
    }

    /**
     * Replaces GEMM projections typically present as FP8 in Qwen checkpoints
     * ({@code in_proj_qkv}, {@code in_proj_z}, {@code out_proj}), leaving
     * dense {@code in_proj_a}/{@code in_proj_b} for the residual load path.
     */
    public void replaceGemmProjections(LinearOp qkv, LinearOp z, LinearOp out) {
        if (qkv == null || z == null || out == null) {
            throw new IllegalArgumentException("qkv, z, and out projections required");
        }
        LinearOp oldQkv = this.inProjQkv;
        LinearOp oldZ = this.inProjZ;
        LinearOp oldOut = this.outProj;
        this.inProjQkv = qkv;
        this.inProjZ = z;
        this.outProj = out;
        DenseLinearRelease.unregisterAndClose(module, "in_proj_qkv", oldQkv);
        DenseLinearRelease.unregisterAndClose(module, "in_proj_z", oldZ);
        DenseLinearRelease.unregisterAndClose(module, "out_proj", oldOut);
    }

    /**
     * Returns the native module handle for weight registration.
     * @return module handle.
     */
    public MemorySegment module() {
        return module;
    }

    void setStatePool(DeltaNetStatePool pool) {
        this.statePool = pool;
    }

    /** Lazily materializes float A_log / dt_bias caches on the parameter device. */
    private void ensureFloatCaches() {
        if (aLogF == null || dtBiasF == null
                || !aLogF.device().equals(aLog.device())
                || !dtBiasF.device().equals(dtBias.device())) {
            if (aLogF != null && aLogF != aLog) {
                aLogF.close();
            }
            if (dtBiasF != null && dtBiasF != dtBias) {
                dtBiasF.close();
            }
            aLogF = aLog.to(ScalarType.Float);
            dtBiasF = dtBias.to(ScalarType.Float);
            aLogF.detachFromScopes();
            dtBiasF.detachFromScopes();
        }
    }

    /**
     * Forward pass.
     * @param x hidden states {@code [B, S, D]}.
     * @return mixer output {@code [B, S, D]}.
     */
    public Tensor forward(Tensor x) {
        long[] shape = x.shape();
        int batch = (int) shape[0];
        int seqLen = (int) shape[1];
        boolean decode = seqLen == 1 && statePool != null && statePool.boundBatch() > 0;

        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        try {
            Tensor mixedRaw = inProjQkv.forward(x);
            Tensor mixed = mixedRaw.transpose(1, 2); // [B, C, S]
            Tensor zRaw = inProjZ.forward(x);
            Tensor z = zRaw.view(batch, seqLen, numVHeads, headVDim);
            Tensor b = inProjB.forward(x);
            Tensor a = inProjA.forward(x);

            Tensor convState = statePool != null ? statePool.activeConv(linearLayerId) : null;
            Tensor mixedConvBase = decode && convState != null
                    ? GatedDeltaRule.causalConv1dUpdate(mixed, convState, conv1dWeight)
                    : GatedDeltaRule.causalConv1dPrefill(mixed, convState, conv1dWeight);
            mixed.close();
            mixedRaw.close();
            Tensor mixedConv = mixedConvBase.transpose(1, 2); // [B, S, C]

            try (var qSpan = Index.slice(0, keyDim);
                 var kSpan = Index.slice(keyDim, 2 * keyDim);
                 var vSpan = Index.slice(2 * keyDim, 2 * keyDim + valueDim)) {
                Tensor qSlice = mixedConv.get(Index.Ellipsis, qSpan);
                Tensor query = qSlice.view(batch, seqLen, numKHeads, headKDim);
                Tensor kSlice = mixedConv.get(Index.Ellipsis, kSpan);
                Tensor key = kSlice.view(batch, seqLen, numKHeads, headKDim);
                Tensor vSlice = mixedConv.get(Index.Ellipsis, vSpan);
                Tensor value = vSlice.view(batch, seqLen, numVHeads, headVDim);

                int rep = numVHeads / numKHeads;
                if (rep > 1) {
                    Tensor qRep = repeatHeads(query, rep);
                    Tensor kRep = repeatHeads(key, rep);
                    query.close();
                    key.close();
                    query = qRep;
                    key = kRep;
                }

                Tensor beta = sigmoid.forward(b);
                b.close();
                ensureFloatCaches();
                Tensor aF = a.to(ScalarType.Float);
                a.close();
                Tensor aPlusDt = aF.add(dtBiasF);
                Tensor soft = GatedDeltaRule.softplus(aPlusDt);
                Tensor aExp = aLogF.exp();
                Tensor aNeg = aExp.neg();
                Tensor g = aNeg.mul(soft);
                aF.close();
                aPlusDt.close();
                soft.close();
                aExp.close();
                aNeg.close();

                // Prefill and decode both reuse the pool buffer (reset() zeros it).
                Tensor initState = statePool != null ? statePool.activeRecurrent(linearLayerId) : null;

                var result = GatedDeltaRule.recurrentGatedDeltaRule(
                        query, key, value, g, beta, initState, statePool != null, true);
                query.close();
                key.close();
                value.close();
                qSlice.close();
                kSlice.close();
                vSlice.close();
                g.close();
                beta.close();
                mixedConv.close();
                mixedConvBase.close();

                Tensor core = result._1();
                // Non-null only when the kernel allocated a fresh state (no pool).
                if (statePool != null && result._2() != null) {
                    Tensor dest = statePool.activeRecurrent(linearLayerId);
                    dest.put_(result._2(), Index.Colon, Index.Colon, Index.Colon, Index.Colon);
                    result._2().close();
                }

                core = core.reshape(batch * seqLen * numVHeads, headVDim);
                Tensor zFlat = z.reshape(batch * seqLen * numVHeads, headVDim);
                Tensor gated = norm.forward(core, zFlat);
                gated = gated.view(batch, seqLen, valueDim);
                Tensor out = outProj.forward(gated);
                if (tpGroup != null && tpGroup.tpSize() > 1) {
                    tpGroup.allReduceSumInPlace(tpRank, out);
                }
                out.promoteToParent();
                return out;
            }
        } finally {
            Tensor.pop();
        }
    }

    /** Repeats K heads along the head axis to match V head count. */
    private static Tensor repeatHeads(Tensor x, int rep) {
        // x: [B, S, Hk, D] → [B, S, Hk*rep, D]
        long[] s = x.shape();
        try (Tensor u = x.unsqueeze(3);
             Tensor e = u.expand(s[0], s[1], s[2], rep, s[3]);
             Tensor viewed = e.reshape(s[0], s[1], s[2] * rep, s[3])) {
            // Must copy: expand/reshape are views closed by try-with.
            return viewed.copy();
        }
    }
}
