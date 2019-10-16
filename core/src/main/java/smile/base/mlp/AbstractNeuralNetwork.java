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

import java.util.Arrays;
import java.util.stream.Collectors;

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
public abstract class AbstractNeuralNetwork {
    /**
     * The dimensionality of input data.
     */
    protected int p;
    /**
     * The input layer with bias.
     */
    protected double[] x1;
    /**
     * The output layer.
     */
    protected OutputLayer output;
    /**
     * The hidden layers.
     */
    protected Layer[] net;
    /**
     * The buffer to store desired target value of training instance.
     */
    protected double[] target;
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
    protected double lambda = 0.0;

    /**
     * Constructor.
     * @param output the output layer.
     * @param net the hidden layers in the neural network
     *            from bottom to top.
     *            The input layer should not be included.
     */
    public AbstractNeuralNetwork(OutputLayer output, HiddenLayer... net) {
        if (net.length < 1) {
            throw new IllegalArgumentException("Too few layers: " + net.length);
        }

        Layer upper = output;
        for (int i = net.length-1; i >= 0; i--) {
            Layer layer = net[i];
            if (layer.getOutputSize() != upper.getInputSize()) {
                throw new IllegalArgumentException(String.format(
                        "Invalid network architecture. Layer %d has %d neurons while layer %d takes %d inputs",
                        i, layer.getOutputSize(),
                        i+1, upper.getInputSize()));
            }
            upper = layer;
        }
/*
        if (net.length > 1) {
            // reverse the order of layers as the input is from top to bottom
            int l = net.length;
            for (int i = 0; i < l/2; i++) {
                HiddenLayer tmp = net[i];
                net[i] = net[l - i - 1];
                net[l - i - 1] = tmp;
            }
        }
*/
        this.output = output;
        this.net = net;
        this.p = net[0].getInputSize();

        x1 = new double[p+1];
        x1[p] = 1.0;

        int d = output.getOutputSize();
        target = new double[d];
    }

    @Override
    public String toString() {
        return String.format("x(%d) -> %s -> %s", p,
                Arrays.stream(net).map(Object::toString).collect(Collectors.joining(" -> ")),
                output);
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
     * Sets the weight decay factor. After each weight update,
     * every weight is simply "decayed" or shrunk according to
     * w = w * (1 - 2 * eta * lambda).
     */
    public void setWeightDecay(double lambda) {
        if (lambda < 0.0 || lambda > 0.1) {
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
    protected void propagate(double[] x) {
        if (x.length != x1.length - 1) {
            throw new IllegalArgumentException(String.format("Invalid input vector size: %d, expected: %d", x.length, x1.length-1));
        }

        System.arraycopy(x, 0, x1, 0, x.length);

        double[] input = x1;
        for (int i = 0; i < net.length; i++) {
            net[i].propagate(input);
            input = net[i].output();
        }
        output.propagate(input);
    }

    /**
     * Propagates the errors back through the network.
     */
    protected void backpropagate() {
        output.computeError(target, 1.0);

        Layer upper = output;
        for (int i = net.length - 1; i >= 0; i--) {
            double[] error = net[i].gradient();
            upper.backpropagate(error);
            upper = net[i];
        }
        // first hidden layer
        upper.backpropagate(null);
    }

    /** Updates the weights. */
    protected void update() {
        double decay = 1.0 - 2 * eta * lambda;
        if (decay < 0.9) {
            throw new IllegalStateException(String.format("Invalid learning rate (eta = %.2f) and/or decay (lambda = %.2f)", eta, lambda));
        }

        double[] x = x1;
        for (Layer layer : net) {
            layer.computeUpdate(eta, x);
            layer.update(alpha, decay);
            x = layer.output();
        }

        output.computeUpdate(eta, x);
        output.update(alpha, decay);
    }
}

