/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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
 *******************************************************************************/

package smile.base.neuralnetwork;

import java.io.Serializable;
import smile.math.MathEx;
import smile.math.matrix.Matrix;
import smile.math.matrix.DenseMatrix;

/**
 * A layer in the neural network.
 */
public class Layer implements Serializable {
    private static final long serialVersionUID = 1L;
    /**
     * The number of neurons in this layer
     */
    int units;
    /**
     * The number of input variables.
     */
    int p;
    /**
     * The activation function.
     */
    private ActivationFunction activation;
    /**
     * output of i<i>th</i> unit
     */
    private double[] output;
    /**
     * error term of i<i>th</i> unit
     */
    private double[] error;
    /**
     * connection weights to i<i>th</i> unit from previous layer
     */
    private DenseMatrix weight;
    /**
     * weight changes for mini batch or for momentum
     */
    private DenseMatrix delta;

    /**
     * Constructor.
     * @param activation the activation function.
     * @param n the number of neurons.
     * @param p the number of input variables (not including bias value).
     */
    public Layer(ActivationFunction activation, int n, int p) {
        this.units = n;
        this.p = p;
        this.activation = activation;
        output = new double[n + 1];
        output[n] = 1.0; // for intercept
        error = new double[n + 1];
        weight = Matrix.zeros(n, p + 1);
        delta = Matrix.zeros(n, p + 1);

        // Initialize random weights.
        double r = 1.0 / Math.sqrt(n);
        for (int j = 0; j <= p; j++) {
            for (int i = 0; i < n; i++) {
                weight.set(i, j, MathEx.random(-r, r));
            }
        }
    }

    /** Returns the activation function. */
    public ActivationFunction getActivation() {
        return activation;
    }

    /** Returns the dimension of output vector. */
    public int getOutputUnits() {
        return units;
    }

    /** Returns the dimension of input vector (not including bias value). */
    public int getInputUnits() {
        return p;
    }

    /** Returns the output vector. */
    public double[] getOutput() {
        return output;
    }

    /** Returns the error/gradient vector. */
    public double[] getError() {
        return error;
    }

    /**
     * Propagates signals from a lower layer to this layer.
     * @param x the lower layer signals.
     */
    public void propagate(double[] x) {
        assert x[x.length-1] == 1.0 : "bias/intercept element is not 1"; // for intercept

        weight.ax(x, output);

        switch (activation) {
            case RECTIFIER:
                for (int i = 0; i < units; i++) {
                    output[i] = Math.max(0.0, output[i]);
                }
                break;

            case LOGISTIC_SIGMOID:
                for (int i = 0; i < units; i++) {
                    output[i] = MathEx.logistic(output[i]);
                }
                break;

            case HYPERBOLIC_TANGENT:
                for (int i = 0; i < units; i++) {
                    output[i] = MathEx.tanh(output[i]);
                }
                break;

            case SOFTMAX:
                softmax();
                break;

            case LINEAR: // nop
                break;
        }
    }

    /**
     * Propagates the errors back from a upper layer to this layer.
     * @param upper the upper layer where errors are from.
     */
    public void backpropagate(Layer upper) {
        upper.weight.atx(upper.error, error);
        for (int i = 0; i <= units; i++) {
            double out = output[i];
            error[i] = out * (1.0 - out) * error[i];
        }
    }

    /**
     * Computes the update to weights.
     *
     * @param input the input vector of layer.
     * @param eta the learning rate.
     * @param alpha the momentum factor
     */
    public void computeUpdate(double[] input, double eta, double alpha) {
        for (int j = 0; j < input.length; j++) {
            for (int i = 0; i < units; i++) {
                double update = alpha * delta.get(i, j) + (1 - alpha) * eta * error[i] * input[j];
                delta.set(i, j, update);
            }
        }
    }

    /**
     * Adjust network weights by back-propagation algorithm.
     */
    public void update() {
        weight.add(delta);
    }

    /**
     * Weight decay. After each update, the weights are multiplied
     * by a factor slightly less than 1. This prevents the weights
     * from growing too large, and can be seen as gradient descent
     * on a quadratic regularization term.
     *
     * @param lambda weight decay factor
     */
    public void decay(double lambda) {
        int m = weight.nrows();
        int n = weight.ncols() - 1;
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                weight.mul(i, j, lambda);
            }
        }
    }

    /**
     * Calculate softmax activation function without overflow.
     */
    private void softmax() {
        double max = MathEx.max(output);

        double sum = 0.0;
        for (int i = 0; i < output.length; i++) {
            double out = Math.exp(output[i] - max);
            output[i] = out;
            sum += out;
        }

        for (int i = 0; i < output.length; i++) {
            output[i] /= sum;
        }
    }
}