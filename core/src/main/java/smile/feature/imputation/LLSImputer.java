/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.feature.imputation;

import smile.math.matrix.Matrix;
import smile.sort.QuickSort;

/**
 * Local least squares missing value imputation. The local least squares
 * imputation method represents a target instance that has missing values as
 * a linear combination of similar instances, which are selected by k-nearest
 * neighbors method. 
 * 
 * @author Haifeng Li
 */
public interface LLSImputer {
    /**
     * Impute missing values in the dataset.
     * @param data a data set with missing values (represented as Double.NaN).
     * @param k the number of similar rows used for imputation.
     * @return the imputed data.
     * @throws IllegalArgumentException when the whole row or column is missing.
     */
    static double[][] impute(double[][] data, int k) {
        int d = data[0].length;

        if (k < 1) {
            throw new IllegalArgumentException("Invalid number of rows for imputation: " + k);
        }

        if (d < 1.5*k) {
            throw new IllegalArgumentException("The dimensionality of data is too small compared to k = " + k);
        }

        double[][] full = SimpleImputer.impute(data);
        double[] dist = new double[data.length];

        for (int i = 0; i < data.length; i++) {
            double[] x = data[i];
            int missing = 0;
            for (double v : x) {
                if (Double.isNaN(v)) {
                    missing++;
                }
            }

            if (missing == d) {
                throw new IllegalArgumentException("The whole row " + i + " is missing");
            }

            if (missing == 0) {
                continue;
            }

            for (int j = 0; j < data.length; j++) {
                double[] y = data[j];
                int n = 0;
                dist[j] = 0;
                for (int m = 0; m < x.length; m++) {
                    if (!Double.isNaN(x[m]) && !Double.isNaN(y[m])) {
                        n++;
                        dist[j] += (x[m] - y[m]) * (x[m] - y[m]);
                    }
                }

                if (n != x.length - missing) {
                    dist[j] = x.length * dist[j] / n;
                } else {
                    dist[j] = Double.MAX_VALUE;
                }
            }

            double[][] dat = new double[data.length][];
            System.arraycopy(data, 0, dat, 0, data.length);

            QuickSort.sort(dist, dat);

            Matrix A = new Matrix(d - missing, k);
            double[] b = new double[d - missing];

            for (int j = 0, m = 0; j < d; j++) {
                if (!Double.isNaN(x[j])) {
                    for (int l = 0; l < k; l++) {
                        A.set(m, l, dat[l][j]);
                    }
                    b[m++] = dat[i][j];
                }
            }

            boolean sufficient = true;
            for (int m = 0; m < A.nrow(); m++) {
                for (int n = 0; n < k; n++) {
                    if (Double.isNaN(A.get(m, n))) {
                        sufficient = false;
                        break;
                    }
                }

                if (!sufficient) {
                    break;
                }
            }

            // this row has no sufficient nearest neighbors with no missing values.
            if (!sufficient) {
                continue;
            }

            Matrix.LU lu = A.lu(true);
            lu.solve(b);

            for (int j = 0; j < d; j++) {
                if (Double.isNaN(data[i][j])) {
                    full[i][j] = 0;
                    for (int l = 0; l < k; l++) {
                        full[i][j] += b[l] * dat[l][j];
                    }
                }
            }
        }

        // In case we miss some missing values because no sufficient
        // nearest neighbors exist.
        return full;
    }
}
