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
 * Adaptive Moment optimizer. Adam computes adaptive learning rates for
 * each parameter. In addition to storing an exponentially decaying average
 * of past squared gradients RMSProp, Adam also keeps an exponentially
 * decaying average of past gradients, similar to momentum. Whereas momentum
 * can be seen as a ball running down a slope, Adam behaves like a heavy ball
 * with friction, which thus prefers flat minima in the error surface.
 *
 * @author Haifeng Li
 */
public class Adam implements Optimizer {
    /**
     * The learning rate.
     */
    private final TimeFunction learningRate;
    /**
     * The exponential decay rate for the 1st moment estimates.
     */
    private final double beta1;
    /**
     * The exponential decay rate for the 2nd moment estimates.
     */
    private final double beta2;
    /**
     * A small constant for numerical stability.
     */
    private final double epsilon;

    /**
     * Constructor.
     */
    public Adam() {
        this(TimeFunction.constant(0.001));
    }

    /**
     * Constructor.
     * @param learningRate the learning rate.
     */
    public Adam(TimeFunction learningRate) {
        this(learningRate, 0.9, 0.999);
    }

    /**
     * Constructor.
     * @param learningRate the learning rate.
     * @param beta1 the exponential decay rate for the 1st moment estimates.
     * @param beta2 the exponential decay rate for the 2nd moment estimates.
     */
    public Adam(TimeFunction learningRate, double beta1, double beta2) {
        this(learningRate, beta1, beta2, 1E-8);
    }

    /**
     * Constructor.
     * @param learningRate the learning rate.
     * @param beta1 the exponential decay rate for the 1st moment estimates.
     * @param beta2 the exponential decay rate for the 2nd moment estimates.
     * @param epsilon a small constant for numerical stability.
     */
    public Adam(TimeFunction learningRate, double beta1, double beta2, double epsilon) {
        this.learningRate = learningRate;
        this.beta1 = beta1;
        this.beta2 = beta2;
        this.epsilon = epsilon;
    }

    @Override
    public String toString() {
        return String.format("Adam(%s, %s, %s, %f)", learningRate, beta1, beta2, epsilon);
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
        int p = layer.p;

        weightGradient.div(m);
        for (int i = 0; i < n; i++) {
            biasGradient[i] /= m;
        }

        Matrix weightGradientMoment1 = layer.weightGradientMoment1.get();
        Matrix weightGradientMoment2 = layer.weightGradientMoment2.get();
        double[] biasGradientMoment1 = layer.biasGradientMoment1.get();
        double[] biasGradientMoment2 = layer.biasGradientMoment2.get();

        weightGradientMoment1.add(beta1, 1.0 - beta1, weightGradient);
        weightGradientMoment2.add2(beta2, 1.0 - beta2, weightGradient);
        for (int i = 0; i < n; i++) {
            biasGradientMoment1[i] = beta1 * biasGradientMoment2[i] + (1.0 - beta1) * biasGradient[i];
            biasGradientMoment2[i] = beta2 * biasGradientMoment2[i] + (1.0 - beta2) * biasGradient[i] * biasGradient[i];
        }

        Matrix weight = layer.weight;
        double[] bias = layer.bias;
        double beta1t = 1.0 - Math.pow(beta1, t);
        double beta2t = 1.0 - Math.pow(beta2, t);
        for (int j = 0; j < p; j++) {
            for (int i = 0; i < n; i++) {
                double s = weightGradientMoment1.get(i, j);
                double r = weightGradientMoment2.get(i, j);
                weight.add(i, j, eta * (s / beta1t) / (Math.sqrt(r / beta2t) + epsilon));
            }
        }
        for (int i = 0; i < n; i++) {
            double s = biasGradientMoment1[i];
            double r = biasGradientMoment2[i];
            bias[i] += eta * (s / beta1t) / (Math.sqrt(r / beta2t) + epsilon);
        }

        weightGradient.fill(0.0);
        Arrays.fill(biasGradient, 0.0);

         */
    }
}
