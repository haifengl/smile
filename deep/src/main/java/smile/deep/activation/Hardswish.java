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
 * Hard Swish activation function.
 *
 * <p>Hard Swish is a computationally efficient approximation of Swish/SiLU:
 * <pre>
 *   hardswish(x) = x * hardsigmoid(x)
 *                = x * ReLU6(x + 3) / 6
 * </pre>
 *
 * <p>It is used in MobileNetV3 and EfficientNetV2 to reduce computational cost
 * compared to sigmoid-based Swish.
 *
 * @see <a href="https://arxiv.org/abs/1905.02244">Searching for MobileNetV3</a>
 * @author Haifeng Li
 */
public class Hardswish extends ActivationFunction {
    /**
     * Constructor.
     */
    public Hardswish() {
        this(false);
    }

    /**
     * Constructor.
     * @param inplace true if the operation executes in-place.
     */
    public Hardswish(boolean inplace) {
        super("Hardswish", inplace);
    }

    @Override
    public Tensor forward(Tensor input) {
        var x = input.asTorch();
        if (inplace) {
            torch.hardswish_(x);
            return input;
        } else {
            return new Tensor(torch.hardswish(x));
        }
    }
}

