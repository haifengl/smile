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
 * Weighted Pair Group Method with Arithmetic mean. WPGMA down-weights the
 * largest group by giving equal weights to the two branches of the dendrogram
 * that are about to fuse.
 * <p>
 * Note that the terms weighted and unweighted refer to the final result,
 * not the math by which it is achieved. Thus the simple averaging in WPGMA
 * produces a weighted result, and the proportional averaging in UPGMA produces
 * an unweighted result.
 * 
 * @author Haifeng Li
 */
public class WPGMALinkage extends Linkage {
    /**
     * Constructor.
     * @param proximity  the proximity matrix to store the distance measure of
     * dissimilarity. To save space, we only need the lower half of matrix.
     */
    public WPGMALinkage(double[][] proximity) {
        super(proximity);
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
    public WPGMALinkage(int size, float[] proximity) {
        super(size, proximity);
    }

    /** Given a set of data, computes the proximity and then the linkage. */
    public static WPGMALinkage of(double[][] data) {
        return new WPGMALinkage(data.length, proximity(data));
    }

    /** Given a set of data, computes the proximity and then the linkage. */
    public static <T> WPGMALinkage of(T[] data, Distance<T> distance) {
        return new WPGMALinkage(data.length, proximity(data, distance));
    }

    @Override
    public String toString() {
        return "WPGMA linkage";
    }

    @Override
    public void merge(int i, int j) {
        for (int k = 0; k < i; k++) {
            proximity[index(i, k)] = (d(i, k) + d(j, k)) / 2;
        }

        for (int k = i+1; k < size; k++) {
            proximity[index(k, i)] = (d(k, i) + d(j, k)) / 2;
        }
    }
}
