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
import smile.deep.layer.LinearLayer;
import smile.deep.tensor.Tensor;
import smile.llm.quant.LinearOp;
import smile.llm.quant.MxFp8Layer;
import smile.torch.Native;
import smile.util.AutoScope;

import static smile.torch.Native.check;
import static smile.torch.smile_torch_h.smile_module_create;
import static smile.torch.smile_torch_h.smile_module_free;
import static smile.torch.smile_torch_h.smile_module_register_module;

/**
 * Gated feed-forward with the {@code swigluoai} activation, used for the dense
 * layers, the shared expert and every routed expert:
 * {@code w2(glu * (up + 1))} where {@code glu = gate * sigmoid(alpha * gate)},
 * {@code gate} is clamped above at {@code limit} and {@code up} to {@code [-limit, limit]}.
 *
 * <p>Parameters are registered as {@code w1} (gate), {@code w2} (down) and
 * {@code w3} (up) to match {@link smile.llm.transformer.FeedForward}.
 *
 * @author Haifeng Li
 */
public class MiniMaxMlp {
    LinearOp w1, w2, w3;
    final double alpha;
    final double limit;
    private static final Sigmoid SIGMOID = new Sigmoid(false);
    final MemorySegment module;

    /**
     * Constructor.
     *
     * @param dim          model dimension.
     * @param intermediate hidden width.
     * @param alpha        sigmoid slope.
     * @param limit        clamp bound.
     */
    public MiniMaxMlp(int dim, int intermediate, double alpha, double limit) {
        this(dim, intermediate, alpha, limit, MiniMaxWeightMode.DENSE);
    }

    /**
     * Constructor.
     *
     * @param dim          model dimension.
     * @param intermediate hidden width.
     * @param alpha        sigmoid slope.
     * @param limit        clamp bound.
     * @param mode         weight mode; quantized layers are loaded through {@link #collect}.
     */
    public MiniMaxMlp(int dim, int intermediate, double alpha, double limit, MiniMaxWeightMode mode) {
        this.alpha = alpha;
        this.limit = limit;
        if (mode.quantized()) {
            this.w1 = mode.newLinear(dim, intermediate);
            this.w2 = mode.newLinear(intermediate, dim);
            this.w3 = mode.newLinear(dim, intermediate);
            this.module = check(smile_module_create(MemorySegment.NULL));
        } else {
            LinearLayer l1 = new LinearLayer(dim, intermediate, false);
            LinearLayer l2 = new LinearLayer(intermediate, dim, false);
            LinearLayer l3 = new LinearLayer(dim, intermediate, false);
            this.w1 = l1;
            this.w2 = l2;
            this.w3 = l3;
            try (Arena arena = Arena.ofConfined()) {
                this.module = check(smile_module_create(MemorySegment.NULL));
                smile_module_register_module(module, arena.allocateFrom("w1"), l1.module());
                smile_module_register_module(module, arena.allocateFrom("w2"), l2.module());
                smile_module_register_module(module, arena.allocateFrom("w3"), l3.module());
            }
        }
        MemorySegment m = this.module;
        Native.CLEANER.register(this, () -> smile_module_free(m));
    }

    /**
     * Adds this FFN's FP8-resident projections to {@code out}, keyed by their SMILE weight names.
     *
     * @param prefix name prefix, e.g. {@code layers.3.mlp.}.
     * @param out    destination map.
     */
    void collect(String prefix, java.util.Map<String, MxFp8Layer> out) {
        if (w1 instanceof MxFp8Layer a) out.put(prefix + "w1.weight", a);
        if (w2 instanceof MxFp8Layer b) out.put(prefix + "w2.weight", b);
        if (w3 instanceof MxFp8Layer c) out.put(prefix + "w3.weight", c);
    }

    /**
     * Returns the PyTorch module handle.
     * @return module handle.
     */
    public MemorySegment module() {
        return module;
    }

    /**
     * Forward pass.
     *
     * @param x input {@code [..., D]}.
     * @return output with the same shape.
     */
    public Tensor forward(Tensor x) {
        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        try {
            Tensor out = w2.forward(swiglu(w1.forward(x), w3.forward(x), alpha, limit));
            out.promoteToParent();
            return out;
        } finally {
            Tensor.pop();
        }
    }

    /**
     * The {@code swigluoai} activation: {@code gate * sigmoid(alpha * gate) * (up + 1)} with
     * {@code gate} clamped above at {@code limit} and {@code up} to {@code [-limit, limit]}.
     * Intermediates belong to the caller's scope.
     *
     * @param gate  gate projection.
     * @param up    up projection.
     * @param alpha sigmoid slope.
     * @param limit clamp bound.
     * @return the activated tensor.
     */
    static Tensor swiglu(Tensor gate, Tensor up, double alpha, double limit) {
        Tensor g = gate.clamp(Double.NEGATIVE_INFINITY, limit);
        Tensor u = up.clamp(-limit, limit);
        Tensor glu = g.mul(SIGMOID.forward(g.mul(alpha)));
        return glu.mul(u.add(1.0));
    }
}
