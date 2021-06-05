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

import java.util.Arrays;
import java.util.Properties;
import smile.base.mlp.*;
import smile.math.Scaler;
import smile.math.MathEx;
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
     * @param builders the builders of input and hidden layers from bottom to top.
     */
    public MLP(LayerBuilder... builders) {
        this(null, builders);
    }

    /**
     * Constructor.
     *
     * @param scaler the scaling function of output values.
     * @param builders the builders of input and hidden layers from bottom to top.
     */
    public MLP(Scaler scaler, LayerBuilder... builders) {
        super(net(builders));
        this.scaler = scaler;
    }

    /** Builds the layers. */
    private static Layer[] net(LayerBuilder... builders) {
        int p = 0;
        int l = builders.length;
        Layer[] net = new Layer[l];

        for (int i = 0; i < l; i++) {
            net[i] = builders[i].build(p);
            p = builders[i].neurons();
        }

        if (!(net[l-1] instanceof OutputLayer)) {
            net = Arrays.copyOf(net, l + 1);
            net[l] = new OutputLayer(1, p, OutputFunction.LINEAR, Cost.MEAN_SQUARED_ERROR);
        }
        return net;
    }

    @Override
    public double predict(double[] x) {
        propagate(x, false);
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
        propagate(x, true);
        setTarget(y);
        backpropagate(true);
        t++;
    }

    /** Updates the model with a mini-batch. RMSProp is applied if {@code rho > 0}. */
    @Override
    public void update(double[][] x, double[] y) {
        for (int i = 0; i < x.length; i++) {
            propagate(x[i], true);
            setTarget(y[i]);
            backpropagate(false);
        }

        update(x.length);
        t++;
    }

    /**
     * Sets the network target value.
     *
     * @param y the raw responsible variable.
     */
    private void setTarget(double y) {
        target.get()[0] = scaler == null ? y : scaler.f(y);
    }

    /**
     * Fits a MLP model.
     * @param x the training dataset.
     * @param y the response variable.
     * @param params the hyper-parameters.
     * @return the model.
     */
    public static MLP fit(double[][] x, double[] y, Properties params) {
        int p = x[0].length;

        Scaler scaler = Scaler.of(params.getProperty("smile.mlp.scaler"), y);
        LayerBuilder[] layers = Layer.of(0, p, params.getProperty("smile.mlp.layers", "ReLU(100)"));
        MLP model = new MLP(scaler, layers);
        model.setParameters(params);

        int epochs = Integer.parseInt(params.getProperty("smile.mlp.epochs", "100"));
        int batch = Integer.parseInt(params.getProperty("smile.mlp.mini_batch", "32"));
        double[][] batchx = new double[batch][];
        double[] batchy = new double[batch];
        for (int epoch = 1; epoch <= epochs; epoch++) {
            logger.info("{} epoch", Strings.ordinal(epoch));
            int[] permutation = MathEx.permutate(x.length);
            for (int i = 0; i < x.length; i += batch) {
                int size = Math.min(batch, x.length - i);
                for (int j = 0; j < size; j++) {
                    int index = permutation[i + j];
                    batchx[j] = x[index];
                    batchy[j] = y[index];
                }

                if (size < batch) {
                    model.update(Arrays.copyOf(batchx, size), Arrays.copyOf(batchy, size));
                } else {
                    model.update(batchx, batchy);
                }
            }
        }

        return model;
    }
}

