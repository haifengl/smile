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

import java.util.function.Function;
import org.bytedeco.pytorch.Module;
import smile.deep.activation.*;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;

/**
 * A layer in the neural network.
 *
 * @author Haifeng Li
 */
public interface Layer extends Function<Tensor, Tensor> {
    /**
     * Forward propagation (or forward pass) through the layer.
     *
     * @param input the input tensor.
     * @return the output tensor.
     */
    Tensor forward(Tensor input);

    @Override
    default Tensor apply(Tensor input) {
        return forward(input);
    }

    /**
     * Returns the PyTorch Module object.
     * @return the PyTorch Module object.
     */
    Module asTorch();

    /**
     * Moves the layer block to a device.
     * @param device the compute device.
     * @return this layer.
     */
    default Layer to(Device device) {
        asTorch().to(device.asTorch(), true);
        return this;
    }

    /**
     * Moves the layer block to a device.
     * @param device the compute device.
     * @param dtype the data type.
     * @return this layer.
     */
    default Layer to(Device device, ScalarType dtype) {
        asTorch().to(device.asTorch(), dtype.asTorch(), true);
        return this;
    }

    /**
     * Returns a linear fully connected layer.
     * @param in the number of input features.
     * @param out the number of output features.
     * @return a fully connected layer.
     */
    static LinearLayer linear(int in, int out) {
        return new LinearLayer(in, out);
    }

    /**
     * Returns a fully connected layer with ReLU activation function.
     * @param in the number of input features.
     * @param out the number of output features.
     * @return a fully connected layer.
     */
    static SequentialBlock relu(int in, int out) {
        return new SequentialBlock(
                new LinearLayer(in, out),
                new ReLU(true)
        );
    }

    /**
     * Returns a fully connected layer with ReLU activation function.
     * @param in the number of input features.
     * @param out the number of output features.
     * @param dropout the optional dropout probability.
     * @return a fully connected layer.
     */
    static SequentialBlock relu(int in, int out, double dropout) {
        return new SequentialBlock(
                new LinearLayer(in, out),
                new ReLU(true),
                new DropoutLayer(dropout)
        );
    }

    /**
     * Returns a fully connected layer with leaky ReLU activation function.
     * @param in the number of input features.
     * @param out the number of output features.
     * @param negativeSlope Controls the angle of the negative slope in leaky ReLU,
     *                     which is used for negative input values.
     * @return a fully connected layer.
     */
    static SequentialBlock leaky(int in, int out, double negativeSlope) {
        return new SequentialBlock(
                new LinearLayer(in, out),
                new LeakyReLU(negativeSlope, true)
        );
    }

    /**
     * Returns a fully connected layer with leaky ReLU activation function.
     * @param in the number of input features.
     * @param out the number of output features.
     * @param negativeSlope Controls the angle of the negative slope in leaky ReLU,
     *                     which is used for negative input values.
     * @param dropout the optional dropout probability.
     * @return a fully connected layer.
     */
    static SequentialBlock leaky(int in, int out, double negativeSlope, double dropout) {
        return new SequentialBlock(
                new LinearLayer(in, out),
                new LeakyReLU(negativeSlope, true),
                new DropoutLayer(dropout)
        );
    }

    /**
     * Returns a fully connected layer with GELU activation function.
     * @param in the number of input features.
     * @param out the number of output features.
     * @return a fully connected layer.
     */
    static SequentialBlock gelu(int in, int out) {
        return new SequentialBlock(
                new LinearLayer(in, out),
                new GELU(true)
        );
    }

    /**
     * Returns a fully connected layer with GELU activation function.
     * @param in the number of input features.
     * @param out the number of output features.
     * @param dropout the optional dropout probability.
     * @return a fully connected layer.
     */
    static SequentialBlock gelu(int in, int out, double dropout) {
        return new SequentialBlock(
                new LinearLayer(in, out),
                new GELU(true),
                new DropoutLayer(dropout)
        );
    }

    /**
     * Returns a fully connected layer with SiLU activation function.
     * @param in the number of input features.
     * @param out the number of output features.
     * @return a fully connected layer.
     */
    static SequentialBlock silu(int in, int out) {
        return new SequentialBlock(
                new LinearLayer(in, out),
                new GELU(true)
        );
    }

    /**
     * Returns a fully connected layer with SiLU activation function.
     * @param in the number of input features.
     * @param out the number of output features.
     * @param dropout the optional dropout probability.
     * @return a fully connected layer.
     */
    static SequentialBlock silu(int in, int out, double dropout) {
        return new SequentialBlock(
                new LinearLayer(in, out),
                new SiLU(true),
                new DropoutLayer(dropout)
        );
    }

    /**
     * Returns a fully connected layer with tanh activation function.
     * @param in the number of input features.
     * @param out the number of output features.
     * @return a fully connected layer.
     */
    static SequentialBlock tanh(int in, int out) {
        return new SequentialBlock(
                new LinearLayer(in, out),
                new Tanh(true)
        );
    }

    /**
     * Returns a fully connected layer with sigmoid activation function.
     * @param in the number of input features.
     * @param out the number of output features.
     * @return a fully connected layer.
     */
    static SequentialBlock sigmoid(int in, int out) {
        return new SequentialBlock(
                new LinearLayer(in, out),
                new Sigmoid(true)
        );
    }

    /**
     * Returns a fully connected layer with log sigmoid activation function.
     * @param in the number of input features.
     * @param out the number of output features.
     * @return a fully connected layer.
     */
    static SequentialBlock logSigmoid(int in, int out) {
        return new SequentialBlock(
                new LinearLayer(in, out),
                new LogSigmoid()
        );
    }

    /**
     * Returns a fully connected layer with softmax activation function.
     * @param in the number of input features.
     * @param out the number of output features.
     * @return a fully connected layer.
     */
    static SequentialBlock softmax(int in, int out) {
        return new SequentialBlock(
                new LinearLayer(in, out),
                new Softmax()
        );
    }

    /**
     * Returns a fully connected layer with log softmax activation function.
     * @param in the number of input features.
     * @param out the number of output features.
     * @return a fully connected layer.
     */
    static SequentialBlock logSoftmax(int in, int out) {
        return new SequentialBlock(
                new LinearLayer(in, out),
                new LogSoftmax()
        );
    }

    /**
     * Returns a fully connected layer with tanh shrink activation function.
     * @param in the number of input features.
     * @param out the number of output features.
     * @return a fully connected layer.
     */
    static SequentialBlock tanhShrink(int in, int out) {
        return new SequentialBlock(
                new LinearLayer(in, out),
                new TanhShrink()
        );
    }

    /**
     * Returns a fully connected layer with soft shrink activation function.
     * @param in the number of input features.
     * @param out the number of output features.
     * @return a fully connected layer.
     */
    static SequentialBlock softShrink(int in, int out) {
        return new SequentialBlock(
                new LinearLayer(in, out),
                new SoftShrink()
        );
    }

    /**
     * Returns a fully connected layer with hard shrink activation function.
     * @param in the number of input features.
     * @param out the number of output features.
     * @return a fully connected layer.
     */
    static SequentialBlock hardShrink(int in, int out) {
        return new SequentialBlock(
                new LinearLayer(in, out),
                new HardShrink()
        );
    }

    /**
     * Returns a convolutional layer.
     * @param in the number of input channels.
     * @param out the number of output features.
     * @param kernel the window/kernel size.
     * @return a convolutional layer.
     */
    static Conv2dLayer conv2d(int in, int out, int kernel) {
        return new Conv2dLayer(in, out, kernel, 1, 0, 1, 1, true, "zeros");
    }

    /**
     * Returns a convolutional layer.
     * @param in the number of input channels.
     * @param out the number of output channels/features.
     * @param kernel the window/kernel size.
     * @param stride controls the stride for the cross-correlation.
     * @param padding controls the amount of padding applied on both sides.
     * @param dilation controls the spacing between the kernel points.
     * @param groups controls the connections between inputs and outputs.
     *              The in channels and out channels must both be divisible by groups.
     * @param bias If true, adds a learnable bias to the output.
     * @param paddingMode "zeros", "reflect", "replicate" or "circular".
     * @return a convolutional layer.
     */
    static Conv2dLayer conv2d(int in, int out, int kernel, int stride, int padding, int dilation, int groups, boolean bias, String paddingMode) {
        return new Conv2dLayer(in, out, kernel, stride, padding, dilation, groups, bias, paddingMode);
    }

    /**
     * Returns a convolutional layer.
     * @param in the number of input channels.
     * @param out the number of output channels/features.
     * @param size the window/kernel size.
     * @param stride controls the stride for the cross-correlation.
     * @param padding "valid" or "same". With "valid" padding, there's no
     *               "made-up" padding inputs. It drops the right-most columns
     *               (or bottom-most rows). "same" tries to pad evenly left
     *               and right, but if the amount of columns to be added
     *               is odd, it will add the extra column to the right.
     *               If stride is 1, the layer's outputs will have the
     *               same spatial dimensions as its inputs.
     * @param dilation controls the spacing between the kernel points.
     * @param groups controls the connections between inputs and outputs.
     *              The in channels and out channels must both be divisible by groups.
     * @param bias If true, adds a learnable bias to the output.
     * @param paddingMode "zeros", "reflect", "replicate" or "circular".
     * @return a convolutional layer.
     */
    static Conv2dLayer conv2d(int in, int out, int size, int stride, String padding, int dilation, int groups, boolean bias, String paddingMode) {
        return new Conv2dLayer(in, out, size, stride, padding, dilation, groups, bias, paddingMode);
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
     * Returns an average pooling layer that reduces a tensor by combining cells,
     * and assigning the average value of the input cells to the output cell.
     * @param size the window/kernel size.
     * @return a max pooling layer.
     */
    static AvgPool2dLayer avgPool2d(int size) {
        return new AvgPool2dLayer(size);
    }

    /**
     * Returns an adaptive average pooling layer.
     * @param size the output size.
     * @return an adaptive average pooling layer.
     */
    static AdaptiveAvgPool2dLayer adaptiveAvgPool2d(int size) {
        return new AdaptiveAvgPool2dLayer(size);
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
     * <p>
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
     * <p>
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
     * <p>
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
