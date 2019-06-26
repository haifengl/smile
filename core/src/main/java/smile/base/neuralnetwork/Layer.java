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
     * @param n the number of input variables.
     * @param m the number of output variables.
     */
    public Layer(ActivationFunction activation, int n, int m) {
        this.units = m;
        this.activation = activation;
        output = new double[m + 1];
        output[m] = 1.0; // for intercept
        error = new double[m + 1];
        weight = Matrix.zeros(m, n + 1);
        delta = Matrix.zeros(m, n + 1);

        // Initialize random weights.
        double r = 1.0 / Math.sqrt(n);
        for (int j = 0; j <= n; j++) {
            for (int i = 0; i < m; i++) {
                weight.set(i, j, MathEx.random(-r, r));
            }
        }
    }

    /** Returns the output. */
    public double[] getOutput() {
        return output;
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

            case TANH:
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
     * @param eta the learning rate
     */
    public void backpropagate(Layer upper, double eta) {
        upper.weight.atx(upper.error, error);
        for (int i = 0; i < units; i++) {
            double out = output[i];
            error[i] = out * (1.0 - out) * error[i];
        }

        for (int j = 0; j <= units; j++) {
            for (int i = 0; i < upper.units; i++) {
                upper.delta.add(i, j, eta * upper.error[i] * output[j]);
            }
        }
    }

    /**
     * Adjust network weights by back-propagation algorithm.
     * @param alpha the momentum factor
     */
    public void update(double alpha) {
        weight.add(delta);
        delta.mul(alpha);
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

    /**
     * Compute the network output error.
     * @param obj the objective function.
     * @param target the desired output.
     * @return the error defined by loss function.
     */
    public double computeOutputError(ObjectiveFunction obj, double[] target) {
        if (output.length != units) {
            throw new IllegalArgumentException(String.format("Invalid output vector size: %d, expected: %d", output.length, units));
        }

        double err = 0.0;
        for (int i = 0; i < units; i++) {
            double out = output[i];
            double g = target[i] - out;

            switch (obj) {
                case LEAST_MEAN_SQUARES:
                    err += 0.5 * g * g;
                    break;

                case CROSS_ENTROPY:
                    switch (activation) {
                        case SOFTMAX:
                            err -= target[i] * log(out);
                            break;

                        case LOGISTIC_SIGMOID:
                            // We have only one output neuron in this case.
                            err = -target[i] * log(out) - (1.0 - target[i]) * log(1.0 - out);
                            g *= out * (1.0 - out);
                            break;

                        default:
                            throw new IllegalArgumentException(String.format("Unsupported activation function %s for objective function %s", activation, obj));
                    }
            }

            error[i] = g;
        }

        return err;
    }

    /**
     * Returns natural log without underflow.
     */
    private double log(double x) {
        return (x < 1E-300) ? -690.7755 : Math.log(x);
    }
}