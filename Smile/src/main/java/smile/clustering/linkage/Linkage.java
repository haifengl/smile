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
    /**
     * The proximity matrix to store the pair-wise distance measure as
     * dissimilarity between clusters. To save space, we only need the
     * lower half of matrix. During the clustering, this matrix will be
     * updated to reflect the dissimilarity of merged clusters.
     */
    double[][] proximity;

    /**
     * Returns the proximity matrix.
     */
    public double[][] getProximity() {
        return proximity;
    }

    /**
     * Returns the distance/dissimilarity between two clusters/objects, which
     * are indexed by integers.
     */
    double d(int i, int j) {
        if (i > j)
            return proximity[i][j];
        else
            return proximity[j][i];
    }

    /**
     * Merge two clusters into one and update the proximity matrix.
     * @param i cluster id.
     * @param j cluster id.
     */
    public abstract void merge(int i, int j);
}
