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
    protected int n;
    /**
     * The number of input variables.
     */
    protected int p;
    /**
     * The affine transformation matrix.
     */
    protected Matrix weight;
    /**
     * The bias.
     */
    protected double[] bias;
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
     * Constructor. Randomly initialized weights and zero bias.
     *
     * @param n the number of neurons.
     * @param p the number of input variables (not including bias value).
     */
    public Layer(int n, int p) {
        this(Matrix.rand(n, p, -Math.sqrt(6.0 / (n+p)), Math.sqrt(6.0 / (n+p))), new double[n]);
    }

    /**
     * Constructor.
     * @param weight the weight matrix.
     * @param bias the bias vector.
     */
    public Layer(Matrix weight, double[] bias) {
        this.n = weight.nrow();
        this.p = weight.ncol();
        this.weight = weight;
        this.bias = bias;

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
        output = new ThreadLocal<double[]>() {
            protected synchronized double[] initialValue() {
                return new double[n];
            }
        };
        outputGradient = new ThreadLocal<double[]>() {
            protected synchronized double[] initialValue() {
                return new double[n];
            }
        };
        weightGradient = new ThreadLocal<Matrix>() {
            protected synchronized Matrix initialValue() {
                return new Matrix(n, p);
            }
        };
        biasGradient = new ThreadLocal<double[]>() {
            protected synchronized double[] initialValue() {
                return new double[n];
            }
        };
        rmsWeightGradient = new ThreadLocal<Matrix>() {
            protected synchronized Matrix initialValue() {
                return new Matrix(n, p);
            }
        };
        rmsBiasGradient = new ThreadLocal<double[]>() {
            protected synchronized double[] initialValue() {
                return new double[n];
            }
        };
        weightUpdate = new ThreadLocal<Matrix>() {
            protected synchronized Matrix initialValue() {
                return new Matrix(n, p);
            }
        };
        biasUpdate = new ThreadLocal<double[]>() {
            protected synchronized double[] initialValue() {
                return new double[n];
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
     */
    public void propagate(double[] x) {
        double[] output = this.output.get();
        System.arraycopy(bias, 0, output, 0, n);
        weight.mv(1.0, x, 1.0, output);
        f(output);
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
     * Returns a hidden layer with linear activation function.
     * @param n the number of neurons.
     * @return the layer builder.
     */
    public static HiddenLayerBuilder linear(int n) {
        return new HiddenLayerBuilder(n, ActivationFunction.linear());
    }

    /**
     * Returns a hidden layer with rectified linear activation function.
     * @param n the number of neurons.
     * @return the layer builder.
     */
    public static HiddenLayerBuilder rectifier(int n) {
        return new HiddenLayerBuilder(n, ActivationFunction.rectifier());
    }

    /**
     * Returns a hidden layer with sigmoid activation function.
     * @param n the number of neurons.
     * @return the layer builder.
     */
    public static HiddenLayerBuilder sigmoid(int n) {
        return new HiddenLayerBuilder(n, ActivationFunction.sigmoid());
    }

    /**
     * Returns a hidden layer with hyperbolic tangent activation function.
     * @param n the number of neurons.
     * @return the layer builder.
     */
    public static HiddenLayerBuilder tanh(int n) {
        return new HiddenLayerBuilder(n, ActivationFunction.tanh());
    }

    /**
     * Returns an output layer with mean squared error cost function.
     * @param n the number of neurons.
     * @param f the output function.
     * @return the layer builder.
     */
    public static OutputLayerBuilder mse(int n, OutputFunction f) {
        return new OutputLayerBuilder(n, f, Cost.MEAN_SQUARED_ERROR);
    }

    /**
     * Returns an output layer with (log-)likelihood cost function.
     * @param n the number of neurons.
     * @param f the output function.
     * @return the layer builder.
     */
    public static OutputLayerBuilder mle(int n, OutputFunction f) {
        return new OutputLayerBuilder(n, f, Cost.LIKELIHOOD);
    }
}