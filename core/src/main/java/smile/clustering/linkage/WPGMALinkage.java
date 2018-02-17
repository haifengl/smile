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
        init(proximity);
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
