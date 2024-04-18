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
import org.bytedeco.pytorch.global.torch;
import smile.deep.activation.ActivationFunction;
import smile.deep.tensor.Tensor;

/**
 * A fully connected layer with nonlinear activation function.
 *
 * @author Haifeng Li
 */
public class FullyConnectedLayer implements Layer {
    /** The number of input features. */
    private final int in;
    /** The number of output features. */
    private final int out;
    /** The optional activation function. */
    private final ActivationFunction activation;
    /** The optional dropout probability. */
    private final double dropout;
    /** Implementation. */
    private final LinearImpl module;

    /**
     * Constructor.
     * @param in the number of input features.
     * @param out the number of output features.
     */
    public FullyConnectedLayer(int in, int out) {
        this(in, out, null, 0.0);
    }

    /**
     * Constructor.
     * @param in the number of input features.
     * @param out the number of output features.
     * @param activation the non-linear activation function.
     */
    public FullyConnectedLayer(int in, int out, ActivationFunction activation) {
        this(in, out, activation, 0.0);
    }

    /**
     * Constructor.
     * @param in the number of input features.
     * @param out the number of output features.
     * @param activation the non-linear activation function.
     * @param dropout the optional dropout probability.
     */
    public FullyConnectedLayer(int in, int out, ActivationFunction activation, double dropout) {
        this.in = in;
        this.out = out;
        this.activation = activation;
        this.dropout = dropout;
        this.module = new LinearImpl(in, out);
    }

    @Override
    public void register(String name, Module parent) {
        parent.register_module(name, module);
    }

    @Override
    public Tensor forward(Tensor input) {
        org.bytedeco.pytorch.Tensor x = input.asTorch();
        if (x.dim() > 1) {
            x = x.reshape(x.size(0), in);
        }
        x = module.forward(x);
        if (activation != null) {
            x = activation.apply(x);
        }
        if (dropout > 0.0) {
            x = torch.dropout(x, dropout, module.is_training());
        }
        return new Tensor(x);
    }
}
