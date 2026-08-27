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
     *
     * @param layerId   stack layer index.
     * @param args      model args.
     * @param statePool DeltaNet state pool (linear layers; may be null).
     * @param shard     local head / FFN shard description, or {@code null} for full width.
     * @param tpGroup   tensor-parallel group, or {@code null} for single-device.
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

    /** @return stack layer index. */
    public int layerId() {
        return layerId;
    }

    /** @return {@code full_attention} or {@code linear_attention}. */
    public String layerType() {
        return layerType;
    }

    /** @return gated full attention, or {@code null} for DeltaNet layers. */
    public GatedAttention selfAttn() {
        return selfAttn;
    }

    /** @return DeltaNet mixer, or {@code null} for full-attention layers. */
    public GatedDeltaNet linearAttn() {
        return linearAttn;
    }

    /** @return SwiGLU feed-forward. */
    public FeedForward feedForward() {
        return feedForward;
    }

    /**
     * Forward pass.
     * @param x        hidden states.
     * @param startPos cache start position (full-attn).
     * @param cos      partial RoPE cosines for this window (full-attn).
     * @param sin      partial RoPE sines for this window (full-attn).
     * @param mask     causal attention mask (full-attn).
     * @return block output.
     */
    public Tensor forward(Tensor x, int startPos, Tensor cos, Tensor sin, Tensor mask) {
        int batch = (int) x.shape()[0];
        int[] positions = new int[batch];
        java.util.Arrays.fill(positions, startPos);
        return forward(x, positions, cos, sin, mask);
    }

    /**
     * Forward with per-row cache write positions (full-attn layers).
     *
     * @param x         hidden states.
     * @param positions absolute write position per batch row.
     * @param cos       partial RoPE cosines.
     * @param sin       partial RoPE sines.
     * @param mask      causal mask, or {@code null}.
     * @return block output.
     */
    public Tensor forward(Tensor x, int[] positions, Tensor cos, Tensor sin, Tensor mask) {
        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        try {
            Tensor residual = x;
            Tensor h = inputNorm.forward(x);
            Tensor mixed = selfAttn != null
                    ? selfAttn.forward(h, positions, cos, sin, mask)
                    : linearAttn.forward(h);
            h.close();
            Tensor afterAttn = residual.add(mixed);
            mixed.close();

            residual = afterAttn;
            Tensor ffIn = postNorm.forward(afterAttn);
            Tensor ffOut = feedForward.forward(ffIn);
            ffIn.close();
            Tensor out = residual.add(ffOut);
            ffOut.close();
            afterAttn.close();
            out.promoteToParent();
            return out;
        } finally {
            Tensor.pop();
        }
    }
}
