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

package smile.base.mlp;

import java.io.Serializable;
import java.util.Arrays;
import smile.math.MathEx;
import smile.math.matrix.Matrix;
import smile.math.matrix.DenseMatrix;

/**
 * A layer in the neural network.
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
     * The output vector.
     */
    protected double[] output;
    /**
     * The gradient vector.
     */
    protected double[] gradient;
    /**
     * The affine transformation matrix.
     */
    protected DenseMatrix weight;
    /**
     * The weight update of mini batch or momentum.
     */
    protected DenseMatrix update;
    /**
     * The bias.
     */
    protected double[] bias;
    /**
     * The bias update of mini batch or momentum.
     */
    protected double[] updateBias;

    /**
     * Constructor.
     * @param n the number of neurons.
     * @param p the number of input variables (not including bias value).
     */
    public Layer(int n, int p) {
        this.n = n;
        this.p = p;

        // Initialize random weights.
        double r = Math.sqrt(2.0 / p);
        weight = Matrix.randn(n, p, 0.0, r);
        bias = new double[n];
        output = new double[n];
        gradient = new double[n];

        update = Matrix.zeros(n, p);
        updateBias = new double[n];
    }

    /** Returns the dimension of output vector. */
    public int getOutputSize() {
        return n;
    }

    /** Returns the dimension of input vector (not including bias value). */
    public int getInputSize() {
        return p;
    }

    /** Returns the output vector. */
    public double[] output() {
        return output;
    }

    /** Returns the error/gradient vector. */
    public double[] gradient() {
        return gradient;
    }

    /**
     * Propagates signals from a lower layer to this layer.
     * @param x the lower layer signals.
     */
    public void propagate(double[] x) {
        System.arraycopy(bias, 0, output, 0, n);
        weight.axpy(x, output);
        f(output);
    }

    /**
     * The activation or output function.
     * @param x the input and output values.
     */
    public abstract void f(double[] x);

    /**
     * Propagates the errors back to a lower layer.
     * @param error the gradient vector of lower layer.
     */
    public abstract void backpropagate(double[] error);

    /**
     * Computes the updates of weight.
     *
     * @param eta the learning rate.
     * @param alpha the momentum factor
     * @param x the input vector.
     */
    public void computeUpdate(double eta, double alpha, double[] x) {
        for (int j = 0; j < p; j++) {
            double xj = x[j];
            for (int i = 0; i < n; i++) {
                double dw = eta * gradient[i] * xj;
                if (alpha > 0.0) dw += alpha * update.get(i, j);
                update.set(i, j, dw);
            }
        }

        for (int i = 0; i < n; i++) {
            double db = eta * gradient[i];
            if (alpha > 0.0) db += alpha * updateBias[i];
            updateBias[i] = db;
        }
    }

    /**
     * Adjust network weights by back-propagation algorithm.
     * @param alpha the momentum factor
     * @param lambda weight decay factor
     */
    public void update(double alpha, double lambda) {
        weight.add(update);
        MathEx.add(bias, updateBias);

        // Weight decay as the weights are multiplied
        // by a factor slightly less than 1. This prevents the weights
        // from growing too large, and can be seen as gradient descent
        // on a quadratic regularization term.
        if (lambda < 1.0) {
            weight.mul(lambda);
        }

        // Clear update matrix after the mini-batch
        if (alpha == 1.0) {
            update.fill(0.0);
            Arrays.fill(updateBias, 0.0);
        }
    }

    /**
     * Returns a hidden layer with linear activation function.
     * @param n the number of neurons.
     */
    public static HiddenLayerBuilder linear(int n) {
        return new HiddenLayerBuilder(n, ActivationFunction.linear());
    }

    /**
     * Returns a hidden layer with rectified linear activation function.
     * @param n the number of neurons.
     */
    public static HiddenLayerBuilder rectifier(int n) {
        return new HiddenLayerBuilder(n, ActivationFunction.rectifier());
    }

    /**
     * Returns a hidden layer with sigmoid activation function.
     * @param n the number of neurons.
     */
    public static HiddenLayerBuilder sigmoid(int n) {
        return new HiddenLayerBuilder(n, ActivationFunction.sigmoid());
    }

    /**
     * Returns a hidden layer with hyperbolic tangent activation function.
     * @param n the number of neurons.
     */
    public static HiddenLayerBuilder tanh(int n) {
        return new HiddenLayerBuilder(n, ActivationFunction.tanh());
    }

    /**
     * Returns an output layer with mean squared error cost function.
     * @param n the number of neurons.
     * @param f the output function.
     */
    public static OutputLayerBuilder mse(int n, OutputFunction f) {
        return new OutputLayerBuilder(n, f, Cost.MEAN_SQUARED_ERROR);
    }

    /**
     * Returns an output layer with (log-)likelihood cost function.
     * @param n the number of neurons.
     * @param f the output function.
     */
    public static OutputLayerBuilder mle(int n, OutputFunction f) {
        return new OutputLayerBuilder(n, f, Cost.LIKELIHOOD);
    }
}