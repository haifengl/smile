/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.deep.activation;

import org.bytedeco.pytorch.global.torch;
import smile.deep.tensor.Tensor;

/**
 * Gaussian Error Linear Unit activation function.
 *
 * @author Haifeng Li
 */
public class GELU extends ActivationFunction {
    /**
     * Constructor.
     * @param inplace true if the operation executes in-place.
     */
    public GELU(boolean inplace) {
        super("GELU", inplace);
    }

    @Override
    public Tensor forward(Tensor input) {
        var x = input.asTorch();
        if (!module.is_training() && inplace) {
            torch.gelu_(x);
            return input;
        } else {
            return new Tensor(torch.gelu(x));
        }
    }
}
