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
        init(proximity);
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
