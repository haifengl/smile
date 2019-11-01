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

import smile.math.distance.Distance;

/**
 * Ward's linkage. Ward's linkage follows the analysis of variance approach
 * The dissimilarity between two clusters is computed as the
 * increase in the "error sum of squares" (ESS) after fusing two clusters
 * into a single cluster. Ward's Method seeks to choose the successive
 * clustering steps so as to minimize the increase in ESS at each step.
 * Note that it is only valid for Euclidean distance based proximity matrix.
 * 
 * @author Haifeng Li
 */
public class WardLinkage extends Linkage {
    /**
     * The number of samples in each cluster.
     */
    private int[] n;

    /**
     * Constructor.
     * @param proximity  the proximity matrix to store the distance measure of
     * dissimilarity. To save space, we only need the lower half of matrix.
     */
    public WardLinkage(double[][] proximity) {
        super(proximity);
        init();
    }

    /**
     * Constructor. Initialize the linkage with the lower triangular proximity matrix.
     * @param size the data size.
     * @param proximity column-wise linearized proximity matrix that stores
     *                  only the lower half. The length of proximity should be
     *                  size * (size+1) / 2.
     *                  To save space, Linkage will use this argument directly
     *                  without copy. The elements may be modified.
     */
    public WardLinkage(int size, float[] proximity) {
        super(size, proximity);
        init();
    }

    /** Initialize sample size. */
    private void init() {
        n = new int[size];
        for (int i = 0; i < size; i++) {
            n[i] = 1;
        }

        for (int i = 0; i < proximity.length; i++) {
            proximity[i] *= proximity[i];
        }
    }

    /** Given a set of data, computes the proximity and then the linkage. */
    public static WardLinkage of(double[][] data) {
        return new WardLinkage(data.length, proximity(data));
    }

    /** Given a set of data, computes the proximity and then the linkage. */
    public static <T> WardLinkage of(T[] data, Distance<T> distance) {
        return new WardLinkage(data.length, proximity(data, distance));
    }

    @Override
    public String toString() {
        return "Ward's linkage";
    }

    @Override
    public void merge(int i, int j) {
        float nij = n[i] + n[j];

        for (int k = 0; k < i; k++) {
            proximity[index(i, k)] = (d(i, k) * (n[i] + n[k]) + d(j, k) * (n[j] + n[k]) - d(j, i) * n[k]) / (nij + n[k]);
        }

        for (int k = i+1; k < j; k++) {
            proximity[index(k, i)] = (d(k, i) * (n[i] + n[k]) + d(j, k) * (n[j] + n[k]) - d(j, i) * n[k]) / (nij + n[k]);
        }

        for (int k = j+1; k < size; k++) {
            proximity[index(k, i)] = (d(k, i) * (n[i] + n[k]) + d(k, j) * (n[j] + n[k]) - d(j, i) * n[k]) / (nij + n[k]);
        }

        n[i] += n[j];
    }
}
