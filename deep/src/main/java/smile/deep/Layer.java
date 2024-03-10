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
    /** The neural network that this layer is registered to. */
    Module net;

    /**
     * Registers this layer to a neural network.
     * @param name the name of this layer.
     * @param net the neural network that this layer is registered to.
     */
    abstract void register(String name, Module net);

    /**
     * Loads this layer from a checkpoint.
     * @param name the name of this layer.
     * @param dict the named module dictionary of the neural network.
     */
    abstract void load(String name, StringSharedModuleDict dict);

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
        return new Layer() {
            LinearImpl layer;

            @Override
            void register(String name, Module net) {
                this.net = net;
                this.layer = net.register_module(name, new LinearImpl(in, out));
            }

            @Override
            void load(String name, StringSharedModuleDict dict) {
                this.layer = (LinearImpl) dict.get(name);
            }

            @Override
            Tensor forward(Tensor x) {
                if (x.dim() > 1) {
                    x = x.reshape(x.size(0), in);
                }
                x = torch.relu(layer.forward(x));
                if (dropout > 0.0) {
                    x = torch.dropout(x, dropout, net.is_training());
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
        return new Layer() {
            LinearImpl layer;

            @Override
            void register(String name, Module net) {
                this.net = net;
                this.layer = net.register_module(name, new LinearImpl(in, out));
            }

            @Override
            void load(String name, StringSharedModuleDict dict) {
                this.layer = (LinearImpl) dict.get(name);
            }

            @Override
            Tensor forward(Tensor x) {
                if (x.dim() > 1) {
                    x = x.reshape(x.size(0), in);
                }
                x = torch.softmax(layer.forward(x), 1);
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
        return new Layer() {
            LinearImpl layer;

            @Override
            void register(String name, Module net) {
                this.net = net;
                this.layer = net.register_module(name, new LinearImpl(in, out));
            }

            @Override
            void load(String name, StringSharedModuleDict dict) {
                this.layer = (LinearImpl) dict.get(name);
            }

            @Override
            Tensor forward(Tensor x) {
                if (x.dim() > 1) {
                    x = x.reshape(x.size(0), in);
                }
                x = torch.log_softmax(layer.forward(x), 1);
                return x;
            }
        };
    }
}
