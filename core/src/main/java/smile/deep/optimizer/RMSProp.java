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

package smile.deep.optimizer;

import java.util.Arrays;
import smile.base.mlp.Layer;
import smile.math.TimeFunction;
import smile.math.matrix.Matrix;

/**
 * RMSProp optimizer with adaptive learning rate. RMSProp uses a moving
 * average of squared gradients to normalize the gradient. This
 * normalization balances the step size (momentum), decreasing the step
 * for large gradients to avoid exploding, and increasing the step for
 * small gradients to avoid vanishing.
 *
 * @author Haifeng Li
 */
public class RMSProp implements Optimizer {
    /**
     * The learning rate.
     */
    private final TimeFunction learningRate;
    /**
     * The discounting factor for the history/coming gradient.
     */
    private final double rho;
    /**
     * A small constant for numerical stability.
     */
    private final double epsilon;

    /**
     * Constructor.
     */
    public RMSProp() {
        this(TimeFunction.constant(0.001));
    }

    /**
     * Constructor.
     * @param learningRate the learning rate.
     */
    public RMSProp(TimeFunction learningRate) {
        this(learningRate, 0.9, 1E-6);
    }

    /**
     * Constructor.
     * @param learningRate the learning rate.
     * @param rho the discounting factor for the history/coming gradient.
     * @param epsilon a small constant for numerical stability.
     */
    public RMSProp(TimeFunction learningRate, double rho, double epsilon) {
        this.learningRate = learningRate;
        this.rho = rho;
        this.epsilon = epsilon;
    }

    @Override
    public String toString() {
        return String.format("RMSProp(%s, %f, %f)", learningRate, rho, epsilon);
    }

    @Override
    public void update(Layer layer, int m, int t) {
        /*
        Matrix weightGradient = layer.weightGradient.get();
        double[] biasGradient = layer.biasGradient.get();

        // As gradient will be averaged and smoothed in RMSProp,
        // we need to use the original learning rate.
        double eta = learningRate.apply(t);
        int p = layer.p;
        int n = layer.n;

        weightGradient.div(m);
        for (int i = 0; i < n; i++) {
            biasGradient[i] /= m;
        }

        Matrix weightGradientMoment2 = layer.weightGradientMoment2.get();
        double[] biasGradientMoment2 = layer.biasGradientMoment2.get();

        double rho1 = 1.0 - rho;
        weightGradientMoment2.add2(rho, rho1, weightGradient);
        for (int i = 0; i < n; i++) {
            biasGradientMoment2[i] = rho * biasGradientMoment2[i] + rho1 * biasGradient[i] * biasGradient[i];
        }

        Matrix weight = layer.weight;
        double[] bias = layer.bias;
        for (int j = 0; j < p; j++) {
            for (int i = 0; i < n; i++) {
                weight.add(i, j, eta * weightGradient.get(i, j) / Math.sqrt(epsilon + weightGradientMoment2.get(i, j)));
            }
        }
        for (int i = 0; i < n; i++) {
            bias[i] += eta * biasGradient[i] / Math.sqrt(epsilon + biasGradientMoment2[i]);
        }

        weightGradient.fill(0.0);
        Arrays.fill(biasGradient, 0.0);

         */
    }
}
