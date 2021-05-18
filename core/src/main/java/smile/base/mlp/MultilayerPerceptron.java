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
import java.util.Properties;
import java.util.stream.Collectors;
import smile.math.MathEx;
import smile.math.TimeFunction;

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
     * The input and hidden layers.
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
    protected TimeFunction momentum = null;
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
     * The gradient clipping value.
     */
    protected double clipValue = 0.0;
    /**
     * The gradient clipping norm.
     */
    protected double clipNorm = 0.0;
    /**
     * The training iterations.
     */
    protected int t = 0;

    /**
     * Constructor.
     * @param net the input layer, hidden layers, and output layer in order.
     */
    public MultilayerPerceptron(Layer... net) {
        if (net.length <= 2) {
            throw new IllegalArgumentException("Too few layers: " + net.length);
        }

        if (!(net[0] instanceof InputLayer)) {
            throw new IllegalArgumentException("The first layer is not an InputLayer: " + net[0]);
        }

        if (!(net[net.length-1] instanceof OutputLayer)) {
            throw new IllegalArgumentException("The last layer is not an OutputLayer: " + net[net.length-1]);
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
        target = ThreadLocal.withInitial(() -> new double[output.getOutputSize()]);
    }

    @Override
    public String toString() {
        String s = String.format("%s -> %s(learning rate = %s",
                Arrays.stream(net).map(Object::toString).collect(Collectors.joining(" -> ")),
                output, learningRate);

        if (momentum != null) {
            s = String.format("%s, momentum = %s", s, momentum);
        }

        if (lambda != 0.0) {
            s = String.format("%s, weight decay = %f", s, lambda);
        }

        if (rho != 0.0) {
            s = String.format("%s, RMSProp = %f", s, rho);
        }

        return s + ")";
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
     * Sets the gradient clipping value. If clip value is set, the gradient of
     * each weight is clipped to be no higher than this value.
     * @param clipValue the gradient clipping value.
     */
    public void setClipValue(double clipValue) {
        if (clipValue < 0.0) {
            throw new IllegalArgumentException("Invalid gradient clipping value: " + clipValue);
        }

        this.clipValue = clipValue;
    }

    /**
     * Sets the gradient clipping norm. If clip norm is set, the gradient of
     * each weight is individually clipped so that its norm is no higher than
     * this value.
     * @param clipNorm the gradient clipping norm.
     */
    public void setClipNorm(double clipNorm) {
        if (clipNorm < 0.0) {
            throw new IllegalArgumentException("Invalid gradient clipping norm: " + clipNorm);
        }

        this.clipNorm = clipNorm;
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
        return momentum == null ? 0.0 : momentum.apply(t);
    }

    /**
     * Returns the weight decay factor.
     * @return the weight decay factor.
     */
    public double getWeightDecay() {
        return lambda;
    }

    /**
     * Returns the gradient clipping value.
     * @return the gradient clipping value.
     */
    public double getClipValue() {
        return clipValue;
    }

    /**
     * Returns the gradient clipping norm.
     * @return the gradient clipping norm.
     */
    public double getClipNorm() {
        return clipNorm;
    }

    /**
     * Propagates the signals through the neural network.
     * @param x the input signal.
     * @param train true if this is in training pass.
     */
    protected void propagate(double[] x, boolean train) {
        double[] input = x;
        for (Layer layer : net) {
            layer.propagate(input);
            if (train) {
                layer.propagateDropout();
            }
            input = layer.output();
        }
        output.propagate(input);
    }

    /**
     * Gradient clipping prevents exploding gradients in very deep networks,
     * usually in recurrent neural networks.
     * @param gradient the gradient vector.
     */
    private void clipGradient(double[] gradient) {
        if (clipNorm > 0.0) {
            double norm = MathEx.norm(gradient);
            if (norm > clipNorm) {
                double scale = clipNorm / norm;
                for (int j = 0; j < gradient.length; j++) {
                    gradient[j] *= scale;
                }
            }
        } else if (clipValue > 0.0) {
            for (int j = 0; j < gradient.length; j++) {
                if (gradient[j] > clipValue) {
                    gradient[j] = clipValue;
                } else if (gradient[j] < -clipValue) {
                    gradient[j] = -clipValue;
                }
            }
        }
    }

    /**
     * Propagates the errors back through the network.
     * @param update the flag if update the weights directly.
     *               It should be false for (mini-)batch.
     */
    protected void backpropagate(boolean update) {
        output.computeOutputGradient(target.get(), 1.0);
        clipGradient(output.gradient());

        Layer upper = output;
        for (int i = net.length; --i > 0;) {
            upper.backpropagate(net[i].gradient());
            upper = net[i];
            upper.backpopagateDropout();
            clipGradient(upper.gradient());
        }
        // first hidden layer
        upper.backpropagate(null);

        if (update) {
            double eta = getLearningRate();
            if (eta <= 0) {
                throw new IllegalArgumentException("Invalid learning rate: " + eta);
            }

            double alpha = getMomentum();
            if (alpha < 0.0 || alpha >= 1.0) {
                throw new IllegalArgumentException("Invalid momentum factor: " + alpha);
            }

            double decay = 1.0 - 2 * eta * lambda;
            if (decay < 0.9) {
                throw new IllegalStateException(String.format("Invalid learning rate (eta = %.2f) and/or L2 regularization (lambda = %.2f) such that weight decay = %.2f", eta, lambda, decay));
            }

            double[] x = net[0].output();
            for (int i = 1; i < net.length; i++) {
                Layer layer = net[i];
                layer.computeGradientUpdate(x, eta, alpha, decay);
                x = layer.output();
            }

            output.computeGradientUpdate(x, eta, alpha, decay);
        } else {
            double[] x = net[0].output();
            for (int i = 1; i < net.length; i++) {
                Layer layer = net[i];
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
        double eta = getLearningRate();
        if (eta <= 0) {
            throw new IllegalArgumentException("Invalid learning rate: " + eta);
        }

        double alpha = getMomentum();
        if (alpha < 0.0 || alpha >= 1.0) {
            throw new IllegalArgumentException("Invalid momentum factor: " + alpha);
        }

        double decay = 1.0 - 2 * eta * lambda;
        if (decay < 0.9) {
            throw new IllegalStateException(String.format("Invalid learning rate (eta = %.2f) and/or decay (lambda = %.2f)", eta, lambda));
        }

        for (int i = 1; i < net.length; i++) {
            net[i].update(m, eta, alpha, decay, rho, epsilon);
        }

        output.update(m, eta, alpha, decay, rho, epsilon);
    }

    /**
     * Sets MLP hyper-parameters such as learning rate, weight decay, momentum,
     * RMSProp, etc.
     * @param params the MLP hyper-parameters.
     */
    public void setParameters(Properties params) {
        String learningRate = params.getProperty("smile.mlp.learning_rate");
        if (learningRate != null) {
            setLearningRate(TimeFunction.of(learningRate));
        }

        String weightDecay = params.getProperty("smile.mlp.weight_decay");
        if (weightDecay != null) {
            setWeightDecay(Double.parseDouble(weightDecay));
        }

        String momentum = params.getProperty("smile.mlp.momentum");
        if (momentum != null) {
            setMomentum(TimeFunction.of(momentum));
        }

        String clipValue = params.getProperty("smile.mlp.clip_value");
        if (clipValue != null) {
            setClipValue(Double.parseDouble(clipValue));
        }

        String clipNorm = params.getProperty("smile.mlp.clip_norm");
        if (clipNorm != null) {
            setClipNorm(Double.parseDouble(clipNorm));
        }

        String rho = params.getProperty("smile.mlp.RMSProp.rho");
        if (rho != null) {
            double epsilon = Double.parseDouble(params.getProperty("smile.mlp.RMSProp.epsilon", "1E-7"));
            setRMSProp(Double.parseDouble(rho), epsilon);
        }
    }
}

