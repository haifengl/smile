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
package smile.llm.qwen;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import smile.deep.activation.Sigmoid;
import smile.deep.layer.LinearLayer;
import smile.deep.tensor.Index;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
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

    final LinearLayer inProjQkv;
    final LinearLayer inProjZ;
    final LinearLayer inProjB;
    final LinearLayer inProjA;
    final LinearLayer outProj;
    /** Depthwise conv weights {@code [convDim, kernel]}. */
    final Tensor conv1dWeight;
    /** {@code log(A)} per value head. */
    final Tensor aLog;
    /** Discretization bias per value head. */
    final Tensor dtBias;
    final QwenRMSNormGated norm;
    final Sigmoid sigmoid = new Sigmoid(false);

    DeltaNetStatePool statePool;

    /**
     * Constructor.
     *
     * @param args           model hyperparameters.
     * @param linearLayerId  ordinal among linear-attention layers.
     * @param statePool      shared DeltaNet state pool.
     */
    public GatedDeltaNet(QwenModelArgs args, int linearLayerId, DeltaNetStatePool statePool) {
        this.hiddenSize = args.dim();
        this.numKHeads = args.linearNumKeyHeads();
        this.numVHeads = args.linearNumValueHeads();
        this.headKDim = args.linearKeyHeadDim();
        this.headVDim = args.linearValueHeadDim();
        this.keyDim = headKDim * numKHeads;
        this.valueDim = headVDim * numVHeads;
        this.convKernel = args.linearConvKernelDim();
        this.convDim = args.linearConvDim();
        this.linearLayerId = linearLayerId;
        this.statePool = statePool;

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
            smile_module_register_module(module, arena.allocateFrom("in_proj_qkv"), inProjQkv.module());
            smile_module_register_module(module, arena.allocateFrom("in_proj_z"), inProjZ.module());
            smile_module_register_module(module, arena.allocateFrom("in_proj_b"), inProjB.module());
            smile_module_register_module(module, arena.allocateFrom("in_proj_a"), inProjA.module());
            smile_module_register_module(module, arena.allocateFrom("out_proj"), outProj.module());
            smile_module_register_module(module, arena.allocateFrom("norm"), norm.module());
            smile_module_register_parameter(module, arena.allocateFrom("conv1d.weight"), conv1dWeight.handle());
            smile_module_register_parameter(module, arena.allocateFrom("A_log"), aLog.handle());
            smile_module_register_parameter(module, arena.allocateFrom("dt_bias"), dtBias.handle());
        }
        MemorySegment m = this.module;
        Native.CLEANER.register(this, () -> smile_module_free(m));
    }

    public MemorySegment module() {
        return module;
    }

    void setStatePool(DeltaNetStatePool pool) {
        this.statePool = pool;
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

        try (var scope = new AutoScope()) {
            Tensor mixed = scope.add(inProjQkv.forward(x).transpose(1, 2)); // [B, C, S]
            Tensor z = scope.add(inProjZ.forward(x).view(batch, seqLen, numVHeads, headVDim));
            Tensor b = scope.add(inProjB.forward(x));
            Tensor a = scope.add(inProjA.forward(x));

            Tensor convState = statePool != null ? statePool.conv(linearLayerId) : null;
            Tensor mixedConv;
            if (decode && convState != null) {
                mixedConv = scope.add(GatedDeltaRule.causalConv1dUpdate(mixed, convState, conv1dWeight));
            } else {
                mixedConv = scope.add(GatedDeltaRule.causalConv1dPrefill(mixed, convState, conv1dWeight));
            }
            mixedConv = scope.add(mixedConv.transpose(1, 2)); // [B, S, C]

            try (var qSpan = Index.slice(0, keyDim);
                 var kSpan = Index.slice(keyDim, 2 * keyDim);
                 var vSpan = Index.slice(2 * keyDim, 2 * keyDim + valueDim)) {
                Tensor query = scope.add(mixedConv.get(Index.Ellipsis, qSpan)
                        .view(batch, seqLen, numKHeads, headKDim));
                Tensor key = scope.add(mixedConv.get(Index.Ellipsis, kSpan)
                        .view(batch, seqLen, numKHeads, headKDim));
                Tensor value = scope.add(mixedConv.get(Index.Ellipsis, vSpan)
                        .view(batch, seqLen, numVHeads, headVDim));

                int rep = numVHeads / numKHeads;
                if (rep > 1) {
                    query = scope.add(repeatHeads(query, rep));
                    key = scope.add(repeatHeads(key, rep));
                }

                Tensor beta = scope.add(sigmoid.forward(b));
                // g = -exp(A_log) * softplus(a + dt_bias)
                Tensor aLogF = scope.add(aLog.to(ScalarType.Float));
                Tensor dt = scope.add(dtBias.to(ScalarType.Float));
                Tensor aF = scope.add(a.to(ScalarType.Float));
                Tensor soft = scope.add(GatedDeltaRule.softplus(aF.add(dt)));
                Tensor g = scope.add(aLogF.exp().neg().mul(soft));

                Tensor initState = null;
                if (decode && statePool != null) {
                    initState = statePool.recurrent(linearLayerId);
                }

                var result = GatedDeltaRule.recurrentGatedDeltaRule(
                        query, key, value, g, beta, initState, statePool != null, true);
                Tensor core = scope.add(result._1());
                if (statePool != null && result._2() != null) {
                    Tensor dest = statePool.recurrent(linearLayerId);
                    dest.put_(result._2(), Index.Colon, Index.Colon, Index.Colon, Index.Colon);
                    result._2().close();
                }

                // Gated RMSNorm: weight * rms(y) * silu(z)
                core = scope.add(core.reshape(batch * seqLen * numVHeads, headVDim));
                Tensor zFlat = scope.add(z.reshape(batch * seqLen * numVHeads, headVDim));
                Tensor gated = scope.add(norm.forward(core, zFlat));
                gated = scope.add(gated.view(batch, seqLen, valueDim));
                return outProj.forward(gated);
            }
        }
    }

    /** Repeats K heads along the head axis to match V head count. */
    private static Tensor repeatHeads(Tensor x, int rep) {
        // x: [B, S, Hk, D] → [B, S, Hk*rep, D]
        long[] s = x.shape();
        try (Tensor u = x.unsqueeze(3);
             Tensor e = u.expand(s[0], s[1], s[2], rep, s[3])) {
            return e.reshape(s[0], s[1], s[2] * rep, s[3]);
        }
    }
}
