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
 * Unweighted Pair Group Method with Arithmetic mean (also known as average linkage).
 * The distance between two clusters is the mean distance between all possible
 * pairs of nodes in the two clusters. 
 * <p>
 * In bioinformatics, UPGMA is used for the creation of phenetic trees
 * (phenograms). UPGMA assumes a constant rate of evolution (molecular
 * clock hypothesis), and is not a well-regarded method for inferring
 * relationships unless this assumption has been tested and justified
 * for the data set being used.
 *
 * @author Haifeng Li
 */
public class UPGMALinkage extends Linkage {
    /**
     * The number of samples in each cluster.
     */
    private int[] n;

    /**
     * Constructor.
     * @param proximity  the proximity matrix to store the distance measure of
     * dissimilarity. To save space, we only need the lower half of matrix.
     */
    public UPGMALinkage(double[][] proximity) {
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
    public UPGMALinkage(int size, float[] proximity) {
        super(size, proximity);
        init();
    }

    /** Initialize sample size. */
    private void init() {
        n = new int[size];
        for (int i = 0; i < size; i++) {
            n[i] = 1;
        }
    }

    /** Given a set of data, computes the proximity and then the linkage. */
    public static UPGMALinkage of(double[][] data) {
        return new UPGMALinkage(data.length, proximity(data));
    }

    /** Given a set of data, computes the proximity and then the linkage. */
    public static <T> UPGMALinkage of(T[] data, Distance<T> distance) {
        return new UPGMALinkage(data.length, proximity(data, distance));
    }

    @Override
    public String toString() {
        return "UPGMA linkage";
    }

    @Override
    public void merge(int i, int j) {
        float sum = n[i] + n[j];

        for (int k = 0; k < i; k++) {
            proximity[index(i, k)] = d(i, k) * n[i] / sum + d(j, k) * n[j] / sum;
        }

        for (int k = i+1; k < size; k++) {
            proximity[index(k, i)] = d(k, i) * n[i] / sum + d(j, k) * n[j] / sum;
        }

        n[i] += n[j];
    }
}
