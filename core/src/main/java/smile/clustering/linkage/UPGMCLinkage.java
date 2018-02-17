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

package smile.clustering.linkage;

/**
 * Unweighted Pair Group Method using Centroids (also known as centroid linkage).
 * The distance between two clusters is the Euclidean distance between their
 * centroids, as calculated by arithmetic mean. Only valid for Euclidean
 * distance based proximity matrix.
 * 
 * @author Haifeng Li
 */
public class UPGMCLinkage extends Linkage {
    /**
     * The number of samples in each cluster.
     */
    private int[] n;

    /**
     * Constructor.
     * @param proximity  the proximity matrix to store the distance measure of
     * dissimilarity. To save space, we only need the lower half of matrix.
     */
    public UPGMCLinkage(double[][] proximity) {
        init(proximity);
        n = new int[proximity.length];
        for (int i = 0; i < n.length; i++) {
            n[i] = 1;
        }

        for (int i = 0; i < this.proximity.length; i++) {
            this.proximity[i] *= this.proximity[i];
        }
    }

    @Override
    public String toString() {
        return "UPGMC linkage";
    }

    @Override
    public void merge(int i, int j) {
        float nij = n[i] + n[j];

        for (int k = 0; k < i; k++) {
            proximity[index(i, k)] = (d(i, k) * n[i] + d(j, k) * n[j] - d(j, i) * n[i] * n[j] / nij) / nij;
        }

        for (int k = i+1; k < j; k++) {
            proximity[index(k, i)] = (d(k, i) * n[i] + d(j, k) * n[j] - d(j, i) * n[i] * n[j] / nij) / nij;
        }

        for (int k = j+1; k < size; k++) {
            proximity[index(k, i)] = (d(k, i) * n[i] + d(k, j) * n[j] - d(j, i) * n[i] * n[j] / nij) / nij;
        }

        n[i] += n[j];
    }
}
