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
 * Single linkage. The distance between groups is defined as the distance
 * between the closest pair of objects, one from each group.
 * A drawback of this method is the so-called chaining phenomenon: clusters
 * may be forced together due to single elements being close to each other,
 * even though many of the elements in each cluster may be very distant to
 * each other.
 * <p>
 * Single linkage clustering is essentially the same as Kruskal's algorithm
 * for minimum spanning trees. However, in single linkage clustering, the
 * order in which clusters are formed is important, while for minimum spanning
 * trees what matters is the set of pairs of points that form distances chosen
 * by the algorithm.
 * 
 * @author Haifeng Li
 */
public class SingleLinkage extends Linkage {
    /**
     * Constructor.
     * @param proximity The proximity matrix to store the distance measure of
     * dissimilarity. To save space, we only need the lower half of matrix.
     */
    public SingleLinkage(double[][] proximity) {
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
    public SingleLinkage(int size, float[] proximity) {
        super(size, proximity);
    }

    /** Given a set of data, computes the proximity and then the linkage. */
    public static SingleLinkage of(double[][] data) {
        return new SingleLinkage(data.length, proximity(data));
    }

    /** Given a set of data, computes the proximity and then the linkage. */
    public static <T> SingleLinkage of(T[] data, Distance<T> distance) {
        return new SingleLinkage(data.length, proximity(data, distance));
    }

    @Override
    public String toString() {
        return "single linkage";
    }

    @Override
    public void merge(int i, int j) {
        for (int k = 0; k < i; k++) {
            proximity[index(i, k)] = Math.min(d(i, k), d(j, k));
        }

        for (int k = i+1; k < size; k++) {
            proximity[index(k, i)] = Math.min(d(k, i), d(j, k));
        }
    }
}
