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
import java.util.stream.Collectors;

/**
 * Fully connected multilayer perceptron neural network.
 * An MLP consists of at least three layers of nodes: an input layer,
 * a hidden layer and an output layer. The nodes are interconnected
 * through weighted acyclic arcs from each preceding layer to the
 * following, without lateral or feedback connections. Each node
 * calculates a transformed weighted linear combination of its inputs
 * (output activations from the preceding layer), with one of the weights
 * acting as a trainable bias connected to a constant input. The
 * transformation, called activation function, is a bounded non-decreasing
 * (non-linear) function.
 *
 * @author Haifeng Li
 */
public abstract class MultilayerPerceptron implements Serializable {
    private static final long serialVersionUID = 2L;
    /**
     * The dimensionality of input data.
     */
    protected int p;
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
     * @param net the layers from bottom to top.
     *            The input layer should not be included.
     */
    public MultilayerPerceptron(Layer... net) {
        if (net.length < 2) {
            throw new IllegalArgumentException("Too few layers: " + net.length);
        }

        Layer lower = net[0];
        for (int i = 1; i < net.length; i++) {
            Layer layer = net[i];
            if (layer.getInputSize() != lower.getOutputSize()) {
                throw new IllegalArgumentException(String.format(
                        "Invalid network architecture. Layer %d has %d neurons while layer %d takes %d inputs",
                        i-1, lower.getOutputSize(),
                        i, layer.getInputSize()));
            }
            lower = layer;
        }

        this.output = (OutputLayer) net[net.length - 1];
        this.net = Arrays.copyOf(net, net.length - 1);
        this.p = net[0].getInputSize();

        target = new double[output.getOutputSize()];
    }

    @Override
    public String toString() {
        return String.format("x(%d) -> %s -> %s(eta = %.2f, alpha = %.2f, lambda = %.2f)", p,
                Arrays.stream(net).map(Object::toString).collect(Collectors.joining(" -> ")),
                output, eta, alpha, lambda);
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
        double[] input = x;
        for (int i = 0; i < net.length; i++) {
            net[i].propagate(input);
            input = net[i].output();
        }
        output.propagate(input);
    }

    /**
     * Propagates the errors back through the network.
     */
    protected void backpropagate(double[] x) {
        output.computeError(target, 1.0);

        Layer upper = output;
        for (int i = net.length - 1; i >= 0; i--) {
            double[] error = net[i].gradient();
            upper.backpropagate(error);
            upper = net[i];
        }
        // first hidden layer
        upper.backpropagate(null);

        for (Layer layer : net) {
            layer.computeUpdate(eta, alpha, x);
            x = layer.output();
        }

        output.computeUpdate(eta, alpha, x);
    }

    /** Updates the weights. */
    protected void update() {
        double decay = 1.0 - 2 * eta * lambda;
        if (decay < 0.9) {
            throw new IllegalStateException(String.format("Invalid learning rate (eta = %.2f) and/or decay (lambda = %.2f)", eta, lambda));
        }

        for (Layer layer : net) {
            layer.update(alpha, decay);
        }

        output.update(alpha, decay);
    }
}

