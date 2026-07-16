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

import static smile.torch.smile_torch_h.smile_torch_elu;
import static smile.torch.smile_torch_h.smile_torch_elu_;

/**
 * Exponential Linear Unit activation function.
 *
 * @author Haifeng Li
 */
public class ELU extends ActivationFunction {
    /** The alpha value for the ELU formulation. */
    final double alpha;

    /**
     * Constructor.
     */
    public ELU() {
        this(1.0, false);
    }

    /**
     * Constructor.
     * @param alpha the alpha value for the ELU formulation.
     * @param inplace true if the operation executes in-place.
     */
    public ELU(double alpha, boolean inplace) {
        super(String.format("ELU(%.4f)", alpha), inplace);
        if (alpha < 0.0) {
            throw new IllegalArgumentException("Invalid alpha: " + alpha);
        }
        this.alpha = alpha;
    }

    /**
     * Returns the alpha value for the ELU formulation.
     * @return the alpha value.
     */
    public double alpha() {
        return alpha;
    }

    @Override
    public Tensor forward(Tensor input) {
        if (inplace) {
            smile_torch_elu_(input.handle(), alpha, 1.0, 1.0);
            return input;
        }
        return new Tensor(smile_torch_elu(input.handle(), alpha));
    }
}
