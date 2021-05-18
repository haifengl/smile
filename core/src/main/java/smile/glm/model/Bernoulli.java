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

/**
 * The response variable is of Bernoulli distribution.
 *
 * @author Haifeng Li
 */
public interface Bernoulli {
    /**
     * logit link function.
     * @return logit link function.
     */
    static Model logit() {
        return new Model() {
            @Override
            public String toString() {
                return "Bernoulli(logit)";
            }

            @Override
            public double link(double mu) {
                return Math.log(mu / (1.0 - mu));
            }

            @Override
            public double invlink(double eta) {
                return 1.0 / (1.0 + Math.exp(-eta));
            }

            @Override
            public double dlink(double mu) {
                return 1.0 / (mu * (1.0 - mu));
            }

            @Override
            public double variance(double mu) {
                return mu * (1.0 - mu);
            }

            @Override
            public double mustart(double y) {
                if (y == 0) return 0.1;
                if (y == 1.0) return 0.9;
                throw new IllegalArgumentException("Invalid argument (expected 0 or 1): " + y);
            }

            @Override
            public double deviance(double[] y, double[] mu, double[] residuals) {
                return IntStream.range(0, y.length).mapToDouble(i -> {
                    double d = -2.0 * (y[i] == 0.0 ? Math.log(1 - mu[i]) : Math.log(mu[i]));
                    residuals[i] = Math.sqrt(d) * Math.signum(y[i] - mu[i]);
                    return d;
                }).sum();
            }

            @Override
            public double nullDeviance(double[] y, double mu) {
                double logmu = -Math.log(mu);
                double logmu1 = -Math.log(1.0 - mu);
                return 2.0 * Arrays.stream(y).map(yi -> yi == 0.0 ? logmu1 : logmu).sum();
            }

            @Override
            public double loglikelihood(double[] y, double[] mu) {
                return IntStream.range(0, y.length).mapToDouble(i -> y[i] == 0.0 ? Math.log(1 - mu[i]) : Math.log(mu[i])).sum();
            }
        };
    }
}
