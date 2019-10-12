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

import smile.base.neuralnetwork.AbstractNeuralNetwork;
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
 public class NeuralNetwork extends AbstractNeuralNetwork implements OnlineRegression<double[]> {
    private static final long serialVersionUID = 2L;

    /**
     * Constructor.
     *
     * @param net the layers in the neural network. The input layer should not be included.
     */
    public NeuralNetwork(Layer... net) {
        super(ObjectiveFunction.LEAST_MEAN_SQUARES, net);

        Layer outputLayer = outputLayer();
        if (outputLayer.getOutputUnits() != 1) {
            throw new IllegalArgumentException("The output layer must have only one output value: " + outputLayer.getOutputUnits());
        }
    }

    @Override
    public double predict(double[] x) {
        propagate(x);
        return outputLayer().getOutput()[0];
    }

    @Override
    public void update(double[] x, double y) {
        propagate(x);
        target[0] = y;
        backpropagate(target);

        update();
    }

    @Override
    public void update(double[][] x, double[] y) {
        for (int i = 0; i < x.length; i++) {
            propagate(x[i]);
            target[0] = y[i];
            backpropagate(target);
        }

        update();
    }
}

