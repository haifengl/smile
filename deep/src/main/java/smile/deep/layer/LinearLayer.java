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
package smile.deep.layer;

import org.bytedeco.pytorch.LinearImpl;
import org.bytedeco.pytorch.LinearOptions;
import org.bytedeco.pytorch.Module;
import smile.deep.tensor.Tensor;

/**
 * A fully connected linear layer.
 *
 * @author Haifeng Li
 */
public class LinearLayer implements Layer {
    private final LinearImpl module;

    /**
     * Constructor.
     * @param in the number of input features.
     * @param out the number of output features.
     */
    public LinearLayer(int in, int out) {
        this(in, out, true);
    }

    /**
     * Constructor.
     * @param in the number of input features.
     * @param out the number of output features.
     * @param bias If false, the layer will not learn an additive bias.
     */
    public LinearLayer(int in, int out, boolean bias) {
        var options = new LinearOptions(in, out);
        options.bias().put(bias);
        this.module = new LinearImpl(options);
    }

    @Override
    public Module asTorch() {
        return module;
    }

    @Override
    public Tensor forward(Tensor input) {
        return new Tensor(module.forward(input.asTorch()));
    }
}
