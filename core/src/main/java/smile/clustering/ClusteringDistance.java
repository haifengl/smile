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
package smile.clustering;

/**
 * Clustering distance measure.
 *
 * @author Haifeng Li
 */
public enum ClusteringDistance {
    /**
     * Squared Euclidean distance for K-Means.
     */
    EUCLIDEAN,
    /**
     * Squared Euclidean distance with missing value handling for K-Means.
     */
    EUCLIDEAN_MISSING_VALUES,
    /**
     * Jensen-Shannon divergence for SIB.
     */
    JENSEN_SHANNON_DIVERGENCE
}