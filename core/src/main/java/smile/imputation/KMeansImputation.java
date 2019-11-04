/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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
 *******************************************************************************/

package smile.imputation;

import smile.clustering.KMeans;
import smile.clustering.PartitionClustering;

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
        this(k, 8);
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

        KMeans kmeans = PartitionClustering.run(runs, () -> KMeans.lloyd(data, k));

        for (int i = 0; i < k; i++) {
            if (kmeans.size[i] > 0) {
                double[][] d = new double[kmeans.size[i]][];
                for (int j = 0, m = 0; j < data.length; j++) {
                    if (kmeans.y[j] == i) {
                        d[m++] = data[j];
                    }
                }

                MissingValueImputation.imputeWithColumnAverage(d);
            }
        }

        // In case of some clusters miss all values in some columns.
        MissingValueImputation.imputeWithColumnAverage(data);
    }
}
