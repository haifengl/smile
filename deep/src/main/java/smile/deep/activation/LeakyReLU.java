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

import org.bytedeco.pytorch.Scalar;
import org.bytedeco.pytorch.global.torch;
import smile.deep.tensor.Tensor;

/**
 * Sigmoid Linear Unit activation function.
 *
 * @author Haifeng Li
 */
public class LeakyReLU extends ActivationFunction {
    /** Controls the angle of the negative slope. */
    final Scalar negativeSlope;

    /**
     * Constructor.
     */
    public LeakyReLU() {
        this(0.01, false);
    }

    /**
     * Constructor.
     * @param negativeSlope Controls the angle of the negative slope, which is
     *                     used for negative input values.
     * @param inplace true if the operation executes in-place.
     */
    public LeakyReLU(double negativeSlope, boolean inplace) {
        super(String.format("LeakyReLU(%.4f)", negativeSlope), inplace);
        this.negativeSlope = new Scalar(negativeSlope);
    }

    @Override
    public Tensor forward(Tensor input) {
        var x = input.asTorch();
        if (!module.is_training() && inplace) {
            torch.leaky_relu_(x, negativeSlope);
            return input;
        } else {
            return new Tensor(torch.leaky_relu(x, negativeSlope));
        }
    }
}
