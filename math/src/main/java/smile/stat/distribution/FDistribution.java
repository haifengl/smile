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

import smile.math.special.Beta;
import smile.math.special.Gamma;
import smile.math.Math;

/**
 * F-distribution arises in the testing of whether two observed samples have
 * the same variance. A random variate of the F-distribution arises as the
 * ratio of two chi-squared variates:
 * <pre>
 * U<sub>1</sub> / d<sub>1</sub>
 * -------
 * U<sub>2</sub> / d<sub>2</sub>
 * </pre>
 * where U1 and U2 have chi-square distributions with d1 and d2 degrees of
 * freedom respectively, and U1 and U2 are independent.
 *
 * @author Haifeng Li
 */
public class FDistribution extends AbstractDistribution {
    
    private int nu1;
    private int nu2;
    private double fac;

    /**
     * Constructor.
     * @param nu1 the degree of freedom of chi-square distribution in numerator.
     * @param nu2 the degree of freedom of chi-square distribution in denominator.
     */
    public FDistribution(int nu1, int nu2) {
        if (nu1 < 1) {
            throw new IllegalArgumentException("Invalid nu1 = " + nu1);
        }

        if (nu2 < 1) {
            throw new IllegalArgumentException("Invalid nu2 = " + nu2);
        }

        this.nu1 = nu1;
        this.nu2 = nu2;
        fac = 0.5 * (nu1 * Math.log(nu1) + nu2 * Math.log(nu2)) + Gamma.lgamma(0.5 * (nu1 + nu2))
                - Gamma.lgamma(0.5 * nu1) - Gamma.lgamma(0.5 * nu2);
    }

    /**
     * Returns the parameter nu1, the degrees of freedom of chi-square distribution in numerator.
     */
    public int getNu1() {
        return nu1;
    }

    /**
     * Returns the parameter nu2, the degrees of freedom chi-square distribution in denominator.
     */
    public int getNu2() {
        return nu2;
    }

    @Override
    public int npara() {
        return 2;
    }

    @Override
    public double mean() {
        return nu2 / (nu2 - 2.0);
    }

    @Override
    public double var() {
        return 2.0 * nu2 * nu2 * (nu1 + nu2 - 2) / (nu1 * (nu2 - 2) * (nu2 - 2) * (nu2 - 4));
    }

    @Override
    public double sd() {
        return Math.sqrt(var());
    }

    /**
     * Shannon entropy. Not supported.
     */
    @Override
    public double entropy() {
        throw new UnsupportedOperationException("F-distribution does not support entropy()");
    }

    @Override
    public String toString() {
        return String.format("F-distribution(%.4f, %.4f)", nu1, nu2);
    }

    @Override
    public double rand() {
        return inverseTransformSampling();
    }

    @Override
    public double p(double x) {
        if (x <= 0.0) {
            throw new IllegalArgumentException("Invalid x: " + x);
        }

        return Math.exp((0.5 * nu1 - 1.0) * Math.log(x) - 0.5 * (nu1 + nu2) * Math.log(nu2 + nu1 * x) + fac);
    }

    @Override
    public double logp(double x) {
        if (x <= 0.0) {
            throw new IllegalArgumentException("Invalid x: " + x);
        }

        return (0.5 * nu1 - 1.0) * Math.log(x) - 0.5 * (nu1 + nu2) * Math.log(nu2 + nu1 * x) + fac;
    }

    @Override
    public double cdf(double x) {
        if (x < 0.0) {
            throw new IllegalArgumentException("Invalid x: " + x);
        }

        return Beta.regularizedIncompleteBetaFunction(0.5 * nu1, 0.5 * nu2, nu1 * x / (nu2 + nu1 * x));
    }

    @Override
    public double quantile(double p) {
        if (p < 0.0 || p > 1.0) {
            throw new IllegalArgumentException("Invalid p: " + p);
        }

        double x = Beta.inverseRegularizedIncompleteBetaFunction(0.5 * nu1, 0.5 * nu2, p);
        return nu2 * x / (nu1 * (1.0 - x));
    }
}
