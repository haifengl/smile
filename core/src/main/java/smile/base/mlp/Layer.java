/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.base.mlp;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import smile.math.MathEx;
import smile.math.matrix.Matrix;

/**
 * A layer in the neural network.
 *
 * @author Haifeng Li
 */
public abstract class Layer implements Serializable {
    private static final long serialVersionUID = 2L;
    /**
     * The number of neurons in this layer
     */
    protected final int n;
    /**
     * The number of input variables.
     */
    protected final int p;
    /**
     * The dropout rate. Dropout randomly sets input units to 0 with this rate
     * at each step during training time, which helps prevent overfitting.
     */
    protected final double dropoutRate;
    /**
     * The dropout scaling factor.
     * Inputs not set to 0 are scaled up by 1/(1 - rate) such that the sum
     * over all inputs is unchanged.
     */
    protected final double dropoutScale;
    /**
     * The affine transformation matrix.
     */
    protected Matrix weight;
    /**
     * The bias.
     */
    protected double[] bias;
    /**
     * The input vector in case of batch normalization and/or dropout.
     */
    protected transient ThreadLocal<double[]> input;
    /**
     * The output vector.
     */
    protected transient ThreadLocal<double[]> output;
    /**
     * The output gradient.
     */
    protected transient ThreadLocal<double[]> outputGradient;
    /**
     * The weight gradient.
     */
    protected transient ThreadLocal<Matrix> weightGradient;
    /**
     * The bias gradient.
     */
    protected transient ThreadLocal<double[]> biasGradient;
    /**
     * The accumulate weight gradient.
     */
    protected transient ThreadLocal<Matrix> rmsWeightGradient;
    /**
     * The accumulate bias gradient.
     */
    protected transient ThreadLocal<double[]> rmsBiasGradient;
    /**
     * The weight update.
     */
    protected transient ThreadLocal<Matrix> weightUpdate;
    /**
     * The bias update.
     */
    protected transient ThreadLocal<double[]> biasUpdate;
    /**
     * The dropout mask.
     */
    protected transient ThreadLocal<byte[]> dropoutMask;

    /**
     * Constructor. Randomly initialized weights and zero bias.
     *
     * @param n the number of neurons.
     * @param p the number of input variables (not including bias value).
     */
    public Layer(int n, int p) {
        this(n, p, 0.0);
    }

    /**
     * Constructor. Randomly initialized weights and zero bias.
     *
     * @param n the number of neurons.
     * @param p the number of input variables (not including bias value).
     * @param dropout the dropout rate.
     */
    public Layer(int n, int p, double dropout) {
        this(Matrix.rand(n, p, -Math.sqrt(6.0 / (n+p)), Math.sqrt(6.0 / (n+p))), new double[n], dropout);
    }

    /**
     * Constructor.
     * @param weight the weight matrix.
     * @param bias the bias vector.
     */
    public Layer(Matrix weight, double[] bias) {
        this(weight, bias, 0.0);
    }

    /**
     * Constructor.
     * @param weight the weight matrix.
     * @param bias the bias vector.
     * @param dropout the dropout rate.
     */
    public Layer(Matrix weight, double[] bias, double dropout) {
        if (dropout < 0.0 || dropout >= 1.0) {
            throw new IllegalArgumentException("Invalid dropout rate: " + dropout);
        }

        this.n = weight.nrow();
        this.p = weight.ncol();
        this.weight = weight;
        this.bias = bias;
        this.dropoutRate = dropout;
        this.dropoutScale = dropout > 0 ? 1.0 / (1.0 - dropout) : 1.0;

        init();
    }

    /**
     * Initializes the workspace when deserializing the object.
     * @param in the input stream.
     * @throws IOException when fails to read the stream.
     * @throws ClassNotFoundException when fails to load the class.
     */
    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        init();
    }

    /**
     * Initializes the workspace.
     */
    private void init() {
        input = new ThreadLocal<double[]>() {
            protected double[] initialValue() {
                return new double[p];
            }
        };
        output = new ThreadLocal<double[]>() {
            protected double[] initialValue() {
                return new double[n];
            }
        };
        outputGradient = new ThreadLocal<double[]>() {
            protected double[] initialValue() {
                return new double[n];
            }
        };
        weightGradient = new ThreadLocal<Matrix>() {
            protected Matrix initialValue() {
                return new Matrix(n, p);
            }
        };
        biasGradient = new ThreadLocal<double[]>() {
            protected double[] initialValue() {
                return new double[n];
            }
        };
        rmsWeightGradient = new ThreadLocal<Matrix>() {
            protected Matrix initialValue() {
                return new Matrix(n, p);
            }
        };
        rmsBiasGradient = new ThreadLocal<double[]>() {
            protected double[] initialValue() {
                return new double[n];
            }
        };
        weightUpdate = new ThreadLocal<Matrix>() {
            protected Matrix initialValue() {
                return new Matrix(n, p);
            }
        };
        biasUpdate = new ThreadLocal<double[]>() {
            protected double[] initialValue() {
                return new double[n];
            }
        };
        dropoutMask = new ThreadLocal<byte[]>() {
            protected byte[] initialValue() {
                return new byte[p];
            }
        };
    }

    /**
     * Returns the dimension of output vector.
     * @return the dimension of output vector.
     */
    public int getOutputSize() {
        return n;
    }

    /**
     * Returns the dimension of input vector (not including bias value).
     * @return the dimension of input vector.
     */
    public int getInputSize() {
        return p;
    }

    /**
     * Returns the output vector.
     * @return the output vector.
     */
    public double[] output() {
        return output.get();
    }

    /**
     * Returns the output gradient vector.
     * @return the output gradient vector.
     */
    public double[] gradient() {
        return outputGradient.get();
    }

    /**
     * Propagates signals from a lower layer to this layer.
     * @param x the lower layer signals.
     * @param train true if this is in training pass.
     */
    public void propagate(double[] x, boolean train) {
        double[] output = this.output.get();
        System.arraycopy(bias, 0, output, 0, n);
        weight.mv(1.0, x, 1.0, output);
        f(output);
    }

    /**
     * Dropout randomly sets output units to 0. It should only be applied
     * during training.
     */
    public void dropout() {
        if (dropoutRate > 0.0) {
            double[] output = this.output.get();
            byte[] mask = this.dropoutMask.get();
            for (int i = 0; i < p; i++) {
                byte retain = (byte) (MathEx.random() < dropoutRate ? 0 : 1);
                mask[i] = retain;
                output[i] *= retain * dropoutScale;
            }
        }
    }

    /**
     * The activation or output function.
     * @param x the input and output values.
     */
    public abstract void f(double[] x);

    /**
     * Propagates the errors back to a lower layer.
     * @param lowerLayerGradient the gradient vector of lower layer.
     */
    public abstract void backpropagate(double[] lowerLayerGradient);

    /**
     * Propagates back the (implicit) dropout layer.
     * @param lowerLayerGradient the gradient vector of lower layer.
     */
    public void backpopagateDropout(double[] lowerLayerGradient) {
        if (dropoutRate > 0.0) {
            byte[] mask = this.dropoutMask.get();
            for (int i = 0; i < lowerLayerGradient.length; i++) {
                lowerLayerGradient[i] *= mask[i] * dropoutScale;
            }
        }
    }

    /**
     * Computes the parameter gradient and update the weights.
     *
     * @param x the input vector.
     * @param learningRate the learning rate.
     * @param momentum the momentum factor.
     * @param decay weight decay factor.
     */
    public void computeGradientUpdate(double[] x, double learningRate, double momentum, double decay) {
        double[] outputGradient = this.outputGradient.get();

        if (momentum > 0.0 && momentum < 1.0) {
            Matrix weightUpdate = this.weightUpdate.get();
            double[] biasUpdate = this.biasUpdate.get();

            weightUpdate.mul(momentum);
            weightUpdate.add(learningRate, outputGradient, x);
            weight.add(1.0, weightUpdate);

            for (int i = 0; i < n; i++) {
                double b = momentum * biasUpdate[i] + learningRate * outputGradient[i];
                biasUpdate[i] = b;
                bias[i] += b;
            }
        } else {
            weight.add(learningRate, outputGradient, x);
            for (int i = 0; i < n; i++) {
                bias[i] += learningRate * outputGradient[i];
            }
        }

        if (decay > 0.9 && decay < 1.0) {
            weight.mul(decay);
        }
    }

    /**
     * Computes the parameter gradient for a sample of (mini-)batch.
     *
     * @param x the input vector.
     */
    public void computeGradient(double[] x) {
        double[] outputGradient = this.outputGradient.get();
        Matrix weightGradient = this.weightGradient.get();
        double[] biasGradient = this.biasGradient.get();

        weightGradient.add(1.0, outputGradient, x);
        for (int i = 0; i < n; i++) {
            biasGradient[i] += outputGradient[i];
        }
    }

    /**
     * Adjust network weights by back-propagation algorithm.
     *
     * @param m the size of mini-batch.
     * @param learningRate the learning rate.
     * @param momentum the momentum factor.
     * @param decay weight decay factor.
     * @param rho RMSProp discounting factor for the history/coming gradient.
     * @param epsilon a small constant for numerical stability.
     */
    public void update(int m, double learningRate, double momentum, double decay, double rho, double epsilon) {
        Matrix weightGradient = this.weightGradient.get();
        double[] biasGradient = this.biasGradient.get();

        double eta = learningRate / m;

        if (rho > 0.0 && rho < 1.0) {
            // gradient will be averaged and smoothed in RMSProp
            eta = learningRate;

            weightGradient.div(m);
            for (int i = 0; i < n; i++) {
                biasGradient[i] /= m;
            }

            Matrix rmsWeightGradient = this.rmsWeightGradient.get();
            double[] rmsBiasGradient = this.rmsBiasGradient.get();

            double rho1 = 1.0 - rho;
            for (int j = 0; j < p; j++) {
                for (int i = 0; i < n; i++) {
                    rmsWeightGradient.set(i, j, rho * rmsWeightGradient.get(i, j) + rho1 * MathEx.pow2(weightGradient.get(i, j)));
                }
            }
            for (int i = 0; i < n; i++) {
                rmsBiasGradient[i] = rho * rmsBiasGradient[i] + rho1 * MathEx.pow2(biasGradient[i]);
            }

            for (int j = 0; j < p; j++) {
                for (int i = 0; i < n; i++) {
                    weightGradient.div(i, j, Math.sqrt(epsilon + rmsWeightGradient.get(i, j)));
                }
            }
            for (int i = 0; i < n; i++) {
                biasGradient[i] /= Math.sqrt(epsilon + rmsBiasGradient[i]);
            }
        }

        if (momentum > 0.0 && momentum < 1.0) {
            Matrix weightUpdate = this.weightUpdate.get();
            double[] biasUpdate = this.biasUpdate.get();

            weightUpdate.add(momentum, eta, weightGradient);
            for (int i = 0; i < n; i++) {
                biasUpdate[i] = momentum * biasUpdate[i] + eta * biasGradient[i];
            }

            weight.add(1.0, weightUpdate);
            MathEx.add(bias, biasUpdate);
        } else {
            weight.add(eta, weightGradient);
            for (int i = 0; i < n; i++) {
                bias[i] += eta * biasGradient[i];
            }
        }

        // Weight decay as the weights are multiplied
        // by a factor slightly less than 1. This prevents the weights
        // from growing too large, and can be seen as gradient descent
        // on a quadratic regularization term.
        if (decay > 0.9 && decay < 1.0) {
            weight.mul(decay);
        }

        weightGradient.fill(0.0);
        Arrays.fill(biasGradient, 0.0);
    }

    /**
     * Returns a hidden layer.
     * @param activation the activation function.
     * @param neurons the number of neurons.
     * @return the layer builder.
     */
    public static HiddenLayerBuilder builder(String activation, int neurons) {
        switch(activation.toLowerCase(Locale.ROOT)) {
            case "relu":
                return rectifier(neurons);
            case "sigmoid":
                return sigmoid(neurons);
            case "tanh":
                return tanh(neurons);
            case "linear":
                return linear(neurons);
            default:
                throw new IllegalArgumentException("Unsupported activation function: " + activation);
        }
    }

    /**
     * Returns a hidden layer with linear activation function.
     * @param neurons the number of neurons.
     * @return the layer builder.
     */
    public static HiddenLayerBuilder linear(int neurons) {
        return new HiddenLayerBuilder(neurons, ActivationFunction.linear());
    }

    /**
     * Returns a hidden layer with rectified linear activation function.
     * @param neurons the number of neurons.
     * @return the layer builder.
     */
    public static HiddenLayerBuilder rectifier(int neurons) {
        return new HiddenLayerBuilder(neurons, ActivationFunction.rectifier());
    }

    /**
     * Returns a hidden layer with sigmoid activation function.
     * @param neurons the number of neurons.
     * @return the layer builder.
     */
    public static HiddenLayerBuilder sigmoid(int neurons) {
        return new HiddenLayerBuilder(neurons, ActivationFunction.sigmoid());
    }

    /**
     * Returns a hidden layer with hyperbolic tangent activation function.
     * @param neurons the number of neurons.
     * @return the layer builder.
     */
    public static HiddenLayerBuilder tanh(int neurons) {
        return new HiddenLayerBuilder(neurons, ActivationFunction.tanh());
    }

    /**
     * Returns an output layer with mean squared error cost function.
     * @param neurons the number of neurons.
     * @param output the output function.
     * @return the layer builder.
     */
    public static OutputLayerBuilder mse(int neurons, OutputFunction output) {
        return new OutputLayerBuilder(neurons, output, Cost.MEAN_SQUARED_ERROR);
    }

    /**
     * Returns an output layer with (log-)likelihood cost function.
     * @param neurons the number of neurons.
     * @param output the output function.
     * @return the layer builder.
     */
    public static OutputLayerBuilder mle(int neurons, OutputFunction output) {
        return new OutputLayerBuilder(neurons, output, Cost.LIKELIHOOD);
    }

    /**
     * Returns the layer builders given a string representation such as
     * "ReLU(50)|Sigmoid(30)|...".
     *
     * @param k the number of classes. k < 2 for regression.
     * @param spec the hidden layer specification.
     * @return the layer builders.
     */
    public static LayerBuilder[] of(int k, String spec) {
        Pattern regex = Pattern.compile("(\\w+)\\((\\d+)\\)");
        String[] layers = spec.split("\\|");
        LayerBuilder[] builders = new LayerBuilder[layers.length + 1];
        for (int i = 0; i < layers.length; i++) {
            Matcher m = regex.matcher(layers[i]);
            if (m.matches()) {
                String activation = m.group(1);
                int nodes = Integer.parseInt(m.group(2));
                builders[i] = Layer.builder(activation, nodes);
            } else {
                throw new IllegalArgumentException("Invalid layer: " + layers[i]);
            }
        }

        if (k < 2) {
            builders[layers.length] = Layer.mse(1, OutputFunction.LINEAR);
        } else if (k == 2) {
            builders[layers.length] = Layer.mle(1, OutputFunction.SIGMOID);
        } else {
            builders[layers.length] = Layer.mle(k, OutputFunction.SOFTMAX);
        }

        return builders;
    }
}