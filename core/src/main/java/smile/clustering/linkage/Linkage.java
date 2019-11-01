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

package smile.clustering.linkage;

import smile.math.MathEx;
import smile.math.distance.Distance;

/**
 * A measure of dissimilarity between clusters (i.e. sets of observations).
 * 
 * <h2>References</h2>
 * <ol>
 * <li> Anil K. Jain, Richard C. Dubes. Algorithms for clustering data. 1988.</li> 
 * </ol>
 * 
 * @see smile.clustering.HierarchicalClustering
 *
 * @author Haifeng Li
 */
public abstract class Linkage {
    /** The data size. */
    int size;

    /**
     * Linearized proximity matrix to store the pair-wise distance measure
     * as dissimilarity between clusters. To save space, we only need the
     * lower half of matrix. And we use float instead of double to save
     * more space, which also help speed performance. During the
     * clustering, this matrix will be updated to reflect the dissimilarity
     * of merged clusters.
     */
    float[] proximity;

    /** Initialize the linkage with the lower triangular proximity matrix. */
    public Linkage(double[][] proximity) {
        this.size = proximity.length;
        this.proximity = new float[size * (size+1) / 2];

        // row wise
        /*
        for (int i = 0, k = 0; i < size; i++) {
            double[] pi = proximity[i];
            for (int j = 0; j <= i; j++, k++) {
                this.proximity[k] = (float) pi[j];
            }
        }
        */

        // column wise
        for (int j = 0, k = 0; j < size; j++) {
            for (int i = j; i < size; i++, k++) {
                this.proximity[k] = (float) proximity[i][j];
            }
        }
    }

    /**
     * Initialize the linkage with the lower triangular proximity matrix.
     * @param size the data size.
     * @param proximity column-wise linearized proximity matrix that stores
     *                  only the lower half. The length of proximity should be
     *                  size * (size+1) / 2.
     *                  To save space, Linkage will use this argument directly
     *                  without copy. The elements may be modified.
     */
    public Linkage(int size, float[] proximity) {
        if (proximity.length != size * (size+1) / 2) {
            throw new IllegalArgumentException(String.format("The length of proximity is %d, expected %d", proximity.length, size * (size+1) / 2));
        }

        this.size = size;
        this.proximity = proximity;
    }

    int index(int i, int j) {
        // row wise
        // return i > j ? i*(i+1)/2 + j : j*(j+1)/2 + i;
        // column wise
        return i > j ? proximity.length - (size-j)*(size-j+1)/2 + i - j : proximity.length - (size-i)*(size-i+1)/2 + j - i;
    }

    /** Returns the proximity matrix size. */
    public int size() {
        return size;
    }

    /**
     * Returns the distance/dissimilarity between two clusters/objects, which
     * are indexed by integers.
     */
    public float d(int i, int j) {
        return proximity[index(i, j)];
    }

    /**
     * Merge two clusters into one and update the proximity matrix.
     * @param i cluster id.
     * @param j cluster id.
     */
    public abstract void merge(int i, int j);

    /** Calculate the proximity matrix (linearized in column major) with Euclidean distance. */
    public static float[] proximity(double[][] data) {
        int n = data.length;
        int length = n * (n+1) / 2;

        float[] proximity = new float[length];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < i; j++) {
                int k = length - (n-j)*(n-j+1)/2 + i - j;
                proximity[k] = (float) MathEx.distance(data[i], data[j]);
            }
        }

        return proximity;
    }

    /** Calculate the proximity matrix (linearized in column major). */
    public static <T> float[] proximity(T[] data, Distance<T> distance) {
        int n = data.length;
        int length = n * (n+1) / 2;

        float[] proximity = new float[length];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < i; j++) {
                int k = length - (n-j)*(n-j+1)/2 + i - j;
                proximity[k] = (float) distance.d(data[i], data[j]);
            }
        }

        return proximity;
    }
}
