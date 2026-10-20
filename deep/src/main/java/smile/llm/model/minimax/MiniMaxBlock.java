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
import smile.deep.tensor.Tensor;
import smile.llm.model.qwen.QwenRMSNorm;
import smile.torch.Native;
import smile.util.AutoScope;

import static smile.torch.Native.check;
import static smile.torch.smile_torch_h.smile_module_create;
import static smile.torch.smile_torch_h.smile_module_free;
import static smile.torch.smile_torch_h.smile_module_register_module;

/**
 * One MiniMax-M3 decoder block: pre-norm, GQA/MSA attention, residual, post-norm,
 * dense FFN or MoE, residual. Norms use the Gemma {@code 1 + weight} convention.
 *
 * @author Haifeng Li
 */
public class MiniMaxBlock {
    final int layerId;
    final MemorySegment module;
    final QwenRMSNorm inputNorm;
    final QwenRMSNorm postNorm;
    final MiniMaxAttention selfAttn;
    /** Dense FFN; {@code null} on MoE layers. */
    final MiniMaxMlp mlp;
    /** Mixture of experts; {@code null} on dense layers. */
    final MiniMaxMoE moe;

    /**
     * Constructor.
     *
     * @param layerId decoder layer index.
     * @param args    model hyperparameters.
     */
    public MiniMaxBlock(int layerId, MiniMaxModelArgs args) {
        this(layerId, args, MiniMaxShard.single(), null);
    }

    /**
     * Expert-parallel constructor.
     *
     * @param layerId decoder layer index.
     * @param args    model hyperparameters.
     * @param shard   this rank's slice of the routed experts.
     * @param group   all-reduce group (needed when {@code shard.size() > 1}).
     */
    public MiniMaxBlock(int layerId, MiniMaxModelArgs args, MiniMaxShard shard,
                        smile.llm.parallel.TensorParallelGroup group) {
        this.layerId = layerId;
        this.inputNorm = new QwenRMSNorm(args.dim(), args.normEps());
        this.postNorm = new QwenRMSNorm(args.dim(), args.normEps());
        this.selfAttn = new MiniMaxAttention(args, layerId);
        if (args.isMoe(layerId)) {
            this.moe = new MiniMaxMoE(args, shard, group);
            this.mlp = null;
        } else {
            this.mlp = new MiniMaxMlp(args.dim(), args.denseIntermediateSize(),
                    args.swigluAlpha(), args.swigluLimit(), args.weightMode());
            this.moe = null;
        }
        try (Arena arena = Arena.ofConfined()) {
            this.module = check(smile_module_create(MemorySegment.NULL));
            smile_module_register_module(module, arena.allocateFrom("input_layernorm"), inputNorm.module());
            smile_module_register_module(module, arena.allocateFrom("post_attention_layernorm"), postNorm.module());
            smile_module_register_module(module, arena.allocateFrom("self_attn"), selfAttn.module());
            if (moe != null) {
                smile_module_register_module(module, arena.allocateFrom("moe"), moe.module());
            } else {
                smile_module_register_module(module, arena.allocateFrom("mlp"), mlp.module());
            }
        }
        MemorySegment m = this.module;
        Native.CLEANER.register(this, () -> smile_module_free(m));
    }

    /**
     * Returns the decoder layer index.
     * @return layer index.
     */
    public int layerId() {
        return layerId;
    }

    /**
     * Returns the attention mixer.
     * @return attention.
     */
    public MiniMaxAttention selfAttn() {
        return selfAttn;
    }

    /**
     * Returns the dense FFN.
     * @return the FFN, or {@code null} for MoE layers.
     */
    public MiniMaxMlp mlp() {
        return mlp;
    }

    /**
     * Returns the mixture of experts.
     * @return the MoE, or {@code null} for dense layers.
     */
    public MiniMaxMoE moe() {
        return moe;
    }

    /**
     * Adds this block's FP8-resident projections to {@code out}, keyed by their SMILE weight names.
     *
     * @param out destination map.
     */
    void collect(java.util.Map<String, smile.llm.quant.MxFp8Layer> out) {
        String prefix = "layers." + layerId + ".";
        selfAttn.collect(prefix + "self_attn.", out);
        if (mlp != null) {
            mlp.collect(prefix + "mlp.", out);
        }
        if (moe != null) {
            moe.collect(prefix + "moe.", out);
        }
    }

    /**
     * Forward pass.
     *
     * @param x         hidden states {@code [B, S, D]}.
     * @param positions KV write position per row.
     * @param cos       RoPE cosines.
     * @param sin       RoPE sines.
     * @param mask      causal mask for multi-token windows, or {@code null}.
     * @return block output.
     */
    public Tensor forward(Tensor x, int[] positions, Tensor cos, Tensor sin, Tensor mask) {
        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        try {
            Tensor attn = selfAttn.forward(inputNorm.forward(x), positions, cos, sin, mask);
            Tensor afterAttn = x.add(attn);
            Tensor ffIn = postNorm.forward(afterAttn);
            Tensor ff = moe != null ? moe.forward(ffIn) : mlp.forward(ffIn);
            Tensor out = afterAttn.add(ff);
            out.promoteToParent();
            return out;
        } finally {
            Tensor.pop();
        }
    }
}
