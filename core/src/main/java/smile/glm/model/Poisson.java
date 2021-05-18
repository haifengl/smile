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

package smile.glm.model;

import java.util.Arrays;
import java.util.stream.IntStream;
import smile.math.MathEx;

/**
 * The response variable is of Poisson distribution.
 *
 * @author Haifeng Li
 */
public interface Poisson {
    /**
     * log link function.
     * @return log link function.
     */
    static Model log() {
        return new Model() {

            @Override
            public String toString() {
                return "Poisson(log)";
            }

            @Override
            public double link(double mu) {
                return Math.log(mu);
            }

            @Override
            public double invlink(double eta) {
                return Math.exp(eta);
            }

            @Override
            public double dlink(double mu) {
                return 1.0 / mu;
            }

            @Override
            public double variance(double mu) {
                return mu;
            }

            @Override
            public double mustart(double y) {
                if (y < 0) {
                    throw new IllegalArgumentException("Invalid argument (expected non-negative): " + y);
                }

                return y == 0 ? 0.5 : y;
            }

            @Override
            public double deviance(double[] y, double[] mu, double[] residuals) {
                return IntStream.range(0, y.length).mapToDouble(i -> {
                    double d = 2.0 * y[i] * Math.log(y[i] / mu[i]);
                    residuals[i] = Math.sqrt(d) * Math.signum(y[i] - mu[i]);
                    return d;
                }).sum();
            }

            @Override
            public double nullDeviance(double[] y, double mu) {
                return Arrays.stream(y).map(yi -> 2.0 * yi * Math.log(yi / mu)).sum();
            }

            @Override
            public double logLikelihood(double[] y, double[] mu) {
                return IntStream.range(0, y.length).mapToDouble(i -> -mu[i] + y[i] * Math.log(mu[i]) - MathEx.lfactorial((int) y[i])).sum();
            }
        };
    }
}
