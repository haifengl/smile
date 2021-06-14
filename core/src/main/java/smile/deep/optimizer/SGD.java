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
import smile.math.MathEx;
import smile.math.TimeFunction;
import smile.math.matrix.Matrix;

/**
 * Stochastic gradient descent (with momentum) optimizer.
 *
 * @author Haifeng Li
 */
public class SGD implements Optimizer {
    /**
     * The learning rate.
     */
    private final TimeFunction learningRate;
    /**
     * The momentum factor.
     */
    private final TimeFunction momentum;

    /**
     * Constructor.
     */
    public SGD() {
        this(TimeFunction.constant(0.01));
    }

    /**
     * Constructor.
     * @param learningRate the learning rate.
     */
    public SGD(TimeFunction learningRate) {
        this(learningRate, null);
    }

    /**
     * Constructor.
     * @param learningRate the learning rate.
     * @param momentum the momentum.
     */
    public SGD(TimeFunction learningRate, TimeFunction momentum) {
        this.learningRate = learningRate;
        this.momentum = momentum;
    }

    @Override
    public String toString() {
        return momentum == null ?
                String.format("SGD(%s)", learningRate) :
                String.format("SGD(%s, %s)", learningRate, momentum);
    }

    @Override
    public void update(Layer layer, int m, int t) {
        /*
        Matrix weightGradient = layer.weightGradient.get();
        double[] biasGradient = layer.biasGradient.get();

        // Instead of computing the average gradient explicitly,
        // we scale down the learning rate by the number of samples.
        double eta = learningRate.apply(t) / m;
        int n = layer.n;

        if (momentum == null) {
            layer.weight.add(eta, weightGradient);
            for (int i = 0; i < n; i++) {
                layer.bias[i] += eta * biasGradient[i];
            }
        } else {
            double alpha = momentum.apply(t);
            Matrix weightUpdate = layer.weightUpdate.get();
            double[] biasUpdate = layer.biasUpdate.get();

            weightUpdate.add(alpha, eta, weightGradient);
            for (int i = 0; i < n; i++) {
                biasUpdate[i] = alpha * biasUpdate[i] + eta * biasGradient[i];
            }

            layer.weight.add(weightUpdate);
            MathEx.add(layer.bias, biasUpdate);
        }

        weightGradient.fill(0.0);
        Arrays.fill(biasGradient, 0.0);

         */
    }
}
