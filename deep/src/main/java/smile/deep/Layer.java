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

import org.bytedeco.javacpp.LongPointer;
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
    protected Module net;

    /**
     * Registers this layer to a neural network.
     * @param name the name of this layer.
     * @param net the neural network that this layer is registered to.
     */
    public abstract void register(String name, Module net);

    /**
     * Forward propagation (or forward pass) through the layer.
     *
     * @param x the input tensor.
     * @return the output tensor.
     */
    public abstract Tensor forward(Tensor x);

    /**
     * Returns a linear (fully connected) layer.
     * @param in the number of input features.
     * @param out the number of output features.
     * @return a linear layer.
     */
    public static Layer linear(int in, int out) {
        return new Layer() {
            LinearImpl layer;

            @Override
            public void register(String name, Module net) {
                this.net = net;
                this.layer = net.register_module(name, new LinearImpl(in, out));
            }

            @Override
            public Tensor forward(Tensor x) {
                if (x.dim() > 1) {
                    x = x.reshape(x.size(0), in);
                }
                return layer.forward(x);
            }
        };
    }

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
            public void register(String name, Module net) {
                this.net = net;
                this.layer = net.register_module(name, new LinearImpl(in, out));
            }

            @Override
            public Tensor forward(Tensor x) {
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
            public void register(String name, Module net) {
                this.net = net;
                this.layer = net.register_module(name, new LinearImpl(in, out));
            }

            @Override
            public Tensor forward(Tensor x) {
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
            public void register(String name, Module net) {
                this.net = net;
                this.layer = net.register_module(name, new LinearImpl(in, out));
            }

            @Override
            public Tensor forward(Tensor x) {
                if (x.dim() > 1) {
                    x = x.reshape(x.size(0), in);
                }
                x = torch.log_softmax(layer.forward(x), 1);
                return x;
            }
        };
    }

    /**
     * Returns a convolutional  layer.
     * @param in the number of input channels.
     * @param out the number of output features.
     * @param size the window size.
     * @param pool the max pooling kernel size. Sets it to zero to skip pooling.
     * @return a convolutional layer.
     */
    public static Layer conv2d(int in, int out, int size, int pool) {
        return new Layer() {
            Conv2dImpl layer;

            @Override
            public void register(String name, Module net) {
                LongPointer p = new LongPointer(1).put(size);
                this.net = net;
                this.layer = net.register_module(name, new Conv2dImpl(in, out, p));
            }

            @Override
            public Tensor forward(Tensor x) {
                x = torch.relu(layer.forward(x));
                if (pool > 0) {
                    x = torch.max_pool2d(x, pool, pool);
                }
                return x;
            }
        };
    }

    /**
     * Returns a convolutional  layer.
     * @param in the number of input channels.
     * @param out the number of output channels/features.
     * @param size the window size.
     * @param pool the max pooling kernel size. Sets it to zero to skip pooling.
     * @param stride controls the stride for the cross-correlation.
     * @param dilation controls the spacing between the kernel points.
     *                It is harder to describe, but this link has a nice
     *                visualization of what dilation does.
     *
     * @param groups controls the connections between inputs and outputs.
     *              The in channels and out channels must both be divisible by groups.
     * @return a convolutional layer.
     */
    public static Layer conv2d(int in, int out, int size, int pool, int stride, int dilation, int groups, boolean bias) {
        return new Layer() {
            Conv2dImpl layer;

            @Override
            public void register(String name, Module net) {
                LongPointer p = new LongPointer(1).put(size);
                Conv2dOptions options = new Conv2dOptions(in, out, p);
                options.stride().put(stride);
                options.dilation().put(dilation);
                options.groups().put(groups);
                options.bias().put(bias);

                this.net = net;
                this.layer = net.register_module(name, new Conv2dImpl(options));
            }

            @Override
            public Tensor forward(Tensor x) {
                x = torch.relu(layer.forward(x));
                if (pool > 0) {
                    x = torch.max_pool2d(x, pool, pool);
                }
                return x;
            }
        };
    }

    /**
     * Returns a max pooling layer that reduces a tensor by combining cells,
     * and assigning the maximum value of the input cells to the output cell.
     * @param size the window/kernel size.
     * @return a max pooling layer.
     */
    public static Layer maxPool2d(int size) {
        return new Layer() {
            MaxPool2dImpl layer;

            @Override
            public void register(String name, Module net) {
                LongPointer p = new LongPointer(1).put(size);
                this.net = net;
                this.layer = net.register_module(name, new MaxPool2dImpl(p));
            }

            @Override
            public Tensor forward(Tensor x) {
                return layer.forward(x);
            }
        };
    }

    /**
     * Returns a normalization layer that re-centers and normalizes the output
     * of one layer before feeding it to another. Centering and scaling the
     * intermediate tensors has a number of beneficial effects, such as allowing
     * higher learning rates without exploding/vanishing gradients.
     * @param in the number of input features.
     * @return a normalization layer.
     */
    public static Layer batchNorm1d(int in) {
        return new Layer() {
            BatchNorm1dImpl layer;

            @Override
            public void register(String name, Module net) {
                LongPointer p = new LongPointer(1).put(in);
                this.net = net;
                this.layer = net.register_module(name, new BatchNorm1dImpl(p));
            }

            @Override
            public Tensor forward(Tensor x) {
                return layer.forward(x);
            }
        };
    }

    /**
     * Returns a normalization layer that re-centers and normalizes the output
     * of one layer before feeding it to another. Centering and scaling the
     * intermediate tensors has a number of beneficial effects, such as allowing
     * higher learning rates without exploding/vanishing gradients.
     * @param in the number of input features.
     * @param eps a value added to the denominator for numerical stability.
     * @param momentum the value used for the running_mean and running_var
     *                computation. Can be set to 0.0 for cumulative moving average
     *                (i.e. simple average).
     * @param affine when set to true, this layer has learnable affine parameters.
     * @return a normalization layer.
     */
    public static Layer batchNorm1d(int in, double eps, double momentum, boolean affine) {
        return new Layer() {
            BatchNorm1dImpl layer;

            @Override
            public void register(String name, Module net) {
                LongPointer p = new LongPointer(1).put(in);
                BatchNormOptions options = new BatchNormOptions(p);
                options.eps().put(eps);
                if (momentum > 0.0) options.momentum().put(momentum);
                options.affine().put(affine);
                this.net = net;
                this.layer = net.register_module(name, new BatchNorm1dImpl(options));
            }

            @Override
            public Tensor forward(Tensor x) {
                return layer.forward(x);
            }
        };
    }

    /**
     * Returns a normalization layer that re-centers and normalizes the output
     * of one layer before feeding it to another. Centering and scaling the
     * intermediate tensors has a number of beneficial effects, such as allowing
     * higher learning rates without exploding/vanishing gradients.
     * @param in the number of input features.
     * @return a normalization layer.
     */
    public static Layer batchNorm2d(int in) {
        return new Layer() {
            BatchNorm2dImpl layer;

            @Override
            public void register(String name, Module net) {
                LongPointer p = new LongPointer(1).put(in);
                this.net = net;
                this.layer = net.register_module(name, new BatchNorm2dImpl(p));
            }

            @Override
            public Tensor forward(Tensor x) {
                return layer.forward(x);
            }
        };
    }

    /**
     * Returns a normalization layer that re-centers and normalizes the output
     * of one layer before feeding it to another. Centering and scaling the
     * intermediate tensors has a number of beneficial effects, such as allowing
     * higher learning rates without exploding/vanishing gradients.
     * @param in the number of input features.
     * @param eps a value added to the denominator for numerical stability.
     * @param momentum the value used for the running_mean and running_var
     *                computation. Can be set to 0.0 for cumulative moving average
     *                (i.e. simple average).
     * @param affine when set to true, this layer has learnable affine parameters.
     * @return a normalization layer.
     */
    public static Layer batchNorm2d(int in, double eps, double momentum, boolean affine) {
        return new Layer() {
            BatchNorm2dImpl layer;

            @Override
            public void register(String name, Module net) {
                LongPointer p = new LongPointer(1).put(in);
                BatchNormOptions options = new BatchNormOptions(p);
                options.eps().put(eps);
                if (momentum > 0.0) options.momentum().put(momentum);
                options.affine().put(affine);
                this.net = net;
                this.layer = net.register_module(name, new BatchNorm2dImpl(options));
            }

            @Override
            public Tensor forward(Tensor x) {
                return layer.forward(x);
            }
        };
    }

    /**
     * Returns a dropout layer that randomly zeroes some of the elements of
     * the input tensor with probability p during training. The zeroed
     * elements are chosen independently for each forward call and are
     * sampled from a Bernoulli distribution. Each channel will be zeroed
     * out independently on every forward call.
     *
     * This has proven to be an effective technique for regularization
     * and preventing the co-adaptation of neurons as described in the
     * paper "Improving Neural Networks by Preventing Co-adaptation
     * of Feature Detectors".
     *
     * @param p the probability of an element to be zeroed.
     * @return a dropout layer.
     */
    public static Layer dropout(double p) {
        return new Layer() {
            DropoutImpl layer;

            @Override
            public void register(String name, Module net) {
                this.net = net;
                this.layer = net.register_module(name, new DropoutImpl(p));
            }

            @Override
            public Tensor forward(Tensor x) {
                return layer.forward(x);
            }
        };
    }

    /**
     * Returns an embedding layer that is a simple lookup table that stores
     * embeddings of a fixed dictionary and size.
     *
     * This layer is often used to store word embeddings and retrieve them
     * using indices. The input to the module is a list of indices, and the
     * output is the corresponding word embeddings.
     *
     * @param numTokens the size of the dictionary of embeddings.
     * @param dim the size of each embedding vector.
     * @return a dropout layer.
     */
    public static Layer embedding(int numTokens, int dim) {
        return embedding(numTokens, dim, 1.0);
    }

    /**
     * Returns an embedding layer that is a simple lookup table that stores
     * embeddings of a fixed dictionary and size.
     *
     * This layer is often used to store word embeddings and retrieve them
     * using indices. The input to the module is a list of indices, and the
     * output is the corresponding word embeddings.
     *
     * @param numTokens the size of the dictionary of embeddings.
     * @param dim the size of each embedding vector.
     * @param alpha optional scaling factor.
     * @return a dropout layer.
     */
    public static Layer embedding(int numTokens, int dim, double alpha) {
        return new Layer() {
            EmbeddingImpl layer;
            Scalar scaler = new Scalar(alpha);

            @Override
            public void register(String name, Module net) {
                this.net = net;
                this.layer = net.register_module(name, new EmbeddingImpl(numTokens, dim));
            }

            @Override
            public Tensor forward(Tensor x) {
                Tensor output = layer.forward(x);
                if (alpha != 1.0) {
                    output.mul_(scaler);
                }
                return output;
            }
        };
    }
}
