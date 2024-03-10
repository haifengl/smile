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
package smile.deep;

import org.bytedeco.pytorch.*;
import org.bytedeco.pytorch.Module;
import org.bytedeco.pytorch.Tensor;
import org.bytedeco.pytorch.global.torch;

/**
 * A layer in the neural network.
 *
 * @author Haifeng Li
 */
public abstract class Layer {
    /** The neural network layer. */
    Module module;

    /**
     * Constructor.
     * @param module the layer module.
     */
    Layer(Module module) {
        this.module = module;
    }

    /**
     * Forward propagation (or forward pass) through the layer.
     *
     * @param x the input tensor.
     * @return the output tensor.
     */
    abstract Tensor forward(Tensor x);

    /**
     * Returns a ReLU layer.
     * @param in the number of input features.
     * @param out the number of output features.
     * @return a ReLU layer.
     */
    public static Layer relu(int in, int out) {
        return relu(in, out, 0.0);
    }

    /**
     * Returns a ReLU layer.
     * @param in the number of input features.
     * @param out the number of output features.
     * @param dropout the optional dropout probability.
     * @return a ReLU layer.
     */
    public static Layer relu(int in, int out, double dropout) {
        final LinearImpl linear = new LinearImpl(in, out);
        return new Layer(linear) {
            @Override
            Tensor forward(Tensor x) {
                if (x.dim() > 1) {
                    x = x.reshape(x.size(0), in);
                }
                x = torch.relu(linear.forward(x));
                if (dropout > 0.0) {
                    x = torch.dropout(x, dropout, module.is_training());
                }
                return x;
            }
        };
    }

    /**
     * Returns a softmax layer.
     * @param in the number of input features.
     * @param out the number of output features.
     * @return a softmax layer.
     */
    public static Layer softmax(int in, int out) {
        final LinearImpl linear = new LinearImpl(in, out);
        return new Layer(linear) {
            @Override
            Tensor forward(Tensor x) {
                if (x.dim() > 1) {
                    x = x.reshape(x.size(0), in);
                }
                x = torch.softmax(linear.forward(x), 1);
                return x;
            }
        };
    }

    /**
     * Returns a log softmax layer.
     * @param in the number of input features.
     * @param out the number of output features.
     * @return a log softmax layer.
     */
    public static Layer logSoftmax(int in, int out) {
        final LinearImpl linear = new LinearImpl(in, out);
        return new Layer(linear) {
            @Override
            Tensor forward(Tensor x) {
                if (x.dim() > 1) {
                    x = x.reshape(x.size(0), in);
                }
                x = torch.log_softmax(linear.forward(x), 1);
                return x;
            }
        };
    }
}
