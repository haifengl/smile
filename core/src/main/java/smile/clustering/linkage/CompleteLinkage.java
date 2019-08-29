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

/**
 * Complete linkage. This is the opposite of single linkage. Distance between
 * groups is now defined as the distance between the most distant pair of
 * objects, one from each group.
 * 
 * @author Haifeng Li
 */
public class CompleteLinkage extends Linkage {
    /**
     * Constructor.
     * @param proximity  the proximity matrix to store the distance measure of
     * dissimilarity. To save space, we only need the lower half of matrix.
     */
    public CompleteLinkage(double[][] proximity) {
        super(proximity);
    }

    /**
     * Constructor.
     * @param size the data size.
     * @param proximity column-wise linearized proximity matrix that stores
     *                  only the lower half without diagonal elements.
     */
    public CompleteLinkage(int size, float[] proximity) {
        super(size, proximity);
    }

    @Override
    public String toString() {
        return "complete linkage";
    }

    @Override
    public void merge(int i, int j) {
        for (int k = 0; k < i; k++) {
            proximity[index(i, k)] = Math.max(d(i, k), d(j, k));
        }

        for (int k = i+1; k < size; k++) {
            proximity[index(k, i)] = Math.max(d(k, i), d(j, k));
        }
    }
}
