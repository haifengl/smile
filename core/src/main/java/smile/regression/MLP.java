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

package smile.regression;

import java.util.Locale;
import java.util.Properties;
import smile.base.mlp.*;
import smile.math.Scaler;
import smile.math.MathEx;
import smile.math.TimeFunction;
import smile.util.Strings;

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
 public class MLP extends MultilayerPerceptron implements Regression<double[]> {
    private static final long serialVersionUID = 2L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MLP.class);

    /** The scaling function of output values. */
    private final Scaler scaler;

    /**
     * Constructor.
     *
     * @param p the number of variables in input layer.
     * @param builders the builders of hidden layers from bottom to top.
     */
    public MLP(int p, LayerBuilder... builders) {
        super(net(p, builders));
        scaler = null;
    }

    /**
     * Constructor.
     *
     * @param scaler the scaling function of output values.
     * @param p the number of variables in input layer.
     * @param builders the builders of hidden layers from bottom to top.
     */
    public MLP(Scaler scaler, int p, LayerBuilder... builders) {
        super(net(p, builders));
        this.scaler = scaler;
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
        double y = output.output()[0];
        return scaler == null ? y : scaler.inv(y);
    }

    @Override
    public boolean online() {
        return true;
    }

    /** Updates the model with a single sample. RMSProp is not applied. */
    @Override
    public void update(double[] x, double y) {
        propagate(x);
        target.get()[0] = scaler == null ? y : scaler.f(y);
        backpropagate(x, true);
        t++;
    }

    /** Updates the model with a mini-batch. RMSProp is applied if {@code rho > 0}. */
    @Override
    public void update(double[][] x, double[] y) {
        double[] target = this.target.get();
        for (int i = 0; i < x.length; i++) {
            propagate(x[i]);
            target[0] = scaler == null ? y[i] : scaler.f(y[i]);
            backpropagate(x[i], false);
        }

        update(x.length);
        t++;
    }

    /**
     * Fits a MLP model.
     * @param x the training dataset.
     * @param y the response variable.
     * @param prop the hyper-parameters.
     * @return the model.
     */
    public static MLP fit(double[][] x, double[] y, Properties prop) {
        int p = x[0].length;

        String scaling = prop.getProperty("smile.mlp.scaler", "standardizer");
        Scaler scaler;
        switch (scaling.toLowerCase(Locale.ROOT)) {
            case "scaler":
                scaler = Scaler.of(y);
                break;
            case "winsor":
                scaler = Scaler.winsor(y);
                break;
            case "standardizer":
                scaler = Scaler.standardizer(y, true);
                break;
            default:
                throw new IllegalArgumentException("Invalid smile.mlp.scaler: " + scaling);
        }

        LayerBuilder[] layers = Layer.of(0, prop.getProperty("smile.mlp.layers", "ReLU(100)"));
        MLP model = new MLP(scaler, p, layers);

        int epochs = Integer.parseInt(prop.getProperty("smile.mlp.epochs", "100"));
        int batch = Integer.parseInt(prop.getProperty("smile.mlp.mini_batch", "256"));
        double[][] batchx = new double[batch][];
        double[] batchy = new double[batch];
        for (int epoch = 1; epoch <= epochs; epoch++) {
            logger.info("{} epoch", Strings.ordinal(epoch));
            int[] permutation = MathEx.permutate(x.length);
            for (int i = 0; i < x.length; i += batch) {
                for (int j = 0; j < batch; j++) {
                    int pi = permutation[(i+j) % x.length];
                    batchx[j] = x[pi];
                    batchy[j] = y[pi];
                }
                model.update(batchx, batchy);
            }
        }

        return model;
    }
}

