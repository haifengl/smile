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

package smile.base;

import smile.base.neuralnetwork.ObjectiveFunction;
import smile.base.neuralnetwork.Layer;

/**
 * Multilayer perceptron neural network.
 * An MLP consists of several layers of nodes, interconnected through weighted
 * acyclic arcs from each preceding layer to the following, without lateral or
 * feedback connections. Each node calculates a transformed weighted linear
 * combination of its inputs (output activations from the preceding layer), with
 * one of the weights acting as a trainable bias connected to a constant input.
 * The transformation, called activation function, is a bounded non-decreasing
 * (non-linear) function.
 *
 * @author Haifeng Li
 */
public abstract class NeuralNetwork {
    /**
     * Objective function.
     */
    protected ObjectiveFunction obj;
    /**
     * learning rate
     */
    protected double eta = 0.1;
    /**
     * momentum factor
     */
    protected double alpha = 0.0;
    /**
     * weight decay factor, which is also a regularization term.
     */
    protected double lambda = 0.9;
    /**
     * The input layer with bias.
     */
    protected double[] x1;
    /**
     * The buffer to store target value of training instance.
     */
    protected double[] target;

    /**
     * Constructor.
     * @param obj the objective function.
     * @param p the dimension of input vector, i.e. the number of neuron of input layer.
     * @param d the dimension of output vector, i.e. the number of neurons of output layer.
     */
    public NeuralNetwork(ObjectiveFunction obj, int p, int d) {
        this.obj = obj;
        x1 = new double[p+1];
        x1[p] = 1.0;
        target = new double[d];
    }

    /**
     * Constructor.
     * @param obj the objective function.
     * @param p the dimension of input vector, i.e. the number of neurons of input layer.
     * @param d the dimension of output vector, i.e. the number of neurons of output layer.
     * @param eta the learning rate.
     * @param alpha the momentum factor.
     * @param lambda the weight decay factor.
     */
    public NeuralNetwork(ObjectiveFunction obj, int p, int d, double eta, double alpha, double lambda) {
        this(obj, p, d);
        setLearningRate(eta);
        setMomentum(alpha);
        setWeightDecay(lambda);
    }

    /**
     * Sets the learning rate.
     * @param eta the learning rate.
     */
    public void setLearningRate(double eta) {
        if (eta <= 0) {
            throw new IllegalArgumentException("Invalid learning rate: " + eta);
        }

        this.eta = eta;
    }

    /**
     * Sets the momentum factor. alpha = 0.0 means no momentum.
     * @param alpha the momentum factor.
     */
    public void setMomentum(double alpha) {
        if (alpha < 0.0 || alpha >= 1.0) {
            throw new IllegalArgumentException("Invalid momentum factor: " + alpha);
        }

        this.alpha = alpha;
    }

    /**
     * Sets the weight decay factor. lambda = 1.0 means no decay.
     */
    public void setWeightDecay(double lambda) {
        if (lambda < 0.0 || lambda >= 1.0) {
            throw new IllegalArgumentException("Invalid weight decay factor: " + lambda);
        }

        this.lambda = lambda;
    }

   /**
     * Returns the learning rate.
     */
    public double getLearningRate() {
        return eta;
    }

    /**
     * Returns the momentum factor.
     */
    public double getMomentum() {
        return alpha;
    }

    /**
     * Returns the weight decay factor.
     */
    public double getWeightDecay() {
        return lambda;
    }

    /**
     * Propagates the signals through the neural network.
     */
    public void propagate(Layer[] net, double[] x) {
        if (x.length != x1.length - 1) {
            throw new IllegalArgumentException(String.format("Invalid input vector size: %d, expected: %d", x.length, x1.length-1));
        }

        System.arraycopy(x, 0, x1, 0, x.length);
        net[0].propagate(x1);
        for (int l = 1; l < net.length; l++) {
            net[l].propagate(net[l-1].getOutput());
        }
    }

    /**
     * Propagates the errors back through the network.
     */
    public void backpropagate(Layer[] net, double[] y) {
        net[net.length - 1].computeOutputError(obj, y);
        for (int l = net.length; --l > 0;) {
            net[l-1].backpropagate(net[l], eta);
        }
    }

    /**
     * Propagates the errors back through the network.
     */
    public void update(Layer[] net, double[] x, double[] y) {
        propagate(net, x);
        backpropagate(net, y);
    }
}

