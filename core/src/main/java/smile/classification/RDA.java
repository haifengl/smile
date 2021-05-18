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

package smile.classification;

import java.util.Properties;
import smile.math.matrix.Matrix;
import smile.util.IntSet;
import smile.util.Strings;

/**
 * Regularized discriminant analysis. RDA is a compromise between LDA and QDA,
 * which allows one to shrink the separate covariances of QDA toward a common
 * variance as in LDA. This method is very similar in flavor to ridge regression.
 * The regularized covariance matrices of each class is
 * &Sigma;<sub>k</sub>(&alpha;) = &alpha; &Sigma;<sub>k</sub> + (1 - &alpha;) &Sigma;.
 * The quadratic discriminant function is defined using the shrunken covariance
 * matrices &Sigma;<sub>k</sub>(&alpha;). The parameter &alpha; in [0, 1]
 * controls the complexity of the model. When &alpha; is one, RDA becomes QDA.
 * While &alpha; is zero, RDA is equivalent to LDA. Therefore, the
 * regularization factor &alpha; allows a continuum of models between LDA and QDA.
 * 
 * @see LDA
 * @see QDA
 * 
 * @author Haifeng Li
 */
public class RDA extends QDA {
    private static final long serialVersionUID = 2L;

    /**
     * Constructor.
     * @param priori a priori probabilities of each class.
     * @param mu the mean vectors of each class.
     * @param eigen the eigen values of each variance matrix.
     * @param scaling the eigen vectors of each covariance matrix.
     */
    public RDA(double[] priori, double[][] mu, double[][] eigen, Matrix[] scaling) {
        super(priori, mu, eigen, scaling, IntSet.of(priori.length));
    }

    /**
     * Constructor.
     * @param priori a priori probabilities of each class.
     * @param mu the mean vectors of each class.
     * @param eigen the eigen values of each variance matrix.
     * @param scaling the eigen vectors of each covariance matrix.
     * @param labels the class label encoder.
     */
    public RDA(double[] priori, double[][] mu, double[][] eigen, Matrix[] scaling, IntSet labels) {
        super(priori, mu, eigen, scaling, labels);
    }

    /**
     * Fits regularized discriminant analysis.
     * @param x training samples.
     * @param y training labels.
     * @param params the hyper-parameters.
     * @return the model.
     */
    public static RDA fit(double[][] x, int[] y, Properties params) {
        double alpha = Double.parseDouble(params.getProperty("smile.rda.alpha", "0.9"));
        double[] priori = Strings.parseDoubleArray(params.getProperty("smile.rda.priori"));
        double tol = Double.parseDouble(params.getProperty("smile.rda.tolerance", "1E-4"));
        return fit(x, y, alpha, priori, tol);
    }

    /**
     * Fits regularized discriminant analysis.
     * @param x training samples.
     * @param y training labels in [0, k), where k is the number of classes.
     * @param alpha regularization factor in [0, 1] allows a continuum of models
     *              between LDA and QDA.
     * @return the model.
     */
    public static RDA fit(double[][] x, int[] y, double alpha) {
        return fit(x, y, alpha, null, 1E-4);
    }

    /**
     * Fits regularized discriminant analysis.
     * @param x training samples.
     * @param y training labels in [0, k), where k is the number of classes.
     * @param alpha regularization factor in [0, 1] allows a continuum of models
     *              between LDA and QDA.
     * @param priori the priori probability of each class. If null, it will be
     *               estimated from the training data.
     * @param tol a tolerance to decide if a covariance matrix is singular; it
     *            will reject variables whose variance is less than tol<sup>2</sup>.
     * @return the model.
     */
    public static RDA fit(double[][] x, int[] y, double alpha, double[] priori, double tol) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Invalid regularization factor: " + alpha);
        }

        DiscriminantAnalysis da = DiscriminantAnalysis.fit(x, y, priori, tol);

        int k = da.k;
        int p = da.mean.length;

        Matrix St = DiscriminantAnalysis.St(x, da.mean, k, tol);
        Matrix[] cov = DiscriminantAnalysis.cov(x, y, da.mu, da.ni);

        double[][] eigen = new double[k][];
        Matrix[] scaling = new Matrix[k];

        tol = tol * tol;
        for (int i = 0; i < k; i++) {
            Matrix v = cov[i];
            v.add(alpha, 1.0 - alpha, St);

            // quick test of singularity
            for (int j = 0; j < p; j++) {
                if (v.get(j, j) < tol) {
                    throw new IllegalArgumentException(String.format("Class %d covariance matrix (column %d) is close to singular.", i, j));
                }
            }

            Matrix.EVD evd = v.eigen(false, true, true).sort();

            for (double s : evd.wr) {
                if (s < tol) {
                    throw new IllegalArgumentException(String.format("Class %d covariance matrix is close to singular.", i));
                }
            }

            eigen[i] = evd.wr;
            scaling[i] = evd.Vr;
        }

        return new RDA(da.priori, da.mu, eigen, scaling, da.labels);
    }
}
