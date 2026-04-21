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

import org.bytedeco.pytorch.ELUOptions;
import org.bytedeco.pytorch.Scalar;
import org.bytedeco.pytorch.global.torch;
import smile.deep.tensor.Tensor;

/**
 * Exponential Linear Unit (ELU) activation function.
 *
 * <p>ELU is defined as:
 * <ul>
 *   <li>{@code x} if {@code x > 0}</li>
 *   <li>{@code alpha * (exp(x) - 1)} if {@code x <= 0}</li>
 * </ul>
 *
 * <p>Unlike ReLU, ELU produces negative outputs, pushing the mean activation
 * closer to zero, which can speed up training.
 *
 * @see <a href="https://arxiv.org/abs/1511.07289">Fast and Accurate Deep Network
 *      Learning by Exponential Linear Units (ELUs)</a>
 * @author Haifeng Li
 */
public class ELU extends ActivationFunction {
    /** The alpha value for negative inputs. */
    final double alpha;
    /** The alpha scalar used in the native call. */
    final Scalar alphaScalar;

    /** Scale scalar (always 1.0 in standard ELU). */
    private static final Scalar SCALE = new Scalar(1.0);

    /**
     * Constructor with default alpha = 1.0.
     */
    public ELU() {
        this(1.0, false);
    }

    /**
     * Constructor.
     * @param alpha the alpha value for the ELU formulation. Must be non-negative.
     * @param inplace true if the operation executes in-place.
     */
    public ELU(double alpha, boolean inplace) {
        super(String.format("ELU(%.4f)", alpha), inplace);
        if (alpha < 0.0) {
            throw new IllegalArgumentException("Invalid alpha: " + alpha);
        }
        this.alpha = alpha;
        this.alphaScalar = new Scalar(alpha);
    }

    /**
     * Returns the alpha value.
     * @return the alpha value.
     */
    public double alpha() {
        return alpha;
    }

    @Override
    public Tensor forward(Tensor input) {
        var x = input.asTorch();
        if (inplace) {
            torch.elu_(x, alphaScalar, SCALE, SCALE);
            return input;
        } else {
            var opts = new ELUOptions();
            opts.alpha().put(alpha);
            return new Tensor(torch.elu(x, opts));
        }
    }
}
