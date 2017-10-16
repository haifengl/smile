/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 * Modifications copyright (C) 2017 Sam Erickson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile.regression;
 
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.Serializable;
import smile.math.Math;
 
 /**
  * Multilayer perceptron neural network for regression.
  * An MLP consists of several layers of nodes, interconnected through weighted
  * acyclic arcs from each preceding layer to the following, without lateral or
  * feedback connections. Each node calculates a transformed weighted linear
  * combination of its inputs (output activations from the preceding layer), with
  * one of the weights acting as a trainable bias connected to a constant input.
  * The transformation, called activation function, is a bounded non-decreasing
  * (non-linear) function, such as the sigmoid functions (ranges from 0 to 1).
  * Another popular activation function is hyperbolic tangent which is actually
  * equivalent to the sigmoid function in shape but ranges from -1 to 1.
  *
  * @author Sam Erickson
  */
 public class NeuralNetwork implements OnlineRegression<double[]>, Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(NeuralNetwork.class);

    public enum ActivationFunction {
        /**
         * Logistic sigmoid activation function (default): sigma(v)=1/(1+exp(-v))
         */
        LOGISTIC_SIGMOID,
        /**
         * Hyperbolic tangent activation function: f(v)=tanh(v)
         */
        TANH
    }

    private class Layer implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * number of units in this layer
         */
        int units;
        /**
         * output of i<i>th</i> unit
         */
        double[] output;
        /**
         * error term of i<i>th</i> unit
         */
        double[] error;
        /**
         * connection weights to i<i>th</i> unit from previous layer
         */
        double[][] weight;
        /**
         * last weight changes for momentum
         */
        double[][] delta;
    }
    /**
     * The type of activation function in output layer.
     */
    private ActivationFunction activationFunction = ActivationFunction.LOGISTIC_SIGMOID;
    /**
     * The dimensionality of data.
     */
    private int p;
    /**
     * layers of this net
     */
    private Layer[] net;
    /**
     * input layer
     */
    private Layer inputLayer;
    /**
     * output layer
     */
    private Layer outputLayer;
    /**
     * learning rate
     */
    private double eta = 0.1;
    /**
     * momentum factor
     */
    private double alpha = 0.0;
    /**
     * weight decay factor, which is also a regularization term.
     */
    private double lambda = 0.0;

    /**
     * Trainer for neural networks.
     */
    public static class Trainer extends RegressionTrainer<double[]> {
        /**
         * The type of activation function in output layer.
         */
        private ActivationFunction activationFunction = ActivationFunction.LOGISTIC_SIGMOID;
        /**
         * The number of units in each layer.
         */
        private int[] numUnits;
        /**
         * learning rate
         */
        private double eta = 0.1;
        /**
         * momentum factor
         */
        private double alpha = 0.0;
        /**
         * weight decay factor, which is also a regularization term.
         */
        private double lambda = 0.0;
        /**
         * The number of epochs of stochastic learning.
         */
        private int epochs = 25;

        /**
         * Constructor. The default activation function is the logistic sigmoid function.
         *
         * @param numUnits the number of units in each layer.
         */
        public Trainer(int... numUnits) {
            this(ActivationFunction.LOGISTIC_SIGMOID, numUnits);
        }

        /**
         * Constructor.
         *
         * @param activation the activation function of output layer.
         * @param numUnits the number of units in each layer.
         */
        public Trainer(ActivationFunction activation, int... numUnits) {
            int numLayers = numUnits.length;
            if (numLayers < 2) {
                throw new IllegalArgumentException("Invalid number of layers: " + numLayers);
            }

            for (int i = 0; i < numLayers; i++) {
                if (numUnits[i] < 1) {
                    throw new IllegalArgumentException(String.format("Invalid number of units of layer %d: %d", i + 1, numUnits[i]));
                }
            }

            if (numUnits[numLayers - 1]!=1){
                throw new IllegalArgumentException(String.format("Invalid number of units in output layer %d",numUnits[numLayers - 1]));
            }

            this.activationFunction = activation;
            this.numUnits = numUnits;
        }

        /**
         * Sets the learning rate.
         * @param eta the learning rate.
         */
        public Trainer setLearningRate(double eta) {
            if (eta <= 0) {
                throw new IllegalArgumentException("Invalid learning rate: " + eta);
            }
            this.eta = eta;
            return this;
        }

        /**
         * Sets the momentum factor.
         * @param alpha the momentum factor.
         */
        public Trainer setMomentum(double alpha) {
            if (alpha < 0.0 || alpha >= 1.0) {
                throw new IllegalArgumentException("Invalid momentum factor: " + alpha);
            }

            this.alpha = alpha;
            return this;
        }

        /**
         * Sets the weight decay factor. After each weight update, every weight
         * is simply ''decayed'' or shrunk according w = w * (1 - eta * lambda).
         * @param lambda the weight decay for regularization.
         */
        public Trainer setWeightDecay(double lambda) {
            if (lambda < 0.0 || lambda > 0.1) {
                throw new IllegalArgumentException("Invalid weight decay factor: " + lambda);
            }

            this.lambda = lambda;
            return this;
        }

        /**
         * Sets the number of epochs of stochastic learning.
         * @param epochs the number of epochs of stochastic learning.
         */
        public Trainer setNumEpochs(int epochs) {
            if (epochs < 1) {
                throw new IllegalArgumentException("Invalid numer of epochs of stochastic learning:" + epochs);
            }

            this.epochs = epochs;
            return this;
        }

        @Override
        public NeuralNetwork train(double[][] x, double[] y) {
            NeuralNetwork net = new NeuralNetwork(activationFunction, numUnits);
            net.setLearningRate(eta);
            net.setMomentum(alpha);
            net.setWeightDecay(lambda);

            for (int i = 1; i <= epochs; i++) {
                net.learn(x, y);
                logger.info("Neural network learns epoch {}", i);
            }

            return net;
        }
    }

    /**
     * Constructor. The default activation function is the logistic sigmoid function.
     *
     * @param numUnits the number of units in each layer.
     */
    public NeuralNetwork(int... numUnits) {
        this(ActivationFunction.LOGISTIC_SIGMOID, numUnits);
    }
    /**
     * Constructor.
     *
     * @param activation the activation function of output layer.
     * @param numUnits the number of units in each layer.
     */
    public NeuralNetwork(ActivationFunction activation, int... numUnits) {
        this(activation,0.0001,0.9,numUnits);
    }

    /**
     * Constructor.
     *
     * @param activation the activation function of output layer.
     * @param numUnits the number of units in each layer.
     */
    public NeuralNetwork(ActivationFunction activation, double alpha, double lambda, int... numUnits) {
        int numLayers = numUnits.length;
        if (numLayers < 2) {
            throw new IllegalArgumentException("Invalid number of layers: " + numLayers);
        }

        for (int i = 0; i < numLayers; i++) {
            if (numUnits[i] < 1) {
                throw new IllegalArgumentException(String.format("Invalid number of units of layer %d: %d", i+1, numUnits[i]));
            }
        }

        if (numUnits[numLayers - 1]!=1){
            throw new IllegalArgumentException(String.format("Invalid number of units in output layer %d",numUnits[numLayers - 1]));
        }

        this.activationFunction = activation;

        this.alpha = alpha;
        this.lambda = lambda;

        this.p = numUnits[0];

        net = new Layer[numLayers];
        for (int i = 0; i < numLayers; i++) {
            net[i] = new Layer();
            net[i].units = numUnits[i];
            net[i].output = new double[numUnits[i] + 1];
            net[i].error = new double[numUnits[i] + 1];
            net[i].output[numUnits[i]] = 1.0;
        }

        inputLayer = net[0];
        outputLayer = net[numLayers - 1];

        // Initialize random weights.
        for (int l = 1; l < numLayers; l++) {
            net[l].weight = new double[numUnits[l]][numUnits[l - 1] + 1];
            net[l].delta = new double[numUnits[l]][numUnits[l - 1] + 1];
            double r = 1.0 / Math.sqrt(net[l - 1].units);
            for (int i = 0; i < net[l].units; i++) {
                for (int j = 0; j <= net[l - 1].units; j++) {
                    net[l].weight[i][j] = Math.random(-r, r);
                }
            }
        }
    }

    /**
     * Private constructor for clone purpose.
     */
    private NeuralNetwork() {

    }

    @Override
    public NeuralNetwork clone() {
        NeuralNetwork copycat = new NeuralNetwork();

        copycat.activationFunction = activationFunction;
        copycat.p = p;
        copycat.eta = eta;
        copycat.alpha = alpha;
        copycat.lambda = lambda;

        int numLayers = net.length;
        copycat.net = new Layer[numLayers];
        for (int i = 0; i < numLayers; i++) {
            copycat.net[i] = new Layer();
            copycat.net[i].units = net[i].units;
            copycat.net[i].output = net[i].output.clone();
            copycat.net[i].error = net[i].error.clone();
            if (i > 0) {
                copycat.net[i].weight = Math.clone(net[i].weight);
                copycat.net[i].delta = Math.clone(net[i].delta);
            }
        }

        copycat.inputLayer = copycat.net[0];
        copycat.outputLayer = copycat.net[numLayers - 1];

        return copycat;
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
     * Returns the learning rate.
     */
    public double getLearningRate() {
        return eta;
    }

    /**
     * Sets the momentum factor.
     * @param alpha the momentum factor.
     */
    public void setMomentum(double alpha) {
        if (alpha < 0.0 || alpha >= 1.0) {
            throw new IllegalArgumentException("Invalid momentum factor: " + alpha);
        }

        this.alpha = alpha;
    }

    /**
     * Returns the momentum factor.
     */
    public double getMomentum() {
        return alpha;
    }

    /**
     * Sets the weight decay factor. After each weight update, every weight
     * is simply ''decayed'' or shrunk according w = w * (1 - eta * lambda).
     * @param lambda the weight decay for regularization.
     */
    public void setWeightDecay(double lambda) {
        if (lambda < 0.0 || lambda > 0.1) {
            throw new IllegalArgumentException("Invalid weight decay factor: " + lambda);
        }

        this.lambda = lambda;
    }

    /**
     * Returns the weight decay factor.
     */
    public double getWeightDecay() {
        return lambda;
    }

    /**
     * Returns the weights of a layer.
     * @param layer the layer of netural network, 0 for input layer.
     */
    public double[][] getWeight(int layer) {
        return net[layer].weight;
    }

    /**
     * Sets the input vector into the input layer.
     * @param x the input vector.
     */
    private void setInput(double[] x) {
        if (x.length != inputLayer.units) {
            throw new IllegalArgumentException(String.format("Invalid input vector size: %d, expected: %d", x.length, inputLayer.units));
        }
        System.arraycopy(x, 0, inputLayer.output, 0, inputLayer.units);
    }

    /**
     * Propagates signals from a lower layer to the next upper layer.
     * @param lower the lower layer where signals are from.
     * @param upper the upper layer where signals are propagated to.
     */
    private void propagate(Layer lower, Layer upper) {
        for (int i = 0; i < upper.units; i++) {
            double sum = 0.0;
            for (int j = 0; j <= lower.units; j++) {
                sum += upper.weight[i][j] * lower.output[j];
            }

            if (upper == outputLayer) {
                upper.output[i] = sum;
            }

            else {
                if (activationFunction == ActivationFunction.LOGISTIC_SIGMOID) {
                    upper.output[i] = Math.logistic(sum);
                }
                else if (activationFunction==ActivationFunction.TANH){
                    upper.output[i]=(2*Math.logistic(2*sum))-1;
                }
            }
        }
    }

    /**
     * Propagates the signals through the neural network.
     */
    private void propagate() {
        for (int l = 0; l < net.length - 1; l++) {
            propagate(net[l], net[l + 1]);
        }
    }

    /**
     * Compute the network output error.
     * @param output the desired output.
     */
    private double computeOutputError(double output) {
        return computeOutputError(output, outputLayer.error);
    }

    /**
     * Compute the network output error.
     * @param output the desired output.
     * @param gradient the array to store gradient on output.
     * @return the error defined by loss function.
     */
    private double computeOutputError(double output, double[] gradient) {

        double error = 0.0;
        double out = outputLayer.output[0];
        double g = output - out;

        error += (0.5*g * g);


        gradient[0] = g;

        return error;
    }

    /**
     * Propagates the errors back from a upper layer to the next lower layer.
     * @param upper the lower layer where errors are from.
     * @param lower the upper layer where errors are propagated back to.
     */
    private void backpropagate(Layer upper, Layer lower) {
        for (int i = 0; i <= lower.units; i++) {
            double out = lower.output[i];
            double err = 0;
            for (int j = 0; j < upper.units; j++) {
                err += upper.weight[j][i] * upper.error[j];
            }
            if (activationFunction==ActivationFunction.LOGISTIC_SIGMOID) {
                lower.error[i] = out * (1.0 - out) * err;
            }
            else if (activationFunction==ActivationFunction.TANH){
                lower.error[i] = (1-(out*out))*err;
            }
        }
    }

    /**
     * Propagates the errors back through the network.
     */
    private void backpropagate() {
        for (int l = net.length; --l > 0;) {
            backpropagate(net[l], net[l - 1]);
        }
    }

    /**
     * Adjust network weights by back-propagation algorithm.
     */
    private void adjustWeights() {
        for (int l = 1; l < net.length; l++) {
            for (int i = 0; i < net[l].units; i++) {
                for (int j = 0; j <= net[l - 1].units; j++) {
                    double out = net[l - 1].output[j];
                    double err = net[l].error[i];
                    double delta = (1 - alpha) * eta * err * out + alpha * net[l].delta[i][j];
                    net[l].delta[i][j] = delta;
                    net[l].weight[i][j] += delta;
                    if (lambda != 0.0 && j < net[l-1].units) {
                        net[l].weight[i][j] *= (1.0 - eta * lambda);
                    }
                }
            }
        }
    }

    @Override
    public double predict(double[] x) {
        setInput(x);
        propagate();
        return outputLayer.output[0];
    }


    /**
     * Update the neural network with given instance and associated target value.
     * Note that this method is NOT multi-thread safe.
     * @param x the training instance.
     * @param y the target value.
     * @param weight a positive weight value associated with the training instance.
     * @return the weighted training error before back-propagation.
     */
    public double learn(double[] x, double y, double weight) {
        setInput(x);
        propagate();

        double err = weight * computeOutputError(y);

        if (weight != 1.0) {
            outputLayer.error[0] *= weight;
        }

        backpropagate();
        adjustWeights();
        return err;
    }

    @Override
    public void learn(double[] x, double y) {
        learn(x, y, 1.0);
    }


    /**
     * Trains the neural network with the given dataset for one epoch by
     * stochastic gradient descent.
     *
     * @param x training instances.
     * @param y training labels in [0, k), where k is the number of classes.
     */
    public void learn(double[][] x, double[] y) {
        int n = x.length;
        int[] index = Math.permutate(n);
        for (int i = 0; i < n; i++) {
            learn(x[index[i]], y[index[i]]);
        }
    }
}

