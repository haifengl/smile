/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
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
package smile.deep.layer;

import org.bytedeco.pytorch.LinearImpl;
import org.bytedeco.pytorch.Module;
import smile.deep.tensor.Tensor;

/**
 * A fully connected layer with nonlinear activation function.
 *
 * @author Haifeng Li
 */
public class FullyConnectedLayer implements Layer {
    private final int in;
    private final LinearImpl module;

    /**
     * Constructor.
     * @param in the number of input features.
     * @param out the number of output features.
     */
    public FullyConnectedLayer(int in, int out) {
        this.in = in;
        this.module = new LinearImpl(in, out);
    }

    @Override
    public Module asTorch() {
        return module;
    }

    @Override
    public Tensor forward(Tensor input) {
        org.bytedeco.pytorch.Tensor x = input.asTorch();
        if (x.dim() > 1) {
            x = x.reshape(x.size(0), in);
        }

        return new Tensor(module.forward(x));
    }
}
