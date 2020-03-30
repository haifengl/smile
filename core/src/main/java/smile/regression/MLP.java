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

import smile.base.mlp.*;

/**
 * Fully connected multilayer perceptron neural network for regression.
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
 public class MLP extends MultilayerPerceptron implements OnlineRegression<double[]> {
    private static final long serialVersionUID = 2L;

    /**
     * Constructor.
     *
     * @param p the number of variables in input layer.
     * @param builders the builders of hidden layers from bottom to top.
     */
    public MLP(int p, LayerBuilder... builders) {
        super(net(p, builders));
    }

    /** Builds the layers. */
    private static Layer[] net(int p, LayerBuilder... builders) {
        int l = builders.length;
        Layer[] net = new Layer[l+1];

        for (int i = 0; i < l; i++) {
            net[i] = builders[i].build(p);
            p = builders[i].neurons();
        }

        net[l] = new OutputLayer(1, p, OutputFunction.LINEAR, Cost.MEAN_SQUARED_ERROR);
        return net;
    }

    @Override
    public double predict(double[] x) {
        propagate(x);
        return output.output()[0];
    }

    @Override
    public void update(double[] x, double y) {
        propagate(x);
        target[0] = y;
        backpropagate(x);
        update();
    }

    @Override
    public void update(double[][] x, double[] y) {
        // Set momentum factor to 1.0 so that mini-batch is in play.
        double a = alpha;
        alpha = 1.0;

        for (int i = 0; i < x.length; i++) {
            propagate(x[i]);
            target[0] = y[i];
            backpropagate(x[i]);
        }

        update();
        alpha = a;
    }
}

