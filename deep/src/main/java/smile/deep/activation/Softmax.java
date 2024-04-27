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

import org.bytedeco.pytorch.global.torch;
import smile.deep.tensor.Tensor;

/**
 * Softmax activation function.
 *
 * @author Haifeng Li
 */
public class Softmax extends ActivationFunction {
    /**
     * Constructor.
     */
    public Softmax() {
        super("Softmax", false);
    }

    @Override
    public Tensor forward(Tensor x) {
        return new Tensor(torch.softmax(x.asTorch(), 1));
    }
}
