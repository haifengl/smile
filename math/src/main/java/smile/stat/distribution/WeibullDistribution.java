/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package smile.stat.distribution;

import smile.math.special.Gamma;
import smile.math.Math;

/**
 * The Weibull distribution is one of the most widely used lifetime distributions
 * in reliability engineering. It is a versatile distribution that can take on
 * the characteristics of other types of distributions, based on the value of
 * the shape parameter. The distribution has two parameters: k &gt; 0 is the shape
 * parameter and &lambda; &gt; 0 is the scale parameter of the distribution.
 * The probability density function is
 * f(x;&lambda;,k) = k/&lambda; (x/&lambda;)<sup>k-1</sup>e<sup>-(x/&lambda;)<sup>k</sup></sup>
 * for x &ge; 0.
 * <p>
 * The Weibull distribution is often used in the field of life data analysis
 * due to its flexibility - it can mimic the behavior of other statistical
 * distributions such as the normal and the exponential. If the failure rate
 * decreases over time, then k &lt; 1. If the failure rate is constant over time,
 * then k = 1. If the failure rate increases over time, then k &gt; 1.
 * <p>
 * An understanding of the failure rate may provide insight as to what is
 * causing the failures:
 * <ul>
 * <li> A decreasing failure rate would suggest "infant mortality". That is,
 * defective items fail early and the failure rate decreases over time as
 * they fall out of the population.
 * <li> A constant failure rate suggests that items are failing from random
 * events.
 * <li> An increasing failure rate suggests "wear out" - parts are more likely
 * to fail as time goes on.
 * </ul>
 *
 * Under certain parameterizations, the Weibull distribution reduces to several
 * other familiar distributions:
 * <ul>
 * <li> When k = 1, it is the exponential distribution.
 * <li> When k = 2, it becomes equivalent to the Rayleigh distribution,
 * which models the modulus of a two-dimensional uncorrelated bivariate
 * normal vector.
 * <li> When k = 3.4, it appears similar to the normal distribution.
 * <li> As k goes to infinity, the Weibull distribution asymptotically
 * approaches the Dirac delta function.
 * </ul>
 *
 * @author Haifeng Li
 */
public class WeibullDistribution extends AbstractDistribution {
    private double shape;
    private double scale;
    private double mean;
    private double var;
    private double entropy;

    /**
     * Constructor. The default scale parameter is 1.0.
     * @param shape the shape parameter.
     */
    public WeibullDistribution(double shape) {
        this(shape, 1.0);
    }

    /**
     * Constructor.
     * @param shape the shape parameter.
     * @param scale the scale parameter.
     */
    public WeibullDistribution(double shape, double scale) {
        if (shape <= 0) {
            throw new IllegalArgumentException("Invalid shape: " + shape);
        }

        if (scale <= 0) {
            throw new IllegalArgumentException("Invalid scale: " + scale);
        }

        this.shape = shape;
        this.scale = scale;

        mean = scale * Gamma.gamma(1 + 1 / shape);
        var = scale * scale * Gamma.gamma(1 + 2 / shape) - mean * mean;
        entropy = 0.5772156649015328606 * (1 - 1 / shape) + Math.log(scale / shape) + 1;
    }

    @Override
    public int npara() {
        return 2;
    }

    @Override
    public double mean() {
        return mean;
    }

    @Override
    public double var() {
        return var;
    }

    @Override
    public double sd() {
        return Math.sqrt(var());
    }

    @Override
    public double entropy() {
        return entropy;
    }

    @Override
    public String toString() {
        return String.format("Weibull Distribution(%.4f, %.4f)", shape, scale);
    }

    @Override
    public double rand() {
        double r = Math.random();
        return scale * Math.pow(-Math.log(1 - r), 1 / shape);
    }

    @Override
    public double p(double x) {
        if (x <= 0) {
            return 0.0;
        } else {
            return (shape / scale) * Math.pow(x / scale, shape - 1) * Math.exp(-Math.pow(x / scale, shape));
        }
    }

    @Override
    public double logp(double x) {
        if (x <= 0) {
            return Double.NEGATIVE_INFINITY;
        } else {
            return Math.log(shape / scale) + (shape - 1) * Math.log(x / scale) - Math.pow(x / scale, shape);
        }
    }

    @Override
    public double cdf(double x) {
        if (x <= 0) {
            return 0.0;
        } else {
            return 1 - Math.exp(-Math.pow(x / scale, shape));
        }
    }

    @Override
    public double quantile(double p) {
        if (p < 0.0 || p > 1.0) {
            throw new IllegalArgumentException("Invalid p: " + p);
        }

        return scale * Math.pow(-Math.log(1 - p), 1 / shape);
    }
}

