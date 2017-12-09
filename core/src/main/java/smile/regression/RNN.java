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

import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.Serializable;
import smile.math.Math;
 
 /**
  * Recurrent neural network for regression.
  * An RNN consists of several layers of nodes, which are a combination of
  * interconnected and weighted arcs from each preceding layer to 
  * the following, and weighted context units which are the previous 
  * outputs of the layer i<i>th</i> to itself after the previous training 
  * instance has been propagated through the network. The context units are 
  * analogous to memory, and make RNNs useful for predicting sequential data.
  * An example use case for RNNs in regression is in time series forecasting.
  * 
  * A common algorithm to train RNNs is truncated back-propagation 
  * through time (TBPTT), which is a gradient descent method. Based on chain 
  * rule, the algorithm propagates the error back through the unfolded network 
  * in time and adjusts the weights of each connection in order to reduce the 
  * value of the error function.
  * 
  * For neural networks, the data should be scaled/standardized.
  * Commonly, each input variable is scaled into interval [0, 1] or to have
  * mean 0 and standard deviation 1.
  * 
  * <h2>References</h2>
  * <ol>
  * <li> http://cs231n.stanford.edu/slides/2017/cs231n_2017_lecture10.pdf </li>
  * </ol>
  *
  * @author Sam Erickson
  */
public class RNN implements Regression<double[]>, Serializable  {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(RNN.class);

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
         * time step t<i>th</i> outputs of i<i>th</i> unit 
         */
        double[][] output;
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
        /**
         * total gradient for non-recurrent weights
         */
        double[][] nonRecurrentTotalGradient;
        /**
         * determines if layer is recurrent or not
         */
        boolean recurrent;
        /**
         * connection weights from i<i>th</i> layer to itself 
         * (dimensions of net[i].units x net[i].units)
         */
        double[][] recurrentWeight;
        /**
         * error from back-propagation at step t+1 for computing error
         * in back-propagation in step t
         */
        double[] backwardsError;
        /**
         * last recurrent weight changes for momentum
         */
        double[][] recurrentDelta;
        /**
         * total gradient for recurrent weights
         */
        double[][] recurrentTotalGradient;
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
     * buffer to hold output gradients yhat - y
     */
    private double[] gradientBuffer;
    /**
     * buffer to hold x instances for bptt
     */
    private double[][] xBuffer;
    /**
     * buffer to hold y instances for bptt
     */
    private double[] yBuffer;
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
    private double eta = 0.05;
    /**
     * momentum factor
     */
    private double alpha = 0.0;
    /**
     * weight decay factor, which is also a regularization term.
     */
    private double lambda = 0.0;
    /**
     * number of steps to use for truncated BPTT
     */
    private int steps;
    /**
     * determines the if the state of the RNN is kept after each update
     * to weights
     */
    private boolean keepState;

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
         * booleans determining if the i<i>th</i> layer is recurrent.
         */
        private boolean[] recurrentLayers;
        /**
         * learning rate
         */
        private double eta = 0.05;
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
         * @param recurrentLayers booleans determining if the i<i>th</i> layer is recurrent.
         */
        public Trainer(int[] numUnits, boolean[] recurrentLayers) {
            this(ActivationFunction.LOGISTIC_SIGMOID, numUnits, recurrentLayers);
        }

        /**
         * Constructor.
         *
         * @param activation the activation function of output layer.
         * @param numUnits the number of units in each layer.
         * @param recurrentLayers booleans determining if the i<i>th</i> layer is recurrent.
         */
        public Trainer(ActivationFunction activation, int[] numUnits, boolean[] recurrentLayers) {
            int numLayers = numUnits.length;
            int numRecurrentSpecified = recurrentLayers.length;
            if (numLayers < 2) {
                throw new IllegalArgumentException(String.format("Invalid number of layers: %d", numLayers));
            }

            if (numRecurrentSpecified != numLayers){
                throw new IllegalArgumentException(String.format("Number of layers %d not equal to number of recurrent layers specified %d", numLayers, numRecurrentSpecified));
            }

            if (recurrentLayers[0] || recurrentLayers[numRecurrentSpecified-1]){
                throw new IllegalArgumentException("Only hidden layers can be recurrent");
            }

            int recurrentCt = 0;
            for (int i = 0; i < numLayers; i++) {
                if (numUnits[i] < 1) {
                    throw new IllegalArgumentException(String.format("Invalid number of units of layer %d: %d", i+1, numUnits[i]));
                }
                if (recurrentLayers[i]){
                    recurrentCt += 1;
                }
            }

            if (recurrentCt < 1){
                throw new IllegalArgumentException(String.format("Invalid number of recurrent layers: %d", recurrentCt));
            }

            if (numUnits[numLayers - 1]!=1){
                throw new IllegalArgumentException(String.format("Invalid number of units in output layer %d",numUnits[numLayers - 1]));
            }

            this.activationFunction = activation;
            this.numUnits = numUnits;
            this.recurrentLayers = recurrentLayers;
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
        public RNN train(double[][] x, double[] y) {
            RNN net = new RNN(activationFunction, 3, true, numUnits, recurrentLayers);
            net.setLearningRate(eta);
            net.setMomentum(alpha);
            net.setWeightDecay(lambda);

            for (int i = 1; i <= epochs; i++) {
                net.learn(x, y);
                net.resetState();
                logger.info("RNN learns epoch {}", i);
            }
            net.learn(x, y);
            logger.info("RNN learns epoch {}", epochs);

            return net;
        }
    }

    /**
     * Constructor. The default activation function is the logistic sigmoid function.
     *
     * @param steps the number of steps to use in truncated backpropagation through time.
     * @param numUnits the number of units in each layer.
     * @param recurrentLayers booleans determining if the i<i>th</i> layer is recurrent.
     */
    public RNN(int steps, int[] numUnits, boolean[] recurrentLayers) {
        this(ActivationFunction.LOGISTIC_SIGMOID, steps, false, numUnits, recurrentLayers);
    }

    /**
     * Constructor.
     *
     * @param activation the activation function of the output layer.
     * @param steps the number of steps to use in truncated backpropagation through time.
     * @param keepState determines if state is reset after each update to weights
     * @param numUnits the number of units in each layer.
     * @param recurrentLayers booleans determining if the i<i>th</i> layer is recurrent.
     */
    public RNN(ActivationFunction activation, int steps, boolean keepState, int[] numUnits, boolean[] recurrentLayers) {
        int numLayers = numUnits.length;
        int numRecurrentSpecified = recurrentLayers.length;
        if (steps < 2){
            throw new IllegalArgumentException(String.format("Invalid number of truncated BPTT steps: %d", steps));
        }
        
        if (numLayers < 2) {
            throw new IllegalArgumentException(String.format("Invalid number of layers: %d", numLayers));
        }

        if (numRecurrentSpecified != numLayers){
            throw new IllegalArgumentException(String.format("Number of layers %d not equal to number of recurrent layers specified %d", numLayers, numRecurrentSpecified));
        }
        
        if (recurrentLayers[0] || recurrentLayers[numRecurrentSpecified-1]){
            throw new IllegalArgumentException("Only hidden layers can be recurrent");
        }
        
        int recurrentCt = 0;
        for (int i = 0; i < numLayers; i++) {
            if (numUnits[i] < 1) {
                throw new IllegalArgumentException(String.format("Invalid number of units of layer %d: %d", i+1, numUnits[i]));
            }
            if (recurrentLayers[i]){
                recurrentCt += 1;
            }
        }
        
        if (recurrentCt < 1){
            throw new IllegalArgumentException(String.format("Invalid number of recurrent layers: %d", recurrentCt));
        }

        if (numUnits[numLayers - 1]!=1){
            throw new IllegalArgumentException(String.format("Invalid number of units in output layer %d",numUnits[numLayers - 1]));
        }

        this.activationFunction = activation;
        
        this.steps = steps;
        this.keepState = keepState;
        this.p = numUnits[0];
        gradientBuffer = new double[steps];
        xBuffer = new double[steps][p];
        yBuffer = new double[steps];
        
        net = new Layer[numLayers];
        for (int i = 0; i < numLayers; i++) {
            net[i] = new Layer();
            net[i].units = numUnits[i];
            net[i].output = new double[steps][numUnits[i]];
            net[i].error = new double[numUnits[i]];
            net[i].recurrent = recurrentLayers[i];
            if (net[i].recurrent){
                net[i].backwardsError = new double[numUnits[i]];
                net[i].recurrentWeight = new double[numUnits[i]][numUnits[i]];
                net[i].recurrentDelta = new double[numUnits[i]][numUnits[i]];
                net[i].recurrentTotalGradient = new double[numUnits[i]][numUnits[i]];
                double r = 1.0 / Math.sqrt(net[i].units);
                for (int j = 0; j < numUnits[i]; j++){
                    for (int k = 0; k < numUnits[i]; k++){
                        net[i].recurrentWeight[j][k] = Math.random(-r, r);
                    }
                }
            }
        }

        inputLayer = net[0];
        outputLayer = net[numLayers - 1];

        // Initialize random weights.
        for (int l = 1; l < numLayers; l++) {
            net[l].weight = new double[numUnits[l]][numUnits[l - 1]];
            net[l].delta = new double[numUnits[l]][numUnits[l - 1]];
            net[l].nonRecurrentTotalGradient = new double[numUnits[l]][numUnits[l - 1]];
            double r = 1.0 / Math.sqrt(net[l - 1].units);
            for (int i = 0; i < net[l].units; i++) {
                for (int j = 0; j < net[l - 1].units; j++) {
                    net[l].weight[i][j] = Math.random(-r, r);
                }
            }
        }
    }

    /**
     * Private constructor for clone purpose.
     */
    private RNN() {

    }

    @Override
    public RNN clone() {
        RNN copycat = new RNN();

        copycat.activationFunction = activationFunction;
        copycat.p = p;
        copycat.eta = eta;
        copycat.alpha = alpha;
        copycat.lambda = lambda;
        copycat.steps = steps;
        copycat.gradientBuffer = gradientBuffer.clone();
        copycat.yBuffer = yBuffer.clone();
        copycat.xBuffer = Math.clone(xBuffer);
        copycat.keepState = keepState;

        int numLayers = net.length;
        copycat.net = new Layer[numLayers];
        for (int i = 0; i < numLayers; i++) {
            copycat.net[i] = new Layer();
            copycat.net[i].units = net[i].units;
            copycat.net[i].output = Math.clone(net[i].output);
            copycat.net[i].error = net[i].error.clone();
            copycat.net[i].recurrent = net[i].recurrent;
            if (i > 0) {
                copycat.net[i].weight = Math.clone(net[i].weight);
                copycat.net[i].delta = Math.clone(net[i].delta);
                copycat.net[i].nonRecurrentTotalGradient = Math.clone(net[i].nonRecurrentTotalGradient);
                if (copycat.net[i].recurrent){
                    copycat.net[i].recurrentWeight = Math.clone(net[i].recurrentWeight);
                    copycat.net[i].backwardsError = net[i].backwardsError.clone();
                    copycat.net[i].recurrentDelta = Math.clone(net[i].recurrentDelta);
                    copycat.net[i].recurrentTotalGradient = Math.clone(net[i].recurrentTotalGradient);
                }
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
     * @param layer the layer of neural network, 0 for input layer.
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
        System.arraycopy(inputLayer.output, 1, inputLayer.output, 0, steps - 1);
        System.arraycopy(x, 0, inputLayer.output[steps - 1], 0, inputLayer.units);
    }

    /**
     * Propagates signals from a lower layer to the next upper layer.
     * @param lower the lower layer where signals are from.
     * @param upper the upper layer where signals are propagated to.
     */
    private void propagate(Layer lower, Layer upper) {
        // Shift all output from all previous time steps in upper layer down one
        System.arraycopy(upper.output, 1, upper.output, 0, steps - 1);
        
        for (int i = 0; i < upper.units; i++) {
            double sum = 0;
            if (upper.recurrent){
                sum += Math.dot(upper.recurrentWeight[i], upper.output[steps - 2]);
            }
            for (int j = 0; j < lower.units; j++) {
                sum += upper.weight[i][j] * lower.output[steps - 1][j];
            }
            
            if (upper == outputLayer) {
                upper.output[steps - 1][i] = sum;
            }

            else {
                if (activationFunction == ActivationFunction.LOGISTIC_SIGMOID) {
                    upper.output[steps - 1][i] = Math.logistic(sum);
                }
                else if (activationFunction==ActivationFunction.TANH){
                    upper.output[steps - 1][i] = (2*Math.logistic(2*sum))-1;;
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
     * @param t the step to compute the error at
     * @return the error defined by loss function.
     */
    private double computeOutputError(int t) {
        double yhat = outputLayer.output[t][0];
        double y = yBuffer[t];
        double gradient = y - yhat;
        gradientBuffer[t] = gradient;
        
        double error = 0.5 * gradient * gradient;
        return error;
    }

    /**
     * Propagates the errors back from a upper layer to the next lower layer.
     * @param upper the lower layer where errors are from.
     * @param lower the upper layer where errors are propagated back to.
     * @param t the current time step used in truncated BPTT.
     */
    private void backpropagate(Layer upper, Layer lower, int t) {
        for (int i = 0; i < lower.units; i++) {
            double out = lower.output[t][i];
            double err = 0;
            for (int j = 0; j < upper.units; j++) {
                err += upper.weight[j][i] * upper.error[j];
            }
            if (lower.recurrent){
                err += lower.backwardsError[i];
            }
            if (activationFunction==ActivationFunction.LOGISTIC_SIGMOID) {
                lower.error[i] = out * (1.0 - out) * err;
            }
            else if (activationFunction==ActivationFunction.TANH){
                lower.error[i] = (1-(out*out))*err;
            }
        }
        
        if (lower.recurrent){
            Arrays.fill(lower.backwardsError, 0);
            for (int j = 0; j < lower.units; j++){
                for (int i = 0; i < lower.units; i++){
                    lower.backwardsError[j] += lower.recurrentWeight[i][j] * lower.error[i];
                }
            }
        }
    }

    /**
     * Propagates the errors back through the network.
     * @param t the current time step used in truncated BPTT.
     */
    private void backpropagate(int t) {
        for (int l = net.length; --l > 0;) {
            backpropagate(net[l], net[l - 1], t);
        }
    }

    /**
     * Compute the total gradient across time.
     * @param t the current time step used in truncated BPTT.
     */
    private void computeTotalGradient(int t) {
        for (int l = 1; l < net.length; l++) {
            for (int i = 0; i < net[l].units; i++) {
                for (int j = 0; j < net[l - 1].units; j++) {
                    double out = net[l - 1].output[t][j];
                    double err = net[l].error[i];
                    double weightDelta = err * out;
                    net[l].nonRecurrentTotalGradient[i][j] += weightDelta;
                }
                if (net[l].recurrent && t >= 1){
                    for (int j = 0; j < net[l].units; j++){
                        double out = net[l].output[t - 1][j];
                        double err = net[l].error[i];
                        double recurrentDelta = err * out;
                        net[l].recurrentTotalGradient[i][j] += recurrentDelta;
                    }
                }
            }
        }
    }
    
    /**
     * Adjust network weights after back-propagation through time.
     */
    private void adjustWeights() {
        for (int l = 1; l < net.length; l++) {
            for (int i = 0; i < net[l].units; i++) {
                for (int j = 0; j < net[l - 1].units; j++) {
                    net[l].weight[i][j] += (1 - alpha) * eta *net[l].nonRecurrentTotalGradient[i][j] + alpha * net[l].delta[i][j];
                    net[l].delta[i][j] = net[l].nonRecurrentTotalGradient[i][j];
                    if (lambda != 0.0 && j < net[l-1].units) {
                        net[l].weight[i][j] *= (1.0 - eta * lambda);
                    }

                }
                if (net[l].recurrent){
                    for (int j = 0; j < net[l].units; j++){
                        net[l].recurrentWeight[i][j] += (1 - alpha) * eta * net[l].recurrentTotalGradient[i][j] + alpha * net[l].recurrentDelta[i][j];
                        net[l].recurrentDelta[i][j] = net[l].recurrentTotalGradient[i][j];
                        if (lambda != 0.0){
                            net[l].recurrentWeight[i][j] *= (1.0 - eta * lambda);
                        }
                        
                    }
                }
            }
        }
    }

    @Override
    public double predict(double[] x) {
        setInput(x);
        propagate();
        return outputLayer.output[steps - 1][0];
    }
    
    /**
    * Reset the state of the RNN
    */
    public void resetState(){
        for (int l = 0; l < net.length; l++){
            for (int t = 0; t < steps; t++){
                Arrays.fill(net[l].output[t], 0);
            }
        }
    }
    
    /**
     * Reset gradients before computing total gradient 
     * across time
     */
    private void resetTotalGradient(){
        for (int l = 1; l < net.length; l++) {
            if (net[l].recurrent){
                Arrays.fill(net[l].backwardsError, 0);
            }
            for (int i = 0; i < net[l].units; i++) {
                Arrays.fill(net[l].nonRecurrentTotalGradient[i], 0);
                if (net[l].recurrent){
                    Arrays.fill(net[l].recurrentTotalGradient[i], 0);
                }
            }
        }
    }
    
    /**
     * Do truncated back-propagation through time and return the error
     * @return the error
     */
    private double bptt(){
        double err = 0;
        // Propagate the inputs forward from least recent input to most recent
        for (int t = 0; t < steps; t++){
            setInput(xBuffer[t]);
            propagate();
            err += computeOutputError(t);
        }
        
        // Reset total gradients back to zero before computing gradient
        resetTotalGradient();
        
        // Propagate error back through time
        for (int t = steps - 1; t >= 0; t--){
            outputLayer.error[0] = gradientBuffer[t];
            backpropagate(t);
            computeTotalGradient(t);
        }
        outputLayer.error[0] = gradientBuffer[steps - 1];
        adjustWeights();
        
        if (!keepState){
            resetState();
        }
        return err;
    }


    /**
     * Trains the neural network with the given dataset for one epoch by
     * stochastic gradient descent.
     *
     * @param x training instances.
     * @param y training labels in [0, k), where k is the number of classes.
     * @return the error 
     */
    public double learn(double[][] x, double[] y) {
        if (x.length != y.length){
            throw new IllegalArgumentException("Illegal arguments: x is not same length as y");
        }
        int n = x.length;
        double err = 0;
        for (int i = 0; i < n; i++) {
            xBuffer[i%steps] = x[i];
            yBuffer[i%steps] = y[i];
            if ((i + 1) % steps == 0){
                err += bptt();
            }
            
        }
        return err;
    }
}
