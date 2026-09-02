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
package smile.llm.transformer;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import smile.deep.activation.SiLU;
import smile.deep.layer.LinearLayer;
import smile.deep.tensor.Tensor;
import smile.llm.parallel.ColumnParallelLinear;
import smile.llm.parallel.RowParallelLinear;
import smile.llm.parallel.TensorParallelGroup;
import smile.llm.parallel.TensorShardSpec;
import smile.llm.quant.LinearOp;
import smile.torch.Native;

import static smile.torch.Native.check;
import static smile.torch.smile_torch_h.smile_module_create;
import static smile.torch.smile_torch_h.smile_module_free;
import static smile.torch.smile_torch_h.smile_module_register_module;

/**
 * Feedforward layer in Transformer. It has two linear transformations and
 * an intermediate SiLU activation function.
 *
 * <p>Under tensor parallelism, {@code w1}/{@code w3} are column-parallel and
 * {@code w2} is row-parallel (all-reduce after the down-projection).
 *
 * @author Haifeng Li
 */
public class FeedForward {
    LinearOp w1, w2, w3;
    final SiLU silu;
    final MemorySegment module;
    final TensorParallelGroup tpGroup;
    final int tpRank;

    /**
     * Constructor with an explicit intermediate size, as provided by HuggingFace {@code config.json}.
     * @param dim the dimension of input tensor.
     * @param intermediateSize the FFN hidden dimension size, used directly without further computation.
     */
    public FeedForward(int dim, int intermediateSize) {
        this(dim, intermediateSize, null, null);
    }

    /**
     * Tensor-parallel constructor.
     *
     * @param dim                model dimension.
     * @param globalIntermediate full (unsharded) intermediate size.
     * @param shard              local shard sizes ({@code intermediateSize} already divided).
     * @param tpGroup            TP group for row-parallel all-reduce; {@code null} when tp=1.
     */
    public FeedForward(int dim, int globalIntermediate, TensorShardSpec shard, TensorParallelGroup tpGroup) {
        this.silu = new SiLU(true);
        if (shard == null || shard.tpSize() <= 1) {
            this.tpGroup = null;
            this.tpRank = 0;
            int hidden = shard != null ? shard.intermediateSize() : globalIntermediate;
            this.w1 = new LinearLayer(dim, hidden, false);
            this.w2 = new LinearLayer(hidden, dim, false);
            this.w3 = new LinearLayer(dim, hidden, false);
        } else {
            this.tpGroup = tpGroup;
            this.tpRank = shard.tpRank();
            var col1 = new ColumnParallelLinear(dim, globalIntermediate, false, shard.tpSize(), shard.tpRank());
            var col3 = new ColumnParallelLinear(dim, globalIntermediate, false, shard.tpSize(), shard.tpRank());
            var row2 = new RowParallelLinear(globalIntermediate, dim, false, shard.tpSize(), shard.tpRank());
            this.w1 = col1.linearOp();
            this.w3 = col3.linearOp();
            this.w2 = row2.linearOp();
        }

        try (Arena arena = Arena.ofConfined()) {
            this.module = check(smile_module_create(MemorySegment.NULL));
            registerDense(module, arena, "w1", w1);
            registerDense(module, arena, "w2", w2);
            registerDense(module, arena, "w3", w3);
        }
        MemorySegment m = this.module;
        Native.CLEANER.register(this, () -> smile_module_free(m));
    }

    /**
     * Constructor.
     * @param dim the dimension of input tensor.
     * @param hiddenDim the dimension of hidden layer. First, hiddenDim is set
     *                 to two-thirds of the provided hiddenDim value. If ffnDimMultiplier
     *                 is provided, hiddenDim is further multiplied by this value.
     *                  The hiddenDim is then adjusted to ensure it is a multiple of multipleOf.
     * @param multipleOf make SwiGLU hidden layer size multiple of large power of 2.
     * @param ffnDimMultiplier the multiplier for the hidden dimension of the feedforward layers.
     */
    public FeedForward(int dim, int hiddenDim, int multipleOf, Double ffnDimMultiplier) {
        hiddenDim = (int) (2 * hiddenDim / 3.0);
        if (ffnDimMultiplier != null) {
            hiddenDim = (int) (ffnDimMultiplier * hiddenDim);
        }
        hiddenDim = multipleOf * ((hiddenDim + multipleOf - 1) / multipleOf);
        this.tpGroup = null;
        this.tpRank = 0;
        this.w1 = new LinearLayer(dim, hiddenDim, false);
        this.w2 = new LinearLayer(hiddenDim, dim, false);
        this.w3 = new LinearLayer(dim, hiddenDim, false);
        this.silu = new SiLU(true);

        try (Arena arena = Arena.ofConfined()) {
            this.module = check(smile_module_create(MemorySegment.NULL));
            registerDense(module, arena, "w1", w1);
            registerDense(module, arena, "w2", w2);
            registerDense(module, arena, "w3", w3);
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
     * Replaces FFN linears with quantized ops (already sharded/packed for TP).
     * Frees dense {@link LinearLayer} shells so empty GPU weights are released.
     */
    public void replaceLinears(LinearOp w1, LinearOp w2, LinearOp w3) {
        if (w1 == null || w2 == null || w3 == null) {
            throw new IllegalArgumentException("w1/w2/w3 required");
        }
        LinearOp old1 = this.w1;
        LinearOp old2 = this.w2;
        LinearOp old3 = this.w3;
        this.w1 = w1;
        this.w2 = w2;
        this.w3 = w3;
        smile.llm.quant.DenseLinearRelease.unregisterAndClose(module, "w1", old1);
        smile.llm.quant.DenseLinearRelease.unregisterAndClose(module, "w2", old2);
        smile.llm.quant.DenseLinearRelease.unregisterAndClose(module, "w3", old3);
    }

    /**
     * Returns the PyTorch module handle.
     * @return module handle.
     */
    public MemorySegment module() {
        return module;
    }

    /**
     * Feed forward.
     * @param x the input tensor.
     * @return the output tensor.
     */
    public Tensor forward(Tensor x) {
        // SiLU may be in-place and return w1x; do not list it as a second
        // try-with resource (would double-close the same handle).
        boolean profile = smile.llm.engine.DecodeForwardProfile.enabled();
        long t0 = profile ? System.nanoTime() : 0L;
        try (var w3x = w3.forward(x);
             var w1x = w1.forward(x)) {
            Tensor siluOut = silu.forward(w1x);
            Tensor out = w2.forward(siluOut.mul_(w3x));
            if (profile) {
                smile.llm.engine.DecodeForwardProfile.addMlp(System.nanoTime() - t0);
            }
            if (tpGroup != null) {
                tpGroup.allReduceSumInPlace(tpRank, out);
            }
            return out;
        }
    }
}
