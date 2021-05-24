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

package smile.base.mlp;

import java.util.Arrays;
import smile.math.MathEx;
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
     * The momentum factor.
     */
    private final TimeFunction momentum;
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
     * @param learningRate the learning rate.
     */
    public RMSProp(TimeFunction learningRate) {
        this(learningRate, null, 0.9, 1E-7);
    }

    /**
     * Constructor.
     * @param learningRate the learning rate.
     * @param rho the discounting factor for the history/coming gradient.
     */
    public RMSProp(TimeFunction learningRate, double rho) {
        this(learningRate, null, rho, 1E-7);
    }

    /**
     * Constructor.
     * @param learningRate the learning rate.
     * @param momentum the momentum.
     * @param rho the discounting factor for the history/coming gradient.
     * @param epsilon a small constant for numerical stability.
     */
    public RMSProp(TimeFunction learningRate, TimeFunction momentum, double rho, double epsilon) {
        this.learningRate = learningRate;
        this.momentum = momentum;
        this.rho = rho;
        this.epsilon = epsilon;
    }

    @Override
    public String toString() {
        return momentum == null ?
                String.format("RMSProp(%s, %f, %f)", learningRate, rho, epsilon) :
                String.format("RMSProp(%s, %s, %f, %f)", learningRate, momentum, rho, epsilon);
    }

    @Override
    public void update(Layer layer, int m, int t) {
        Matrix weightGradient = layer.weightGradient.get();
        double[] biasGradient = layer.biasGradient.get();

        // Instead of computing the average gradient explicitly,
        // we scale down the learning rate by the number of samples.
        double eta = learningRate.apply(t) / m;
        int n = layer.n;

        if (momentum != null) {
            double alpha = momentum.apply(t);
            Matrix weightUpdate = layer.weightUpdate.get();
            double[] biasUpdate = layer.biasUpdate.get();

            weightUpdate.add(alpha, eta, weightGradient);
            for (int i = 0; i < n; i++) {
                biasUpdate[i] = alpha * biasUpdate[i] + eta * biasGradient[i];
            }

            layer.weight.add(1.0, weightUpdate);
            MathEx.add(layer.bias, biasUpdate);
        } else {
            layer.weight.add(eta, weightGradient);
            for (int i = 0; i < n; i++) {
                layer.bias[i] += eta * biasGradient[i];
            }
        }

        weightGradient.fill(0.0);
        Arrays.fill(biasGradient, 0.0);
    }
}
