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
        init(proximity);
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
