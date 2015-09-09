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

package smile.feature;

/**
 * Univariate feature ranking metric.
 * 
 * @author Haifeng Li
 */
public interface FeatureRanking {
    
    /**
     * Univariate feature ranking. Note that this method actually does NOT rank
     * the features. It just returns the metric values of each feature. The
     * caller can then rank and select features.
     * 
     * @param x a n-by-p matrix of n instances with p features.
     * @param y class labels in [0, k), where k is the number of classes.
     * @return the metric values of each feature.
     */
    public double[] rank(double[][] x, int[] y);
    
}
