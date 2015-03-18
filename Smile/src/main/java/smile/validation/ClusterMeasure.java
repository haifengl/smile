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

package smile.validation;

/**
 * An abstract interface to measure the clustering performance.
 *
 * @author Haifeng Li
 */
public interface ClusterMeasure {

    /**
     * Returns an index to measure the quality of clustering.
     * @param y1 the cluster labels.
     * @param y2 the alternative cluster labels.
     */
    public double measure(int[] y1, int[] y2);

}
