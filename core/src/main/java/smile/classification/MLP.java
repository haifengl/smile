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
import java.util.Arrays;
import smile.base.mlp.*;
import smile.math.MathEx;
import smile.util.IntSet;

/**
 * Fully connected multilayer perceptron neural network for classification.
 * An MLP consists of at least three layers of nodes: an input layer,
 * a hidden layer and an output layer. The nodes are interconnected
 * through weighted acyclic arcs from each preceding layer to the
 * following, without lateral or feedback connections. Each node
 * calculates a transformed weighted linear combination of its inputs
 * (output activations from the preceding layer), with one of the weights
 * acting as a trainable bias connected to a constant input. The
 * transformation, called activation function, is a bounded non-decreasing
 * (non-linear) function.
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
public class MLP extends MultilayerPerceptron implements OnlineClassifier<double[]>, SoftClassifier<double[]>, Serializable {
    private static final long serialVersionUID = 2L;

    /**
     * The number of classes.
     */
    private int k;
    /**
     * The class label encoder.
     */
    private IntSet labels;

    /**
     * Constructor.
     *
     * @param p the number of variables in input layer.
     * @param builders the builders of layers from bottom to top.
     */
    public MLP(int p, LayerBuilder... builders) {
        super(net(p, builders));

        k = output.getOutputSize();
        if (k == 1) k = 2;
        labels = IntSet.of(k);
    }

    /**
     * Constructor.
     *
     * @param p the number of variables in input layer.
     * @param builders the builders of layers from bottom to top.
     */
    public MLP(IntSet labels, int p, LayerBuilder... builders) {
        super(net(p, builders));

        k = output.getOutputSize();
        if (k == 1) k = 2;
        this.labels = labels;
    }

    /** Builds the layers. */
    private static Layer[] net(int p, LayerBuilder... builders) {
        int l = builders.length;
        Layer[] net = new Layer[l];

        for (int i = 0; i < l; i++) {
            net[i] = builders[i].build(p);
            p = builders[i].neurons();
        }

        return net;
    }

    @Override
    public int predict(double[] x, double[] posteriori) {
        propagate(x);

        int n = output.getOutputSize();
        if (n == 1 && k == 2) {
            posteriori[1] = output.output()[0];
            posteriori[0] = 1.0 - posteriori[1];
        } else {
            System.arraycopy(output.output(), 0, posteriori, 0, n);
        }

        return labels.valueOf(MathEx.whichMax(posteriori));
    }

    @Override
    public int predict(double[] x) {
        propagate(x);
        int n = output.getOutputSize();

        if (n == 1 && k == 2) {
            return labels.valueOf(output.output()[0] > 0.5 ? 1 : 0);
        } else {
            return labels.valueOf(MathEx.whichMax(output.output()));
        }
    }

    @Override
    public void update(double[] x, int y) {
        propagate(x);
        setTarget(labels.indexOf(y));
        backpropagate(x);
        update();
    }

    /** Mini-batch. */
    @Override
    public void update(double[][] x, int[] y) {
        // Set momentum factor to 1.0 so that mini-batch is in play.
        double a = alpha;
        alpha = 1.0;

        for (int i = 0; i < x.length; i++) {
            propagate(x[i]);
            setTarget(labels.indexOf(y[i]));
            backpropagate(x[i]);
        }

        update();
        alpha = a;
    }

    /** Sets the target vector. */
    private void setTarget(int y) {
        int n = output.getOutputSize();

        double t = output.cost() == Cost.LIKELIHOOD ? 1.0 : 0.9;
        double f = 1.0 - t;

        if (n == 1) {
            target[0] = y == 1 ? t : f;
        } else {
            Arrays.fill(target, f);
            target[y] = t;
        }
    }
}
