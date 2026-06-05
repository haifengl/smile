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
package smile.deep.layer;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import smile.deep.tensor.Native;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;

import static smile.deep.tensor.Native.check;
import static smile.torch.smile_torch_h.*;

/**
 * Root Mean Square Layer Normalization. RMSNorm regularizes the summed inputs
 * to a neuron in one layer according to root mean square (RMS), giving the
 * model re-scaling invariance property and implicit learning rate adaptation
 * ability. RMSNorm is computationally simpler and thus more efficient than LayerNorm.
 *
 * @author Haifeng Li
 */
public class RMSNormLayer implements Layer {
    /** The neural network module ({@code ST_Module}). */
    private final MemorySegment module;
    /** The term added to the denominator to improve numerical stability. */
    private final double eps;
    /** The learnable per-element scale parameter. */
    private final Tensor weight;

    /**
     * Constructor.
     * @param dim the layer size.
     */
    public RMSNormLayer(int dim) {
        this(dim, 1E-6);
    }

    /**
     * Constructor.
     * @param dim the layer size.
     * @param eps the term added to the denominator to improve numerical stability.
     */
    public RMSNormLayer(int dim, double eps) {
        this.eps = eps;
        this.weight = Tensor.ones(dim);
        try (Arena arena = Arena.ofConfined()) {
            this.module = check(smile_module_create(arena.allocateFrom("RMSNorm")));
            smile_module_register_parameter(module, arena.allocateFrom("weight"), weight.handle());
        }
        MemorySegment m = this.module;
        Native.CLEANER.register(this, () -> smile_module_free(m));
    }

    @Override
    public Tensor forward(Tensor input) {
        Tensor x = input.to(ScalarType.Float32);
        Tensor output = x.mul(x.pow(2).mean(-1, true).add_(eps).rsqrt_()).to(input.dtype());
        return output.mul_(weight);
    }

    @Override
    public MemorySegment asModule() {
        return module;
    }
}
