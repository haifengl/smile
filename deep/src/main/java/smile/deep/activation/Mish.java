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

import org.bytedeco.pytorch.global.torch;
import smile.deep.tensor.Tensor;

/**
 * Mish activation function.
 *
 * <p>Mish is a self-regularized non-monotonic activation function:
 * <pre>
 *   mish(x) = x * tanh(softplus(x))
 *           = x * tanh(ln(1 + exp(x)))
 * </pre>
 *
 * <p>Mish is used in many modern object detection models such as YOLOv4/v5.
 *
 * @see <a href="https://arxiv.org/abs/1908.08681">Mish: A Self Regularized
 *      Non-Monotonic Activation Function</a>
 * @author Haifeng Li
 */
public class Mish extends ActivationFunction {
    /**
     * Constructor.
     */
    public Mish() {
        this(false);
    }

    /**
     * Constructor.
     * @param inplace true if the operation executes in-place.
     */
    public Mish(boolean inplace) {
        super("Mish", inplace);
    }

    @Override
    public Tensor forward(Tensor input) {
        var x = input.asTorch();
        if (inplace) {
            torch.mish_(x);
            return input;
        } else {
            return new Tensor(torch.mish(x));
        }
    }
}

