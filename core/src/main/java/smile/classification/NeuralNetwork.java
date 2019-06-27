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

package smile.classification;

import java.io.Serializable;
import smile.base.neuralnetwork.AbstractNeuralNetwork;
import smile.base.neuralnetwork.ActivationFunction;
import smile.base.neuralnetwork.ObjectiveFunction;
import smile.base.neuralnetwork.Layer;
import smile.math.MathEx;

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
public class NeuralNetwork extends AbstractNeuralNetwork implements OnlineClassifier<double[]>, SoftClassifier<double[]>, Serializable {
    private static final long serialVersionUID = 1L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(NeuralNetwork.class);

    /**
     * The dimensionality of input data.
     */
    private int p;
    /**
     * The number of classes.
     */
    private int k;

    /**
     * Constructor. The activation function of output layer will be chosen
     * by natural pairing based on the error function and the number of
     * classes.
     *
     * @param obj the objective function.
     * @param net the layers in the neural network. The input layer should not be included.
     */
    public NeuralNetwork(ObjectiveFunction obj, Layer... net) {
        super(obj, net);

        Layer outputLayer = net[net.length-1];
        ActivationFunction activation = outputLayer.getActivation();
        switch (obj) {
            case LEAST_MEAN_SQUARES:
                if (activation == ActivationFunction.SOFTMAX) {
                    throw new IllegalArgumentException("Sofmax activation function is invalid for least mean squares error.");
                }

            case CROSS_ENTROPY:
                this.alpha = 0.0;
                this.lambda = 0.0;
                switch (activation) {
                    case RECTIFIER:
                    case LINEAR:
                        throw new IllegalArgumentException("Rectifier/Linear activation function is invalid with cross entropy error.");

                    case SOFTMAX:
                        if (outputLayer.getOutputUnits() == 1) {
                            throw new IllegalArgumentException("Softmax activation function is for multi-class.");
                        }
                        break;

                    case LOGISTIC_SIGMOID:
                        if (outputLayer.getOutputUnits() != 1) {
                            throw new IllegalArgumentException("For cross entropy error, logistic sigmoid output is for binary classification.");
                        }
                        break;
                }
        }

        p = net[0].getInputUnits();
        k = outputLayer.getOutputUnits();
        if (k == 1) k = 2;
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
        propagate(x);

        Layer outputLayer = net[net.length - 1];
        System.arraycopy(outputLayer.getOutput(), 0, y, 0, outputLayer.getOutputUnits());

        if (outputLayer.getOutputUnits() == 1) {
            return outputLayer.getOutput()[0] > 0.5 ? 0 : 1;
        } else {
            return MathEx.whichMax(outputLayer.getOutput());
        }
    }

    @Override
    public int predict(double[] x) {
        propagate(x);

        Layer outputLayer = net[net.length - 1];
        if (outputLayer.getOutputUnits() == 1) {
            return outputLayer.getOutput()[0] > 0.5 ? 0 : 1;
        } else {
            return MathEx.whichMax(outputLayer.getOutput());
        }
    }

    @Override
    public void update(double[] x, int y) {
        propagate(x);
        setTarget(y);
        backpropagate(target);

        update();
    }

    @Override
    public void update(double[][] x, int[] y) {
        for (int i = 0; i < x.length; i++) {
            propagate(x[i]);
            setTarget(y[i]);
            backpropagate(target);
        }

        update();
    }

    /** Sets the target vector. */
    private void setTarget(int y) {
        Layer outputLayer = net[net.length - 1];
        switch (obj) {
            case CROSS_ENTROPY:
                switch (outputLayer.getActivation()) {
                    case LOGISTIC_SIGMOID:
                        target[0] = y == 0 ? 1.0 : 0.0;
                        break;

                    default:
                        for (int i = 0; i < target.length; i++) {
                            target[i] = 0.0;
                        }
                        target[y] = 1.0;
                }
                break;

            case LEAST_MEAN_SQUARES:
                for (int i = 0; i < target.length; i++) {
                    target[i] = 0.1;
                }
                target[y] = 0.9;
                break;
        }
    }
}
