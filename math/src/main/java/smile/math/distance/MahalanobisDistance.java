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

package smile.math.distance;

import smile.math.Math;

/**
 * In statistics, Mahalanobis distance is based on correlations between
 * variables by which different patterns can be identified and analyzed.
 * It is a useful way of determining similarity of an unknown sample set
 * to a known one. It differs from Euclidean distance in that it takes
 * into account the correlations of the data set and is scale-invariant,
 * i.e. not dependent on the scale of measurements.
 *
 * @author Haifeng Li
 */
public class MahalanobisDistance implements Metric<double[]> {

    private double[][] sigma;
    private double[][] sigmaInv;

    /**
     * Constructor with given covariance matrix.
     */
    public MahalanobisDistance(double[][] cov) {
        sigma = new double[cov.length][cov.length];
        for (int i = 0; i < cov.length; i++) {
            System.arraycopy(cov[i], 0, sigma[i], 0, cov.length);
        }

        sigmaInv = Math.inverse(sigma);
    }

    @Override
    public String toString() {
        return "Mahalanobis distance";
    }

    @Override
    public double d(double[] x, double[] y) {
        if (x.length != sigma.length)
            throw new IllegalArgumentException(String.format("Array x[%d] has different dimension with Sigma[%d][%d].", x.length, sigma.length, sigma.length));

        if (y.length != sigma.length)
            throw new IllegalArgumentException(String.format("Array y[%d] has different dimension with Sigma[%d][%d].", y.length, sigma.length, sigma.length));

        int n = x.length;
        double[] z = new double[n];
        for (int i = 0; i < n; i++)
            z[i] = x[i] - y[i];

        double dist = Math.xax(sigmaInv, z);
        return Math.sqrt(dist);
    }
}
