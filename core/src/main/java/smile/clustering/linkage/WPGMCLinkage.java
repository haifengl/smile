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
 * Weighted Pair Group Method using Centroids (also known as median linkage).
 * The distance between two clusters is the Euclidean distance between their
 * weighted centroids. Only valid for Euclidean distance based proximity matrix.
 * 
 * @author Haifeng Li
 */
public class WPGMCLinkage extends Linkage {
    /**
     * Constructor.
     * @param proximity  the proximity matrix to store the distance measure of
     * dissimilarity. To save space, we only need the lower half of matrix.
     */
    public WPGMCLinkage(double[][] proximity) {
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
    public WPGMCLinkage(int size, float[] proximity) {
        super(size, proximity);
        init();
    }

    /** Intialize proximity. */
    private void init() {
        for (int i = 0; i < proximity.length; i++) {
            proximity[i] *= proximity[i];
        }
    }

    /** Given a set of data, computes the proximity and then the linkage. */
    public static WPGMCLinkage of(double[][] data) {
        return new WPGMCLinkage(data.length, proximity(data));
    }

    /** Given a set of data, computes the proximity and then the linkage. */
    public static <T> WPGMCLinkage of(T[] data, Distance<T> distance) {
        return new WPGMCLinkage(data.length, proximity(data, distance));
    }

    @Override
    public String toString() {
        return "WPGMC linkage";
    }

    @Override
    public void merge(int i, int j) {
        for (int k = 0; k < i; k++) {
            proximity[index(i, k)] = (d(i, k) + d(j, k)) / 2 - d(j, i) / 4;
        }

        for (int k = i+1; k < j; k++) {
            proximity[index(k, i)] = (d(k, i) + d(j, k)) / 2 - d(j, i) / 4;
        }

        for (int k = j+1; k < size; k++) {
            proximity[index(k, i)] = (d(k, i) + d(k, j)) / 2 - d(j, i) / 4;
        }
    }
}
