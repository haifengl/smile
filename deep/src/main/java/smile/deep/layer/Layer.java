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

import org.bytedeco.javacpp.LongPointer;
import org.bytedeco.pytorch.*;
import org.bytedeco.pytorch.Module;
import org.bytedeco.pytorch.global.torch;
import smile.deep.tensor.Tensor;

/**
 * A layer in the neural network.
 *
 * @author Haifeng Li
 */
public interface Layer {
    /**
     * Registers this layer to a neural network.
     * @param name the name of this layer.
     * @param parent the parent layer that this layer is registered to.
     */
    void register(String name, Layer parent);

    /**
     * Forward propagation (or forward pass) through the layer.
     *
     * @param input the input tensor.
     * @return the output tensor.
     */
    Tensor forward(Tensor input);

    /** Returns the PyTorch Module object. */
    Module asTorch();

    /**
     * Returns a linear (fully connected) layer.
     * @param in the number of input features.
     * @param out the number of output features.
     * @return a linear layer.
     */
    static LinearLayer linear(int in, int out) {
        return new LinearLayer(in, out);
    }

    /**
     * Returns a ReLU layer.
     * @param in the number of input features.
     * @param out the number of output features.
     * @return a ReLU layer.
     */
    static ReLULayer relu(int in, int out) {
        return relu(in, out, 0.0);
    }

    /**
     * Returns a ReLU layer.
     * @param in the number of input features.
     * @param out the number of output features.
     * @param dropout the optional dropout probability.
     * @return a ReLU layer.
     */
    static ReLULayer relu(int in, int out, double dropout) {
        return new ReLULayer(in, out, dropout);
    }

    /**
     * Returns a softmax layer.
     * @param in the number of input features.
     * @param out the number of output features.
     * @return a softmax layer.
     */
    static SoftmaxLayer softmax(int in, int out) {
        return new SoftmaxLayer(in, out);
    }

    /**
     * Returns a log softmax layer.
     * @param in the number of input features.
     * @param out the number of output features.
     * @return a log softmax layer.
     */
    static LogSoftmaxLayer logSoftmax(int in, int out) {
        return new LogSoftmaxLayer(in, out);
    }

    /**
     * Returns a convolutional layer.
     * @param in the number of input channels.
     * @param out the number of output features.
     * @param size the window size.
     * @param pool the max pooling kernel size. Sets it to zero to skip pooling.
     * @return a convolutional layer.
     */
    static Layer conv2d(int in, int out, int size, int pool) {
        return new Layer() {
            Conv2dImpl module;

            @Override
            public void register(String name, Layer parent) {
                LongPointer p = new LongPointer(1).put(size);
                this.module = parent.asTorch().register_module(name, new Conv2dImpl(in, out, p));
            }

            @Override
            public Tensor forward(Tensor input) {
                org.bytedeco.pytorch.Tensor x = input.asTorch();
                x = torch.relu(module.forward(x));
                if (pool > 0) {
                    x = torch.max_pool2d(x, pool, pool);
                }
                return Tensor.of(x);
            }

            @Override
            public Conv2dImpl asTorch() {
                return module;
            }
        };
    }

    /**
     * Returns a convolutional layer.
     * @param in the number of input channels.
     * @param out the number of output channels/features.
     * @param size the window size.
     * @param stride controls the stride for the cross-correlation.
     * @param dilation controls the spacing between the kernel points.
     *                It is harder to describe, but this link has a nice
     *                visualization of what dilation does.
     * @param groups controls the connections between inputs and outputs.
     *              The in channels and out channels must both be divisible by groups.
     * @param bias If true, adds a learnable bias to the output.
     * @param pool the max pooling kernel size. Sets it to zero to skip pooling.
     * @return a convolutional layer.
     */
    static Conv2dLayer conv2d(int in, int out, int size, int stride, int dilation, int groups, boolean bias, int pool) {
        return new Conv2dLayer(in, out, size, stride, dilation, groups, bias, pool);
    }

    /**
     * Returns a max pooling layer that reduces a tensor by combining cells,
     * and assigning the maximum value of the input cells to the output cell.
     * @param size the window/kernel size.
     * @return a max pooling layer.
     */
    static MaxPool2dLayer maxPool2d(int size) {
        return new MaxPool2dLayer(size);
    }

    /**
     * Returns a normalization layer that re-centers and normalizes the output
     * of one layer before feeding it to another. Centering and scaling the
     * intermediate tensors has a number of beneficial effects, such as allowing
     * higher learning rates without exploding/vanishing gradients.
     * @param in the number of input features.
     * @return a normalization layer.
     */
    static BatchNorm1dLayer batchNorm1d(int in) {
        return new BatchNorm1dLayer(in);
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
    static BatchNorm1dLayer batchNorm1d(int in, double eps, double momentum, boolean affine) {
        return new BatchNorm1dLayer(in, eps, momentum, affine);
    }

    /**
     * Returns a normalization layer that re-centers and normalizes the output
     * of one layer before feeding it to another. Centering and scaling the
     * intermediate tensors has a number of beneficial effects, such as allowing
     * higher learning rates without exploding/vanishing gradients.
     * @param in the number of input features.
     * @return a normalization layer.
     */
    static BatchNorm2dLayer batchNorm2d(int in) {
        return new BatchNorm2dLayer(in);
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
    static BatchNorm2dLayer batchNorm2d(int in, double eps, double momentum, boolean affine) {
        return new BatchNorm2dLayer(in, eps, momentum, affine);
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
    static DropoutLayer dropout(double p) {
        return new DropoutLayer(p);
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
    static EmbeddingLayer embedding(int numTokens, int dim) {
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
    static EmbeddingLayer embedding(int numTokens, int dim, double alpha) {
        return new EmbeddingLayer(numTokens, dim, alpha);
    }
}
