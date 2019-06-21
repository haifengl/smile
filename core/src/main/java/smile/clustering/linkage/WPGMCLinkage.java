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
        init(proximity);
        for (int i = 0; i < this.proximity.length; i++) {
            this.proximity[i] *= this.proximity[i];
        }
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
