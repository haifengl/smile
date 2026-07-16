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
package smile.deep.activation;

import smile.deep.tensor.Tensor;

import static smile.torch.smile_torch_h.smile_torch_softshrink;

/**
 * Soft shrink activation function.
 *
 * @author Haifeng Li
 */
public class SoftShrink extends ActivationFunction {
    /** The lambda value for the soft shrinkage formulation. */
    final double lambda;

    /**
     * Constructor.
     */
    public SoftShrink() {
        this(0.5);
    }

    /**
     * Constructor.
     * @param lambda the lambda value for the soft shrinkage formulation.
     */
    public SoftShrink(double lambda) {
        super(String.format("SoftShrink(%.4f)", lambda), false);
        if (lambda < 0.0) {
            throw new IllegalArgumentException("Invalid lambda: " + lambda);
        }
        this.lambda = lambda;
    }

    @Override
    public Tensor forward(Tensor x) {
        return new Tensor(smile_torch_softshrink(x.handle(), lambda));
    }
}
