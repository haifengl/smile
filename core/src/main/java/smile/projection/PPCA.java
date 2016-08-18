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
package smile.projection;

import smile.math.Math;
import smile.math.matrix.EigenValueDecomposition;
import smile.math.matrix.LUDecomposition;

/**
 * Probabilistic principal component analysis. PPCA is a simplified factor analysis
 * that employs a latent variable model with linear relationship:
 * <pre>
 *     y &sim; W * x + &mu; + &epsilon;
 * </pre>
 * where latent variables x &sim; N(0, I), error (or noise) &epsilon; &sim; N(0, &Psi;),
 * and &mu; is the location term (mean). In PPCA, an isotropic noise model is used,
 * i.e., noise variances constrained to be equal (&Psi;<sub>i</sub> = &sigma;<sup>2</sup>).
 * A close form of estimation of above parameters can be obtained
 * by maximum likelihood method.
 *
 * <h2>References</h2>
 * <ol>
 * <li>Michael E. Tipping and Christopher M. Bishop. Probabilistic Principal Component Analysis. Journal of the Royal Statistical Society. Series B (Statistical Methodology) 61(3):611-622, 1999. </li>
 * </ol>
 *
 * @see PCA
 *
 * @author Haifeng Li
 */
public class PPCA implements Projection<double[]> {

    /**
     * The sample mean.
     */
    private double[] mu;
    /**
     * The projected sample mean.
     */
    private double[] pmu;
    /**
     * The variance of noise part.
     */
    private double noise;
    /**
     * Loading matrix.
     */
    private double[][] loading;
    /**
     * Projection matrix.
     */
    private double[][] projection;

    /**
     * Returns the variable loading matrix, ordered from largest to smallest
     * by corresponding eigenvalues.
     */
    public double[][] getLoadings() {
        return loading;
    }

    /**
     * Returns the center of data.
     */
    public double[] getCenter() {
        return mu;
    }

    /**
     * Returns the variance of noise.
     */
    public double getNoiseVariance() {
        return noise;
    }

    /**
     * Returns the projection matrix. Note that this is not the matrix W in the
     * latent model.
     */
    public double[][] getProjection() {
        return projection;
    }

    @Override
    public double[] project(double[] x) {
        if (x.length != mu.length) {
            throw new IllegalArgumentException(String.format("Invalid input vector size: %d, expected: %d", x.length, mu.length));
        }

        double[] y = new double[projection.length];
        Math.ax(projection, x, y);
        Math.minus(y, pmu);
        return y;
    }

    @Override
    public double[][] project(double[][] x) {
        if (x[0].length != mu.length) {
            throw new IllegalArgumentException(String.format("Invalid input vector size: %d, expected: %d", x[0].length, mu.length));
        }

        double[][] y = new double[x.length][projection.length];
        for (int i = 0; i < x.length; i++) {
            Math.ax(projection, x[i], y[i]);
            Math.minus(y[i], pmu);
        }
        return y;
    }

    /**
     * Constructor. Learn probabilistic principal component analysis.
     * @param data training data of which each row is a sample.
     * @param k the number of principal component to learn.
     */
    public PPCA(double[][] data, int k) {
        int m = data.length;
        int n = data[0].length;

        mu = Math.colMean(data);
        double[][] cov = new double[n][n];
        for (int l = 0; l < m; l++) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j <= i; j++) {
                    cov[i][j] += (data[l][i] - mu[i]) * (data[l][j] - mu[j]);
                }
            }
        }

        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                cov[i][j] /= m;
                cov[j][i] = cov[i][j];
            }
        }


        EigenValueDecomposition eigen = EigenValueDecomposition.decompose(cov, true);
        double[] evalues = eigen.getEigenValues();
        double[][] evectors = eigen.getEigenVectors();

        noise = 0.0;
        for (int i = k; i < n; i++) {
            noise += evalues[i];
        }
        noise /= (n - k);

        loading = new double[n][k];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < k; j++) {
                loading[i][j] = evectors[i][j] * Math.sqrt(evalues[j] - noise);
            }
        }

        double[][] M = Math.atamm(loading);
        for (int i = 0; i < k; i++) {
            M[i][i] += noise;
        }

        LUDecomposition lu = new LUDecomposition(M);
        double[][] Mi = lu.inverse();
        projection = Math.abtmm(Mi, loading);

        pmu = new double[projection.length];
        Math.ax(projection, mu, pmu);
    }
}
