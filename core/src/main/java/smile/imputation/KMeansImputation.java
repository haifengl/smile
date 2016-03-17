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

import smile.clustering.KMeans;

/**
 * Missing value imputation by K-Means clustering. First cluster data by K-Means
 * with missing values and then impute missing values with the average value of each attribute
 * in the clusters.
 * 
 * @author Haifeng Li
 */
public class KMeansImputation implements MissingValueImputation {

    /**
     * The number of clusters in KMeans clustering.
     */
    private int k;
    /**
     * The number of runs of K-Means algorithm.
     */
    private int runs;

    /**
     * Constructor.
     * @param k the number of clusters in K-Means clustering.
     */
    public KMeansImputation(int k) {
        this(k, 4);
    }

    /**
     * Constructor.
     * @param k the number of clusters in K-Means clustering.
     * @param runs the number of runs of K-Means algorithm.
     */
    public KMeansImputation(int k, int runs) {
        if (k < 2) {
            throw new IllegalArgumentException("Invalid number of clusters: " + k);
        }

        if (runs < 1) {
            throw new IllegalArgumentException("Invalid runs: " + runs);
        }

        this.k = k;
        this.runs = runs;
    }

    @Override
    public void impute(double[][] data) throws MissingValueImputationException {
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

        KMeans kmeans = KMeans.lloyd(data, k, Integer.MAX_VALUE, runs);

        for (int i = 0; i < k; i++) {
            if (kmeans.getClusterSize()[i] > 0) {
                double[][] d = new double[kmeans.getClusterSize()[i]][];
                for (int j = 0, m = 0; j < data.length; j++) {
                    if (kmeans.getClusterLabel()[j] == i) {
                        d[m++] = data[j];
                    }
                }

                columnAverageImpute(d);
            }
        }

        // In case of some clusters miss all values in some columns.
        columnAverageImpute(data);
    }

    /**
     * Impute the missing values with column averages.
     * @param data data with missing values.
     * @throws smile.imputation.MissingValueImputationException
     */
    static void columnAverageImpute(double[][] data) throws MissingValueImputationException {
        for (int j = 0; j < data[0].length; j++) {
            int n = 0;
            double sum = 0.0;

            for (int i = 0; i < data.length; i++) {
                if (!Double.isNaN(data[i][j])) {
                    n++;
                    sum += data[i][j];
                }
            }

            if (n == 0) {
                continue;
            }

            if (n < data.length) {
                double avg = sum / n;
                for (int i = 0; i < data.length; i++) {
                    if (Double.isNaN(data[i][j])) {
                        data[i][j] = avg;
                    }
                }
            }
        }
    }
}
