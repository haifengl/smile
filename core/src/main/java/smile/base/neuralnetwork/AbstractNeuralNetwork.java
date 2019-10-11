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
    protected double lambda = 0.0;
    /**
     * The input layer with bias.
     */
    protected double[] x1;
    /**
     * The buffer to store target value of training instance.
     */
    protected double[] target;
    /**
     * layers of this net
     */
    protected Layer[] net;
    /**
     * The dimensionality of input data.
     */
    protected int p;

    /**
     * Constructor.
     * @param obj the objective function.
     * @param net the layers in the neural network. The input layer should not be included.
     */
    public AbstractNeuralNetwork(ObjectiveFunction obj, Layer... net) {
        if (net.length < 2) {
            throw new IllegalArgumentException("Too few layers: " + net.length);
        }

        for (int i = 1; i < net.length; i++) {
            if (net[i].getInputUnits() != net[i-1].getOutputUnits()) {
                throw new IllegalArgumentException(String.format("Invalid network architecture. Layer %d has %d neurons while layer %d takes %d inputs", i-1, net[i-1].getOutputUnits(), i, net[i].getInputUnits()));
            }
        }

        this.obj = obj;
        this.net = net;
        this.p = net[0].getInputUnits();
        int d = net[net.length-1].getOutputUnits();
        x1 = new double[p+1];
        x1[p] = 1.0;
        target = new double[d];
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
     * w = w * (1 - eta * lambda).
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
    public void propagate(double[] x) {
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
    public void backpropagate(double[] y) {
        computeOutputError(y);
        for (int l = net.length; --l > 0;) {
            net[l].computeUpdate(net[l-1].getOutput(), eta, alpha);
            net[l-1].backpropagate(net[l]);
        }

        net[0].computeUpdate(x1, eta, alpha);
    }

    /** Updates the weights. */
    protected void update() {
        double decay = 1.0 - eta * lambda;
        if (decay <= 0.0) {
            throw new IllegalStateException(String.format("Invalid learning rate (eta = %.2f) and/or decay (lambda = %.2f)", eta, lambda));
        }

        for (Layer layer : net) {
            layer.update();
            if (decay < 1.0) layer.decay(decay);
        }
    }

    /**
     * Compute the network output error.
     * @param target the desired output.
     * @return the error defined by loss function.
     */
    public double computeOutputError(double[] target) {
        return computeOutputError(target, 1.0);
    }

    /**
     * Compute the network output error.
     * @param target the desired output.
     * @param weight a positive weight value associated with the training instance.
     * @return the error defined by loss function.
     */
    public double computeOutputError(double[] target, double weight) {
        Layer outputLayer = net[net.length - 1];

        int units = outputLayer.getOutputUnits();
        if (target.length != units) {
            throw new IllegalArgumentException(String.format("Invalid target vector size: %d, expected: %d", target.length, units));
        }

        double[] output = outputLayer.getOutput();
        double[] error = outputLayer.getError();
        double err = 0.0;
        for (int i = 0; i < units; i++) {
            double out = output[i];
            double g = target[i] - out;

            switch (obj) {
                case LEAST_MEAN_SQUARES:
                    err += 0.5 * g * g;
                    break;

                case CROSS_ENTROPY:
                    switch (outputLayer.getActivation()) {
                        case SOFTMAX:
                            err -= target[i] * log(out);
                            break;

                        case LOGISTIC_SIGMOID:
                            // We have only one output neuron in this case.
                            err = -target[i] * log(out) - (1.0 - target[i]) * log(1.0 - out);
                            g *= out * (1.0 - out);
                            break;

                        default:
                            throw new IllegalArgumentException(String.format("Unsupported activation function %s for objective function %s", outputLayer.getActivation(), obj));
                    }
            }

            error[i] = weight * g;
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

