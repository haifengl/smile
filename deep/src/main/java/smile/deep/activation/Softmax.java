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

import static smile.torch.smile_torch_h.smile_torch_softmax;

/**
 * Softmax activation function.
 *
 * @author Haifeng Li
 */
public class Softmax extends ActivationFunction {
    /** The dimension along which softmax will be computed. */
    final int dim;

    /**
     * Constructor.
     */
    public Softmax() {
        this(1);
    }

    /**
     * Constructor.
     * @param dim the dimension along which softmax will be computed.
     */
    public Softmax(int dim) {
        super("Softmax", false);
        this.dim = dim;
    }

    @Override
    public Tensor forward(Tensor x) {
        return new Tensor(smile_torch_softmax(x.handle(), dim));
    }
}
