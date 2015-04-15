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
        this.proximity = proximity;
        for (int i = 0; i < proximity.length; i++) {
            for (int j = 0; j < i; j++)
                proximity[i][j] *= proximity[i][j];
        }
    }

    @Override
    public String toString() {
        return "WPGMC linkage";
    }

    @Override
    public void merge(int i, int j) {
        for (int k = 0; k < i; k++) {
            proximity[i][k] = (proximity[i][k] + proximity[j][k]) / 2 - proximity[j][i] / 4;
        }

        for (int k = i+1; k < j; k++) {
            proximity[k][i] = (proximity[k][i] + proximity[j][k]) / 2 - proximity[j][i] / 4;
        }

        for (int k = j+1; k < proximity.length; k++) {
            proximity[k][i] = (proximity[k][i] + proximity[k][j]) / 2 - proximity[j][i] / 4;
        }
    }
}
