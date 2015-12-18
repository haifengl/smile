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

/**
 * This is the base class of univariate discrete distributions. Basically,
 * this class adds common distribution methods that accept integer argument
 * beside float argument. A quantile function is provided based on bisection
 * searching. Likelihood and log likelihood functions are also implemented here.
 *
 * @author Haifeng Li
 */
public abstract class DiscreteDistribution extends AbstractDistribution {

    /**
     * The probability mass function.
     */
    public abstract double p(int x);

    @Override
    public double p(double x) {
        if (x - Math.floor(x) != 0)
            return 0.0;
        else
            return p((int)x);
    }

    /**
     * The probability mass function in log scale.
     */
    public abstract double logp(int x);
    
    @Override
    public double logp(double x) {
        if (x - Math.floor(x) != 0)
            return Double.NaN;
        else
            return logp((int)x);
    }
    
    /**
     * The likelihood given a sample set following the distribution.
     */
    public double likelihood(int[] x) {
        return Math.exp(logLikelihood(x));        
    }    
    
    /**
     * The likelihood given a sample set following the distribution.
     */
    public double logLikelihood(int[] x) {
        double L = 0.0;
        
        for (double xi : x)
            L += logp(xi);
        
        return L;        
    }

    /**
     * Invertion of cdf by bisection numeric root finding of "cdf(x) = p"
     * for discrete distribution.* Returns integer n such that
     * P(<n) &le; p &le; P(<n+1).
     */
    protected double quantile(double p, int xmin, int xmax) {
        while (xmax - xmin > 1) {
            int xmed = (xmax + xmin) / 2;
            if (cdf(xmed) > p) {
                xmax = xmed;
            } else {
                xmin = xmed;
            }
        }

        if (cdf(xmin) >= p)
            return xmin;
        else
            return xmax;
    }
}
