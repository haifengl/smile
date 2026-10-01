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
     *
     * @param qkv combined QKV projection op.
     * @param z   z-gate projection op.
     * @param out output projection op.
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
        // Active pool ⇒ continue existing conv/recurrent state. This covers S=1
        // decode and S>1 speculative verify windows. Prefill that zeros the
        // left context is wrong mid-sequence (drops prior conv history and
        // disagrees with token-by-token decode — garbes MTP window verify).
        boolean hasActiveState = statePool != null && statePool.boundBatch() > 0;
        boolean decodeS1 = hasActiveState && seqLen == 1;
        // MTP verify-window forward: run the per-position loop below instead
        // of the batched S>1 path, so a partial-accept reject can restore a
        // retained per-position checkpoint instead of a second full forward.
        // Decode (S=1), prefill, and every other call site never set this flag.
        boolean verifyWindow = hasActiveState && seqLen > 1 && statePool.verifyWindowActive();
        // Whole-window fast path: one conv update + one fused recurrent launch that
        // snapshots the state after every position (instead of ~15 small launches
        // per position). Falls back to the per-position loop below whenever the
        // native entry point / CUDA / checkpoint buffers are not usable.
        boolean batchedVerify = verifyWindow && canBatchVerifyWindow(x, batch, seqLen);
        boolean verifyLoop = verifyWindow && !batchedVerify;

        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        boolean profile = smile.llm.engine.DecodeForwardProfile.enabled();
        long t0 = profile ? System.nanoTime() : 0L;
        try {
            long tMark = t0;
            Tensor mixedRaw = inProjQkv.forward(x);
            // Decode S=1: [B,1,C] and [B,C,1] share the same contiguous layout —
            // reshape avoids two transpose kernels per linear layer.
            long channels = mixedRaw.shape()[mixedRaw.dim() - 1];
            Tensor mixed = decodeS1
                    ? mixedRaw.reshape(batch, channels, 1)
                    : mixedRaw.transpose(1, 2); // [B, C, S]
            Tensor zRaw = inProjZ.forward(x);
            Tensor z = zRaw.view(batch, seqLen, numVHeads, headVDim);
            Tensor b = inProjB.forward(x);
            Tensor a = inProjA.forward(x);
            if (profile) {
                smile.llm.engine.DecodeForwardProfile.addDeltaProj(System.nanoTime() - tMark);
                tMark = System.nanoTime();
            }

            Tensor convState = statePool != null ? statePool.activeConv(linearLayerId) : null;
            // Fused path: gates first (independent of conv), then conv+recurrent+checkpoints
            // in two native launches. Returns null (nothing modified) when unsupported.
            Tensor fusedCore = null;
            Tensor[] fusedGates = null;
            if (batchedVerify && convState != null && smile.torch.Native.hasGatedDeltaVerifyWindow()) {
                ensureFloatCaches();
                fusedGates = GatedDeltaRule.computeBetaAndDecayGate(a, b, aLogF, dtBiasF);
                fusedCore = fusedVerifyWindow(mixedRaw, convState, fusedGates, batch, seqLen);
                if (fusedCore != null) {
                    a.close();
                    b.close();
                    mixed.close();
                    mixedRaw.close();
                    fusedGates[0].close();
                    fusedGates[1].close();
                    fusedGates = null;
                }
            }
            if (batchedVerify && fusedCore == null) {
                saveWindowConvCheckpoints(mixed, convState, batch, seqLen);
            }
            Tensor query = null;
            Tensor key = null;
            Tensor value = null;
            Tensor qSlice = null;
            Tensor kSlice = null;
            Tensor vSlice = null;
            Tensor mixedConv = null;
            Tensor mixedConvBase = null;

            if (!verifyLoop && decodeS1 && convState != null) {
                Tensor[] qkv = GatedDeltaRule.causalConv1dUpdateSplitQkv(
                        mixed, convState, conv1dWeight,
                        numKHeads, numVHeads, headKDim, headVDim);
                if (qkv != null) {
                    mixed.close();
                    mixedRaw.close();
                    query = qkv[0];
                    key = qkv[1];
                    value = qkv[2];
                }
            }
            if (!verifyLoop && fusedCore == null && query == null) {
                // S>1 with an active pool (MTP verify) must use Update so the
                // prior K-1 conv context is applied; Prefill pads with zeros.
                mixedConvBase = hasActiveState && convState != null
                        ? GatedDeltaRule.causalConv1dUpdate(mixed, convState, conv1dWeight)
                        : GatedDeltaRule.causalConv1dPrefill(mixed, convState, conv1dWeight);
                mixed.close();
                mixedRaw.close();
                mixedConv = decodeS1
                        ? mixedConvBase.reshape(batch, 1, mixedConvBase.shape()[1])
                        : mixedConvBase.transpose(1, 2); // [B, S, C]

                try (var qSpan = Index.slice(0, keyDim);
                     var kSpan = Index.slice(keyDim, 2 * keyDim);
                     var vSpan = Index.slice(2 * keyDim, 2 * keyDim + valueDim)) {
                    qSlice = mixedConv.get(Index.Ellipsis, qSpan);
                    query = qSlice.view(batch, seqLen, numKHeads, headKDim);
                    kSlice = mixedConv.get(Index.Ellipsis, kSpan);
                    key = kSlice.view(batch, seqLen, numKHeads, headKDim);
                    vSlice = mixedConv.get(Index.Ellipsis, vSpan);
                    value = vSlice.view(batch, seqLen, numVHeads, headVDim);
                }

                int rep = numVHeads / numKHeads;
                if (rep > 1) {
                    Tensor qRep = GatedDeltaRule.repeatHeads(query, rep);
                    Tensor kRep = GatedDeltaRule.repeatHeads(key, rep);
                    if (qRep != query) {
                        query.close();
                    }
                    if (kRep != key) {
                        key.close();
                    }
                    query = qRep;
                    key = kRep;
                }
            }
            if (profile) {
                // Conv + QKV split + head-repeat share this bucket (all pre-gate).
                smile.llm.engine.DecodeForwardProfile.addDeltaConv(System.nanoTime() - tMark);
                tMark = System.nanoTime();
            }

            Tensor g = null;
            Tensor beta = null;
            if (fusedCore == null) {
                ensureFloatCaches();
                Tensor[] gates = fusedGates != null
                        ? fusedGates
                        : GatedDeltaRule.computeBetaAndDecayGate(a, b, aLogF, dtBiasF);
                g = gates[0];
                beta = gates[1];
                a.close();
                b.close();
            }
            if (profile) {
                smile.llm.engine.DecodeForwardProfile.addDeltaGate(System.nanoTime() - tMark);
                tMark = System.nanoTime();
            }

            Tensor core;
            if (fusedCore != null) {
                core = fusedCore;
            } else if (verifyLoop) {
                core = forwardVerifyWindowLoop(mixed, convState, g, beta, batch, seqLen);
                mixed.close();
                mixedRaw.close();
                g.close();
                beta.close();
            } else {
                Tensor initState = statePool != null ? statePool.activeRecurrent(linearLayerId) : null;
                smile.util.Tuple2<Tensor, Tensor> result;
                if (batchedVerify) {
                    Tensor[] ckpts = new Tensor[seqLen];
                    for (int t = 0; t < seqLen; t++) {
                        ckpts[t] = statePool.speculativeRecurrentSlot(t + 1, linearLayerId);
                    }
                    Tensor core0 = GatedDeltaRule.recurrentGatedDeltaRuleCkpt(
                            query, key, value, g, beta, initState, true, ckpts);
                    if (core0 == null) {
                        // Conv state was already rolled for this window, so the per-position
                        // loop can no longer be used; a silent fallback would leave stale
                        // checkpoints for partial-accept restore.
                        throw new IllegalStateException(
                                "fused checkpoint-emitting recurrent rule failed for verify window");
                    }
                    result = new smile.util.Tuple2<>(core0, null);
                } else {
                    result = GatedDeltaRule.recurrentGatedDeltaRule(
                            query, key, value, g, beta, initState, statePool != null, true);
                }
                query.close();
                key.close();
                value.close();
                if (qSlice != null) {
                    qSlice.close();
                }
                if (kSlice != null) {
                    kSlice.close();
                }
                if (vSlice != null) {
                    vSlice.close();
                }
                g.close();
                beta.close();
                if (mixedConv != null) {
                    mixedConv.close();
                }
                if (mixedConvBase != null) {
                    mixedConvBase.close();
                }

                core = result._1();
                if (statePool != null && result._2() != null) {
                    Tensor dest = statePool.activeRecurrent(linearLayerId);
                    dest.put_(result._2(), Index.Colon, Index.Colon, Index.Colon, Index.Colon);
                    result._2().close();
                }
            }
            if (profile) {
                smile.llm.engine.DecodeForwardProfile.addDeltaRecurrent(System.nanoTime() - tMark);
                tMark = System.nanoTime();
            }

            core = core.reshape(batch * seqLen * numVHeads, headVDim);
            Tensor zFlat = z.reshape(batch * seqLen * numVHeads, headVDim);
            Tensor gated = norm.forward(core, zFlat);
            gated = gated.view(batch, seqLen, valueDim);
            Tensor out = outProj.forward(gated);
            if (profile) {
                smile.llm.engine.DecodeForwardProfile.addDeltaOut(System.nanoTime() - tMark);
                smile.llm.engine.DecodeForwardProfile.addLinearAttn(System.nanoTime() - t0);
            }
            if (tpGroup != null && tpGroup.tpSize() > 1) {
                tpGroup.allReduceSumInPlace(tpRank, out);
            }
            out.promoteToParent();
            return out;
        } finally {
            Tensor.pop();
        }
    }

    /**
     * Two-launch fused verify window (conv+split+recurrent, per-position checkpoints).
     *
     * @return core {@code [B, S, Hv, Dv]}, or {@code null} when unsupported (state untouched).
     */
    private Tensor fusedVerifyWindow(Tensor mixedRaw, Tensor convState, Tensor[] gates,
                                     int batch, int seqLen) {
        // Layout/dtype validation lives in the native op (returns null, state untouched).
        Tensor recState = statePool.activeRecurrent(linearLayerId);
        if (recState == null) {
            return null;
        }
        Tensor[] convCk = new Tensor[seqLen];
        Tensor[] recCk = new Tensor[seqLen];
        for (int t = 0; t < seqLen; t++) {
            convCk[t] = statePool.speculativeConvSlot(t + 1, linearLayerId);
            recCk[t] = statePool.speculativeRecurrentSlot(t + 1, linearLayerId);
        }
        recState.detachFromScopes();
        return smile.torch.Native.gatedDeltaVerifyWindow(
                mixedRaw, convState, conv1dWeight, gates[0], gates[1], recState,
                numKHeads, numVHeads, headKDim, headVDim, true, convCk, recCk);
    }

    /** Whether the whole-window checkpointing fast path applies to this verify forward. */
    private boolean canBatchVerifyWindow(Tensor x, int batch, int seqLen) {
        if (!x.device().isCUDA() || !smile.torch.Native.hasRecurrentGatedDeltaRuleCkpt()
                || seqLen > 8 || statePool == null
                || !statePool.hasSpeculativeCheckpoints(seqLen + 1, batch)) {
            return false;
        }
        for (int t = 0; t < seqLen; t++) {
            Tensor ck = statePool.speculativeRecurrentSlot(t + 1, linearLayerId);
            if (ck == null || ck.dtype() != smile.deep.tensor.ScalarType.Float) {
                return false;
            }
            Tensor cc = statePool.speculativeConvSlot(t + 1, linearLayerId);
            Tensor live = statePool.activeConv(linearLayerId);
            if ((cc == null) != (live == null) || (cc != null && cc.dtype() != x.dtype())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Writes the conv left-context checkpoints for slots {@code 1..S}: slot
     * {@code t+1} holds the last {@code K-1} <em>pre-conv</em> inputs after
     * consuming window positions {@code 0..t}, i.e. columns
     * {@code [t+1, t+K)} of {@code concat(oldState, mixed)}. Must run before
     * the window's conv update rolls {@code convState} in place.
     *
     * @param mixed     pre-conv QKV projection {@code [B, C, S]}.
     * @param convState live conv left-context {@code [B, C, K-1]} (not yet rolled).
     */
    private void saveWindowConvCheckpoints(Tensor mixed, Tensor convState, int batch, int seqLen) {
        if (convState == null) {
            return;
        }
        int keep = (int) convState.shape()[2]; // K-1
        try (var rows = Index.slice(0, batch)) {
            for (int t = 0; t < seqLen; t++) {
                Tensor full = statePool.speculativeConvSlot(t + 1, linearLayerId);
                try (Tensor dst = full.get(rows)) {
                    int consumed = t + 1;
                    int fromMixed = Math.min(consumed, keep);
                    int fromOld = keep - fromMixed;
                    if (fromOld > 0) {
                        try (var srcSpan = Index.slice(consumed, keep);
                             var dstSpan = Index.slice(0, fromOld);
                             Tensor src = convState.get(Index.Colon, Index.Colon, srcSpan)) {
                            dst.put_(src, Index.Colon, Index.Colon, dstSpan);
                        }
                    }
                    try (var srcSpan = Index.slice(consumed - fromMixed, consumed);
                         var dstSpan = Index.slice(fromOld, keep);
                         Tensor src = mixed.get(Index.Colon, Index.Colon, srcSpan)) {
                        dst.put_(src, Index.Colon, Index.Colon, dstSpan);
                    }
                }
            }
        }
    }

    /**
     * Per-position verify-window loop: invokes the exact per-step kernels
     * decode's {@code S=1} path already uses ({@code causalConv1dUpdateSplitQkv}
     * once per position for the fused conv+QKV-split+head-repeat, then
     * {@code recurrentGatedDeltaRule} with {@code S=1}) instead of the batched
     * whole-window call, and retains a checkpoint after every position so a
     * partial MTP accept can restore directly rather than re-forwarding.
     *
     * <p>{@code g}/{@code beta} are the already-computed whole-window decay
     * gate / input gate ({@code [B,S,H]}) — elementwise per position, so no
     * per-step recomputation is needed; only DeltaNet's own recurrent state
     * carries across positions.
     *
     * @param mixed     pre-conv QKV projection {@code [B, C, S]}.
     * @param convState conv left-context {@code [B, C, K-1]} (rolled in place).
     * @param g         decay gate {@code [B, S, H]}.
     * @param beta      input gate {@code [B, S, H]}.
     * @param batch     batch size.
     * @param seqLen    verify-window length ({@code numDrafts + 1}).
     * @return mixer core output {@code [B, S, H, Dv]} (pre-gate/pre-norm).
     */
    private Tensor forwardVerifyWindowLoop(Tensor mixed, Tensor convState, Tensor g, Tensor beta,
                                           int batch, int seqLen) {
        // recurrentGatedDeltaRule casts its per-step output back to the
        // compute dtype (query/mixed's dtype) internally — g/beta stay float
        // for numerically-stable gating, so coreOut must match mixed's dtype,
        // not beta's, or outProj's later matmul dtype-mismatches (bf16 vs float).
        var opts = new Tensor.Options()
                .device(mixed.device()).dtype(mixed.dtype()).requireGradients(false);
        Tensor coreOut = Tensor.zeros(opts, batch, seqLen, numVHeads, headVDim);
        Tensor initState = statePool.activeRecurrent(linearLayerId);
        for (int t = 0; t < seqLen; t++) {
            AutoScope stepScope = new AutoScope();
            Tensor.push(stepScope);
            try (var tSpan = Index.slice(t, t + 1)) {
                Tensor mixedStep = mixed.get(Index.Colon, Index.Colon, tSpan);
                Tensor[] qkv = GatedDeltaRule.causalConv1dUpdateSplitQkv(
                        mixedStep, convState, conv1dWeight, numKHeads, numVHeads, headKDim, headVDim);
                mixedStep.close();
                Tensor qStep = qkv[0];
                Tensor kStep = qkv[1];
                Tensor vStep = qkv[2];

                // A scalar Index.of(t) squeezes the dim (PyTorch indexing
                // semantics); Index.slice keeps it as size 1 ([B,1,H]) — matches
                // the [B,S,H] shape recurrentGatedDeltaRule expects with S=1.
                Tensor gStep = g.get(Index.Colon, tSpan);
                Tensor betaStep = beta.get(Index.Colon, tSpan);

                var stepResult = GatedDeltaRule.recurrentGatedDeltaRule(
                        qStep, kStep, vStep, gStep, betaStep, initState, true, true);
                qStep.close();
                kStep.close();
                vStep.close();
                gStep.close();
                betaStep.close();

                Tensor stepCore = stepResult._1();
                if (stepResult._2() != null) {
                    Tensor dest = statePool.activeRecurrent(linearLayerId);
                    dest.put_(stepResult._2(), Index.Colon, Index.Colon, Index.Colon, Index.Colon);
                    stepResult._2().close();
                }
                coreOut.put_(stepCore, Index.Colon, tSpan, Index.Colon, Index.Colon);
                stepCore.close();
                statePool.saveCheckpointForLayer(t + 1, linearLayerId);
            } finally {
                Tensor.pop();
            }
        }
        return coreOut;
    }
}
