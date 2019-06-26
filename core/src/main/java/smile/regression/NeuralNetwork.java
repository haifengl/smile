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

package smile.regression;

import smile.base.neuralnetwork.Layer;
import smile.base.neuralnetwork.ObjectiveFunction;
 
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
 public class NeuralNetwork extends smile.base.NeuralNetwork implements OnlineRegression<double[]> {
    private static final long serialVersionUID = 2L;

    /**
     * layers of this net
     */
    private Layer[] net;
    /**
     * output layer
     */
    private Layer outputLayer;

    /**
     * Constructor.
     *
     * @param p the dimension of input vector, i.e. the number of neuron of input layer.
     * @param net the layers in the neural network. The input layer should not be included.
     */
    public NeuralNetwork(int p, Layer... net) {
        super(ObjectiveFunction.LEAST_MEAN_SQUARES, p, 1);

        if (net.length < 2) {
            throw new IllegalArgumentException("Invalid number of layers: " + net.length);
        }

        this.net = net;
        outputLayer = net[net.length - 1];

        if (outputLayer.getOutput().length != 1) {
            throw new IllegalArgumentException("The output layer must have only one output value: " + outputLayer.getOutput().length);
        }
    }

    @Override
    public double predict(double[] x) {
        propagate(net, x);
        return outputLayer.getOutput()[0];
    }

    @Override
    public void update(double[] x, double y) {
        propagate(net, x);
        target[0] = y;
        backpropagate(net, target);

        update();
    }

    @Override
    public void update(double[][] x, double[] y) {
        for (int i = 0; i < x.length; i++) {
            propagate(net, x[i]);
            target[0] = y[i];
            backpropagate(net, target);
        }

        update();
    }

    /** Updates the weights. */
    private void update() {
        for (Layer layer : net) {
            layer.update(alpha);

            if (lambda != 1.0) {
                layer.decay(lambda);
            }
        }
    }
}

