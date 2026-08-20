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
import smile.deep.tensor.Tensor;
import smile.llm.parallel.TensorParallelGroup;
import smile.llm.parallel.TensorShardSpec;
import smile.llm.transformer.FeedForward;
import smile.torch.Native;
import smile.util.AutoScope;

import static smile.torch.Native.check;
import static smile.torch.smile_torch_h.smile_module_create;
import static smile.torch.smile_torch_h.smile_module_free;
import static smile.torch.smile_torch_h.smile_module_register_module;

/**
 * One hybrid decoder block: pre-norm → mixer (DeltaNet or gated attn) → residual
 * → post-norm → SwiGLU FFN → residual.
 *
 * @author Haifeng Li
 */
public class QwenBlock {
    final int layerId;
    final String layerType;
    final MemorySegment module;
    final QwenRMSNorm inputNorm;
    final QwenRMSNorm postNorm;
    final FeedForward feedForward;
    final GatedAttention selfAttn;
    final GatedDeltaNet linearAttn;

    /**
     * Constructor.
     *
     * @param layerId   stack layer index.
     * @param args      model args.
     * @param statePool DeltaNet state pool (linear layers; may be null).
     */
    public QwenBlock(int layerId, QwenModelArgs args, DeltaNetStatePool statePool) {
        this(layerId, args, statePool, null, null);
    }

    /**
     * Tensor-parallel constructor.
     */
    public QwenBlock(int layerId, QwenModelArgs args, DeltaNetStatePool statePool,
                     TensorShardSpec shard, TensorParallelGroup tpGroup) {
        this.layerId = layerId;
        this.layerType = args.layerTypes()[layerId];
        this.inputNorm = new QwenRMSNorm(args.dim(), args.normEps());
        this.postNorm = new QwenRMSNorm(args.dim(), args.normEps());
        this.feedForward = new FeedForward(args.dim(), args.intermediateSize(), shard, tpGroup);

        if (QwenModelArgs.FULL_ATTENTION.equals(layerType)) {
            int kvId = args.fullAttentionLayerIndex(layerId);
            if (shard != null && shard.tpSize() > 1) {
                this.selfAttn = GatedAttention.forShard(
                        args.dim(), args.headDim(), args.rotaryDim(), args.normEps(),
                        kvId, shard, tpGroup);
            } else {
                this.selfAttn = new GatedAttention(
                        args.dim(), args.numHeads(), args.numKvHeads(), args.headDim(),
                        args.rotaryDim(), args.normEps(), kvId);
            }
            this.linearAttn = null;
        } else if (QwenModelArgs.LINEAR_ATTENTION.equals(layerType)) {
            int linId = args.linearAttentionLayerIndex(layerId);
            this.linearAttn = new GatedDeltaNet(args, linId, statePool, shard, tpGroup);
            this.selfAttn = null;
        } else {
            throw new IllegalArgumentException("Unknown layer type: " + layerType);
        }

        try (Arena arena = Arena.ofConfined()) {
            this.module = check(smile_module_create(MemorySegment.NULL));
            smile_module_register_module(module, arena.allocateFrom("input_layernorm"), inputNorm.module());
            smile_module_register_module(module, arena.allocateFrom("post_attention_layernorm"), postNorm.module());
            smile_module_register_module(module, arena.allocateFrom("mlp"), feedForward.module());
            if (selfAttn != null) {
                smile_module_register_module(module, arena.allocateFrom("self_attn"), selfAttn.module());
            } else {
                smile_module_register_module(module, arena.allocateFrom("linear_attn"), linearAttn.module());
            }
        }
        MemorySegment m = this.module;
        Native.CLEANER.register(this, () -> smile_module_free(m));
    }

    /**
     * Forward pass.
     * @param x        hidden states.
     * @param startPos cache start position (full-attn).
     * @param cis      partial RoPE frequencies (full-attn).
     * @param mask     causal attention mask (full-attn).
     * @return block output.
     */
    public Tensor forward(Tensor x, int startPos, Tensor cis, Tensor mask) {
        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        try {
            Tensor residual = x;
            Tensor h = inputNorm.forward(x);
            Tensor mixed = selfAttn != null
                    ? selfAttn.forward(h, startPos, cis, mask)
                    : linearAttn.forward(h);
            h = residual.add(mixed);

            residual = h;
            Tensor ffIn = postNorm.forward(h);
            Tensor ffOut = feedForward.forward(ffIn);
            Tensor out = residual.add(ffOut);
            scope.remove(out);
            return out;
        } finally {
            Tensor.pop();
        }
    }
}
