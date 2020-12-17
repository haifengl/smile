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

import smile.math.TimeFunction;

import java.io.IOException;
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
    protected transient ThreadLocal<double[]> target;
    /**
     * The learning rate.
     */
    protected TimeFunction learningRate = TimeFunction.constant(0.01);
    /**
     * The momentum factor.
     */
    protected TimeFunction momentum = TimeFunction.constant(0.0);
    /**
     * The discounting factor for the history/coming gradient in RMSProp.
     */
    protected double rho = 0.0;
    /**
     * A small constant for numerical stability in RMSProp.
     */
    protected double epsilon = 1E-07;
    /**
     * The L2 regularization factor, which is also the weight decay factor.
     */
    protected double lambda = 0.0;
    /**
     * The training iterations.
     */
    protected int t = 0;

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
        target = new ThreadLocal<double[]>() {
            protected synchronized double[] initialValue() {
                return new double[output.getOutputSize()];
            }
        };
    }

    @Override
    public String toString() {
        return String.format("x(%d) -> %s -> %s(learning rate = %s, momentum = %s, weight decay = %.2f)", p,
                Arrays.stream(net).map(Object::toString).collect(Collectors.joining(" -> ")),
                output, learningRate, momentum, lambda);
    }

    /**
     * Sets the learning rate.
     * @param rate the learning rate.
     */
    public void setLearningRate(TimeFunction rate) {
        this.learningRate = rate;
    }

    /**
     * Sets the momentum factor. momentum = 0.0 means no momentum.
     * @param momentum the momentum factor.
     */
    public void setMomentum(TimeFunction momentum) {
        this.momentum = momentum;
    }

    /**
     * Sets RMSProp parameters.
     * @param rho The discounting factor for the history/coming gradient.
     * @param epsilon A small constant for numerical stability.
     */
    public void setRMSProp(double rho, double epsilon) {
        if (rho < 0.0 || rho >= 1.0) {
            throw new IllegalArgumentException("Invalid rho = " + rho);
        }

        if (epsilon <= 0.0) {
            throw new IllegalArgumentException("Invalid epsilon = " + epsilon);
        }

        this.rho = rho;
        this.epsilon = epsilon;
    }

    /**
     * Sets the weight decay factor. After each weight update,
     * every weight is simply "decayed" or shrunk according to
     * w = w * (1 - 2 * eta * lambda).
     * @param lambda the weight decay factor.
     */
    public void setWeightDecay(double lambda) {
        if (lambda < 0.0) {
            throw new IllegalArgumentException("Invalid weight decay factor: " + lambda);
        }

        this.lambda = lambda;
    }

   /**
    * Returns the learning rate.
    * @return the learning rate.
    */
    public double getLearningRate() {
        return learningRate.apply(t);
    }

    /**
     * Returns the momentum factor.
     * @return the momentum factor.
     */
    public double getMomentum() {
        return momentum.apply(t);
    }

    /**
     * Returns the weight decay factor.
     * @return the weight decay factor.
     */
    public double getWeightDecay() {
        return lambda;
    }

    /**
     * Propagates the signals through the neural network.
     * @param x the input signal.
     */
    protected void propagate(double[] x) {
        double[] input = x;
        for (Layer layer : net) {
            layer.propagate(input);
            input = layer.output();
        }
        output.propagate(input);
    }

    /**
     * Propagates the errors back through the network.
     * @param x the input signal.
     * @param update the flag if update the weights directly.
     *               It should be false for (mini-)batch.
     */
    protected void backpropagate(double[] x, boolean update) {
        output.computeOutputGradient(target.get(), 1.0);

        Layer upper = output;
        for (int i = net.length - 1; i >= 0; i--) {
            upper.backpropagate(net[i].gradient());
            upper = net[i];
        }
        // first hidden layer
        upper.backpropagate(null);

        if (update) {
            double eta = learningRate.apply(t);
            if (eta <= 0) {
                throw new IllegalArgumentException("Invalid learning rate: " + eta);
            }

            double alpha = momentum.apply(t);
            if (alpha < 0.0 || alpha >= 1.0) {
                throw new IllegalArgumentException("Invalid momentum factor: " + alpha);
            }

            double decay = 1.0 - 2 * eta * lambda;
            if (decay < 0.9) {
                throw new IllegalStateException(String.format("Invalid learning rate (eta = %.2f) and/or L2 regularization (lambda = %.2f) such that weight decay = %.2f", eta, lambda, decay));
            }

            for (Layer layer : net) {
                layer.computeGradientUpdate(x, eta, alpha, decay);
                x = layer.output();
            }

            output.computeGradientUpdate(x, eta, alpha, decay);
        } else {
            for (Layer layer : net) {
                layer.computeGradient(x);
                x = layer.output();
            }

            output.computeGradient(x);
        }
    }

    /**
     * Updates the weights for mini-batch training.
     *
     * @param m the mini-batch size.
     */
    protected void update(int m) {
        double eta = learningRate.apply(t);
        if (eta <= 0) {
            throw new IllegalArgumentException("Invalid learning rate: " + eta);
        }

        double alpha = momentum.apply(t);
        if (alpha < 0.0 || alpha >= 1.0) {
            throw new IllegalArgumentException("Invalid momentum factor: " + alpha);
        }

        double decay = 1.0 - 2 * eta * lambda;
        if (decay < 0.9) {
            throw new IllegalStateException(String.format("Invalid learning rate (eta = %.2f) and/or decay (lambda = %.2f)", eta, lambda));
        }

        for (Layer layer : net) {
            layer.update(m, eta, alpha, decay, rho, epsilon);
        }

        output.update(m, eta, alpha, decay, rho, epsilon);
    }
}

