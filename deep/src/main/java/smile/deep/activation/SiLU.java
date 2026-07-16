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

import static smile.torch.smile_torch_h.smile_torch_silu;
import static smile.torch.smile_torch_h.smile_torch_silu_;

/**
 * Sigmoid Linear Unit activation function.
 *
 * @author Haifeng Li
 */
public class SiLU extends ActivationFunction {
    /**
     * Constructor.
     * @param inplace true if the operation executes in-place.
     */
    public SiLU(boolean inplace) {
        super("SiLU", inplace);
    }

    @Override
    public Tensor forward(Tensor input) {
        if (inplace) {
            smile_torch_silu_(input.handle());
            return input;
        }
        return new Tensor(smile_torch_silu(input.handle()));
    }
}
