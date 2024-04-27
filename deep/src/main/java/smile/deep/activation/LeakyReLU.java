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
 * Sigmoid Linear Unit activation function.
 *
 * @author Haifeng Li
 */
public class LeakyReLU implements ActivationFunction {
    /** Controls the angle of the negative slope. */
    final double negativeSlop;

    /**
     * Constructor.
     */
    public LeakyReLU() {
        this(0.01);
    }

    /**
     * Constructor.
     * @param negativeSlope Controls the angle of the negative slope, which is
     *                     used for negative input values.
     */
    public LeakyReLU(double negativeSlope) {
        this.negativeSlop = negativeSlope;
    }

    @Override
    public String name() {
        return String.format("LeakyReLU(%.4f)", negativeSlop);
    }

    @Override
    public Tensor apply(Tensor x) {
        return torch.leaky_relu(x, negativeSlop, false);
    }
}
