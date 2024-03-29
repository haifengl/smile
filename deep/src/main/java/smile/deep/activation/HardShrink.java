/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.deep.activation;

import org.bytedeco.pytorch.Tensor;
import org.bytedeco.pytorch.global.torch;

/**
 * Hard Shrink activation function.
 *
 * @author Haifeng Li
 */
public class HardShrink implements ActivationFunction {
    /** The lambda value in the formulation. */
    final double lambda;

    /** Constructor. */
    public HardShrink() {
        this(0.5);
    }

    /**
     * Constructor.
     * @param lambda The lambda value in the formulation.
     */
    public HardShrink(double lambda) {
        if (lambda < 0.0) {
            throw new IllegalArgumentException("Invalid lambda: " + lambda);
        }
        this.lambda = lambda;
    }

    @Override
    public String name() {
        return String.format("HardShrink(%.4f)", lambda);
    }

    @Override
    public Tensor apply(Tensor x) {
        return torch.hardshrink(x, lambda);
    }
}
