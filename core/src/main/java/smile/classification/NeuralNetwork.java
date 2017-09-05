/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
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
package smile.classification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.math.Math;

import java.io.Serializable;

/**
 * Multilayer perceptron neural network. 
 * An MLP consists of several layers of nodes, interconnected through weighted
 * acyclic arcs from each preceding layer to the following, without lateral or
 * feedback connections. Each node calculates a transformed weighted linear
 * combination of its inputs (output activations from the preceding layer), with
 * one of the weights acting as a trainable bias connected to a constant input.
 * The transformation, called activation function, is a bounded non-decreasing
 * (non-linear) function, such as the sigmoid functions (ranges from 0 to 1).
 * Another popular activation function is hyperbolic tangent which is actually
 * equivalent to the sigmoid function in shape but ranges from -1 to 1. 
 * More specialized activation functions include radial basis functions which
 * are used in RBF networks.
 * <p>
 * The representational capabilities of a MLP are determined by the range of
 * mappings it may implement through weight variation. Single layer perceptrons
 * are capable of solving only linearly separable problems. With the sigmoid
 * function as activation function, the single-layer network is identical
 * to the logistic regression model.
 * <p>
 * The universal approximation theorem for neural networks states that every
 * continuous function that maps intervals of real numbers to some output
 * interval of real numbers can be approximated arbitrarily closely by a
 * multi-layer perceptron with just one hidden layer. This result holds only
 * for restricted classes of activation functions, which are extremely complex
 * and NOT smooth for subtle mathematical reasons. On the other hand, smoothness
 * is important for gradient descent learning. Besides, the proof is not
 * constructive regarding the number of neurons required or the settings of
 * the weights. Therefore, complex systems will have more layers of neurons
 * with some having increased layers of input neurons and output neurons
 * in practice.
 * <p>
 * The most popular algorithm to train MLPs is back-propagation, which is a
 * gradient descent method. Based on chain rule, the algorithm propagates the
 * error back through the network and adjusts the weights of each connection in
 * order to reduce the value of the error function by some small amount.
 * For this reason, back-propagation can only be applied on networks with
 * differentiable activation functions.
 * <p>
 * During error back propagation, we usually times the gradient with a small
 * number &eta;, called learning rate, which is carefully selected to ensure
 * that the network converges to a local minimum of the error function
 * fast enough, without producing oscillations. One way to avoid oscillation 
 * at large &eta;, is to make the change in weight dependent on the past weight
 * change by adding a momentum term.
 * <p>
 * Although the back-propagation algorithm may performs gradient
 * descent on the total error of all instances in a batch way, 
 * the learning rule is often applied to each instance separately in an online
 * way or stochastic way. There exists empirical indication that the stochastic
 * way results in faster convergence.
 * <p>
 * In practice, the problem of over-fitting has emerged. This arises in
 * convoluted or over-specified systems when the capacity of the network
 * significantly exceeds the needed free parameters. There are two general
 * approaches for avoiding this problem: The first is to use cross-validation
 * and similar techniques to check for the presence of over-fitting and
 * optimally select hyper-parameters such as to minimize the generalization
 * error. The second is to use some form of regularization, which emerges
 * naturally in a Bayesian framework, where the regularization can be
 * performed by selecting a larger prior probability over simpler models;
 * but also in statistical learning theory, where the goal is to minimize over
 * the "empirical risk" and the "structural risk".
 * <p>
 * For neural networks, the input patterns usually should be scaled/standardized.
 * Commonly, each input variable is scaled into interval [0, 1] or to have
 * mean 0 and standard deviation 1.
 * <p>
 * For penalty functions and output units, the following natural pairings are
 * recommended:
 * <ul>
 * <li> linear output units and a least squares penalty function.
 * <li> a two-class cross-entropy penalty function and a logistic
 * activation function.
 * <li> a multi-class cross-entropy penalty function and a softmax
 * activation function.
 * </ul>
 * By assigning a softmax activation function on the output layer of
 * the neural network for categorical target variables, the outputs
 * can be interpreted as posterior probabilities, which are very useful.
 * 
 * @author Haifeng Li
 */
public class NeuralNetwork implements OnlineClassifier<double[]>, SoftClassifier<double[]>, Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(NeuralNetwork.class);

    /**
     * The types of error functions.
     */
    public enum ErrorFunction {
        /**
         * Least mean squares error function.
         */
        LEAST_MEAN_SQUARES,

        /**
         * Cross entropy error function for output as probabilities.
         */
        CROSS_ENTROPY
    }

    /**
     * The types of activation functions in output layer. In this implementation,
     * the hidden layers always employs logistic sigmoid activation function.
     */
    public enum ActivationFunction {
        /**
         * Linear activation function.
         */
        LINEAR,
        
        /**
         * Logistic sigmoid activation function. For multi-class classification,
         * each unit in output layer corresponds to a class. For binary
         * classification and cross entropy error function, there is only
         * one output unit whose value can be regarded as posteriori probability.
         */
        LOGISTIC_SIGMOID,

        /**
         * Softmax activation for multi-class cross entropy objection function.
         * The values of units in output layer can be regarded as posteriori
         * probabilities of each class.
         */
        SOFTMAX
    }


    /**
     * A layer of a feed forward neural network.
     */
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
     * The type of error function of network.
     */
    private ErrorFunction errorFunction = ErrorFunction.LEAST_MEAN_SQUARES;
    /**
     * The type of activation function in output layer.
     */
    private ActivationFunction activationFunction = ActivationFunction.LOGISTIC_SIGMOID;
    /**
     * The dimensionality of data.
     */
    private int p;
    /**
     * The number of classes.
     */
    private int k;
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
     * The buffer to store target value of training instance.
     */
    private double[] target;

    /**
     * Trainer for neural networks.
     */
    public static class Trainer extends ClassifierTrainer<double[]> {
        /**
         * The type of error function of network.
         */        
        private ErrorFunction errorFunction = ErrorFunction.LEAST_MEAN_SQUARES;
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
         * Constructor. The activation function of output layer will be chosen
         * by natural pairing based on the error function and the number of
         * classes.
         * 
         * @param error the error function.
         * @param numUnits the number of units in each layer.
         */
        public Trainer(ErrorFunction error, int... numUnits) {
            this(error, natural(error, numUnits[numUnits.length-1]), numUnits);
        }
        
        /**
         * Constructor.
         * 
         * @param error the error function.
         * @param activation the activation function of output layer.
         * @param numUnits the number of units in each layer.
         */
        public Trainer(ErrorFunction error, ActivationFunction activation, int... numUnits) {
            int numLayers = numUnits.length;
            if (numLayers < 2) {
                throw new IllegalArgumentException("Invalid number of layers: " + numLayers);
            }

            for (int i = 0; i < numLayers; i++) {
                if (numUnits[i] < 1) {
                    throw new IllegalArgumentException(String.format("Invalid number of units of layer %d: %d", i + 1, numUnits[i]));
                }
            }

            if (error == ErrorFunction.LEAST_MEAN_SQUARES) {
                if (activation == ActivationFunction.SOFTMAX) {
                    throw new IllegalArgumentException("Sofmax activation function is invalid for least mean squares error.");
                }
            }

            if (error == ErrorFunction.CROSS_ENTROPY) {
                if (activation == ActivationFunction.LINEAR) {
                    throw new IllegalArgumentException("Linear activation function is invalid with cross entropy error.");
                }

                if (activation == ActivationFunction.SOFTMAX && numUnits[numLayers - 1] == 1) {
                    throw new IllegalArgumentException("Softmax activation function is for multi-class.");
                }

                if (activation == ActivationFunction.LOGISTIC_SIGMOID && numUnits[numLayers - 1] != 1) {
                    throw new IllegalArgumentException("For cross entropy error, logistic sigmoid output is for binary classification.");
                }
            }

            this.errorFunction = error;
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
        public NeuralNetwork train(double[][] x, int[] y) {
            NeuralNetwork net = new NeuralNetwork(errorFunction, activationFunction, numUnits);
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
     * Constructor. The activation function of output layer will be chosen
     * by natural pairing based on the error function and the number of
     * classes.
     * 
     * @param error the error function.
     * @param numUnits the number of units in each layer.
     */
    public NeuralNetwork(ErrorFunction error, int... numUnits) {
        this(error, natural(error, numUnits[numUnits.length-1]), numUnits);
    }

    /**
     * Returns the activation function of output layer based on natural pairing.
     * @param error the error function.
     * @param k the number of output nodes.
     * @return the activation function of output layer based on natural pairing
     */
    private static ActivationFunction natural(ErrorFunction error, int k) {
        if (error == ErrorFunction.CROSS_ENTROPY) {
            if (k == 1) {
                return ActivationFunction.LOGISTIC_SIGMOID;
            } else {
                return ActivationFunction.SOFTMAX;
            }
        } else {
            return ActivationFunction.LOGISTIC_SIGMOID;
        }
        
    }
    
    /**
     * Constructor.
     * 
     * @param error the error function.
     * @param activation the activation function of output layer.
     * @param numUnits the number of units in each layer.
     */
    public NeuralNetwork(ErrorFunction error, ActivationFunction activation, int... numUnits) {
        int numLayers = numUnits.length;
        if (numLayers < 2) {
            throw new IllegalArgumentException("Invalid number of layers: " + numLayers);
        }

        for (int i = 0; i < numLayers; i++) {
            if (numUnits[i] < 1) {
                throw new IllegalArgumentException(String.format("Invalid number of units of layer %d: %d", i+1, numUnits[i]));
            }
        }

        if (error == ErrorFunction.LEAST_MEAN_SQUARES) {
            if (activation == ActivationFunction.SOFTMAX) {
                throw new IllegalArgumentException("Sofmax activation function is invalid for least mean squares error.");
            }
        }

        if (error == ErrorFunction.CROSS_ENTROPY) {
            if (activation == ActivationFunction.LINEAR) {
                throw new IllegalArgumentException("Linear activation function is invalid with cross entropy error.");
            }

            if (activation == ActivationFunction.SOFTMAX && numUnits[numLayers-1] == 1) {
                throw new IllegalArgumentException("Softmax activation function is for multi-class.");
            }

            if (activation == ActivationFunction.LOGISTIC_SIGMOID && numUnits[numLayers-1] != 1) {
                throw new IllegalArgumentException("For cross entropy error, logistic sigmoid output is for binary classification.");
            }
        }

        this.errorFunction = error;
        this.activationFunction = activation;

        if (error == ErrorFunction.CROSS_ENTROPY) {
            this.alpha = 0.0;
            this.lambda = 0.0;
        }

        this.p = numUnits[0];
        this.k = numUnits[numLayers - 1] == 1 ? 2 : numUnits[numLayers - 1];
        this.target = new double[numUnits[numLayers - 1]];

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
        
        copycat.errorFunction = errorFunction;
        copycat.activationFunction = activationFunction;
        copycat.p = p;
        copycat.k = k;
        copycat.eta = eta;
        copycat.alpha = alpha;
        copycat.lambda = lambda;
        copycat.target = target.clone();
        
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
     * Returns the output vector into the given array.
     * @param y the output vector.
     */
    private void getOutput(double[] y) {
        if (y.length != outputLayer.units) {
            throw new IllegalArgumentException(String.format("Invalid output vector size: %d, expected: %d", y.length, outputLayer.units));
        }
        System.arraycopy(outputLayer.output, 0, y, 0, outputLayer.units);
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

            if (upper != outputLayer || activationFunction == ActivationFunction.LOGISTIC_SIGMOID) {
                upper.output[i] = Math.logistic(sum);
            } else {
                if (activationFunction == ActivationFunction.LINEAR || activationFunction == ActivationFunction.SOFTMAX) {
                    upper.output[i] = sum;
                } else {
                    throw new UnsupportedOperationException("Unsupported activation function.");
                }
            }
        }

        if (upper == outputLayer && activationFunction == ActivationFunction.SOFTMAX) {
            softmax();
        }
    }

    /**
     * Calculate softmax activation function in output layer without overflow.
     */
    private void softmax() {
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < outputLayer.units; i++) {
            if (outputLayer.output[i] > max) {
                max = outputLayer.output[i];
            }
        }

        double sum = 0.0;
        for (int i = 0; i < outputLayer.units; i++) {
            double out = Math.exp(outputLayer.output[i] - max);
            outputLayer.output[i] = out;
            sum += out;
        }

        for (int i = 0; i < outputLayer.units; i++) {
            outputLayer.output[i] /= sum;
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
     * Returns natural log without underflow.
     */
    private static double log(double x) {
        double y = 0.0;
        if (x < 1E-300) {
            y = -690.7755;
        } else {
            y = Math.log(x);
        }
        return y;
    }

    /**
     * Compute the network output error.
     * @param output the desired output.
     */
    private double computeOutputError(double[] output) {
        return computeOutputError(output, outputLayer.error);
    }
    
    /**
     * Compute the network output error.
     * @param output the desired output.
     * @param gradient the array to store gradient on output.
     * @return the error defined by loss function.
     */
    private double computeOutputError(double[] output, double[] gradient) {
        if (output.length != outputLayer.units) {
            throw new IllegalArgumentException(String.format("Invalid output vector size: %d, expected: %d", output.length, outputLayer.units));
        }

        double error = 0.0;
        for (int i = 0; i < outputLayer.units; i++) {
            double out = outputLayer.output[i];
            double g = output[i] - out;

            if (errorFunction == ErrorFunction.LEAST_MEAN_SQUARES) {
                error += 0.5 * g * g;
            } else if (errorFunction == ErrorFunction.CROSS_ENTROPY) {
                if (activationFunction == ActivationFunction.SOFTMAX) {
                    error -= output[i] * log(out);
                } else if (activationFunction == ActivationFunction.LOGISTIC_SIGMOID) {
                    // We have only one output neuron in this case.
                    error = -output[i] * log(out) - (1.0 - output[i]) * log(1.0 - out);
                }
            }

            if (errorFunction == ErrorFunction.LEAST_MEAN_SQUARES && activationFunction == ActivationFunction.LOGISTIC_SIGMOID) {
                g *= out * (1.0 - out);
            }

            gradient[i] = g;
        }

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
            lower.error[i] = out * (1.0 - out) * err;
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

    /**
     * Predict the target value of a given instance. Note that this method is NOT
     * multi-thread safe.
     * @param x the instance.
     * @param y the array to store network output on output. For softmax
     * activation function, these are estimated posteriori probabilities.
     * @return the predicted class label.
     */
    @Override
    public int predict(double[] x, double[] y) {
        setInput(x);
        propagate();
        getOutput(y);

        if (outputLayer.units == 1) {
            if (outputLayer.output[0] > 0.5) {
                return 0;
            } else {
                return 1;
            }
        }

        double max = Double.NEGATIVE_INFINITY;
        int label = -1;
        for (int i = 0; i < outputLayer.units; i++) {
            if (outputLayer.output[i] > max) {
                max = outputLayer.output[i];
                label = i;
            }
        }
        return label;
    }

    /**
     * Predict the class of a given instance. Note that this method is NOT
     * multi-thread safe.
     * @param x the instance.
     * @return the predicted class label.
     */
    @Override
    public int predict(double[] x) {
        setInput(x);
        propagate();

        if (outputLayer.units == 1) {
            if (outputLayer.output[0] > 0.5) {
                return 0;
            } else {
                return 1;
            }
        }

        double max = Double.NEGATIVE_INFINITY;
        int label = -1;
        for (int i = 0; i < outputLayer.units; i++) {
            if (outputLayer.output[i] > max) {
                max = outputLayer.output[i];
                label = i;
            }
        }
        return label;
    }

    /**
     * Update the neural network with given instance and associated target value.
     * Note that this method is NOT multi-thread safe.
     * @param x the training instance.
     * @param y the target value.
     * @param weight a positive weight value associated with the training instance.
     * @return the weighted training error before back-propagation.
     */
    public double learn(double[] x, double[] y, double weight) {
        setInput(x);
        propagate();

        double err = weight * computeOutputError(y);

        if (weight != 1.0) {
            for (int i = 0; i < outputLayer.units; i++) {
                outputLayer.error[i] *= weight;
            }
        }
        
        backpropagate();
        adjustWeights();
        return err;
    }

    @Override
    public void learn(double[] x, int y) {
        learn(x, y, 1.0);
    }

    /**
     * Online update the neural network with a new training instance.
     * Note that this method is NOT multi-thread safe.
     * 
     * @param x training instance.
     * @param y training label.
     * @param weight a positive weight value associated with the training instance.
     */
    public void learn(double[] x, int y, double weight) {
        if (weight < 0.0) {
            throw new IllegalArgumentException("Invalid weight: " + weight);            
        }

        if (weight == 0.0) {
            logger.info("Ignore the training instance with zero weight.");
            return;
        }
        
        if (y < 0) {
            throw new IllegalArgumentException("Invalid class label: " + y);
        }

        if (outputLayer.units == 1 && y > 1) {
            throw new IllegalArgumentException("Invalid class label: " + y);
        }

        if (outputLayer.units > 1 && y >= outputLayer.units) {
            throw new IllegalArgumentException("Invalid class label: " + y);
        }

        if (errorFunction == ErrorFunction.CROSS_ENTROPY) {
            if (activationFunction == ActivationFunction.LOGISTIC_SIGMOID) {
                if (y == 0) {
                    target[0] = 1.0;
                } else {
                    target[0] = 0.0;
                }
            } else {
                for (int i = 0; i < target.length; i++) {
                    target[i] = 0.0;
                }
                target[y] = 1.0;
            }
        } else {
            for (int i = 0; i < target.length; i++) {
                target[i] = 0.1;
            }
            target[y] = 0.9;
        }

        learn(x, target, weight);
    }

    /**
     * Trains the neural network with the given dataset for one epoch by
     * stochastic gradient descent.
     * 
     * @param x training instances.
     * @param y training labels in [0, k), where k is the number of classes.
     */
    public void learn(double[][] x, int[] y) {
        int n = x.length;
        int[] index = Math.permutate(n);
        for (int i = 0; i < n; i++) {
            learn(x[index[i]], y[index[i]]);
        }
    }
}
