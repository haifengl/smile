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

package smile.imputation;

import smile.math.matrix.Matrix;
import smile.math.matrix.LU;
import smile.math.matrix.DenseMatrix;
import smile.sort.QuickSort;

/**
 * Local least squares missing value imputation. The local least squares
 * imputation method represents a target instance that has missing values as
 * a linear combination of similar instances, which are selected by k-nearest
 * neighbors method. 
 * 
 * @author Haifeng Li
 */
public class LLSImputation implements MissingValueImputation {
    /**
     * The number of nearest neighbors used for imputation.
     */
    private int k;

    /**
     * Constructor.
     * @param k the number of similar rows used for imputation.
     */
    public LLSImputation(int k) {
        if (k < 1) {
            throw new IllegalArgumentException("Invalid number of rows for imputation: " + k);
        }

        this.k = k;
    }

    @Override
    public void impute(double[][] data) throws MissingValueImputationException {
        int d = data[0].length;

        if (d < 1.5*k)
            throw new IllegalArgumentException("The dimensionality of data is too small compared to k = " + k);

        int[] count = new int[data[0].length];
        for (int i = 0; i < data.length; i++) {
            int n = 0;
            for (int j = 0; j < data[i].length; j++) {
                if (Double.isNaN(data[i][j])) {
                    n++;
                    count[j]++;
                }
            }

            if (n == data[i].length) {
                throw new MissingValueImputationException("The whole row " + i + " is missing");
            }
        }

        for (int i = 0; i < data[0].length; i++) {
            if (count[i] == data.length) {
                throw new MissingValueImputationException("The whole column " + i + " is missing");
            }
        }

        double[] dist = new double[data.length];

        for (int i = 0; i < data.length; i++) {
            double[] x = data[i];
            int missing = 0;
            for (int j = 0; j < x.length; j++) {
                if (Double.isNaN(x[j])) {
                    missing++;
                }
            }

            if (missing == 0)
                continue;

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
            for (int j = 0; j < data.length; j++) {
                dat[j] = data[j];
            }

            QuickSort.sort(dist, dat);

            DenseMatrix A = Matrix.zeros(d - missing, k);
            double[] b = new double[d - missing];

            for (int j = 0, m = 0; j < d; j++) {
                if (!Double.isNaN(data[i][j])) {
                    for (int l = 0; l < k; l++)
                        A.set(m, l, dat[l][j]);
                    b[m++] = dat[i][j];
                }
            }

            boolean sufficient = true;
            for (int m = 0; m < A.nrows(); m++) {
                for (int n = 0; n < k; n++) {
                    if (Double.isNaN(A.get(m, n))) {
                        sufficient = false;
                        break;
                    }
                }

                if (!sufficient)
                    break;
            }

            // this row has no sufficent nearest neighbors with no missing values.
            if (!sufficient)
                continue;

            LU lu = A.lu();
            lu.solve(b);

            for (int j = 0; j < d; j++) {
                if (Double.isNaN(data[i][j])) {
                    data[i][j] = 0;
                    for (int l = 0; l < k; l++) {
                        data[i][j] += b[l] * dat[l][j];
                    }
                }
            }
        }

        /*
         * In case we miss some missing values because no sufficient
         * nearest neighbors exist.
         */
        KNNImputation knn = new KNNImputation(k);
        knn.impute(data);
    }
}
