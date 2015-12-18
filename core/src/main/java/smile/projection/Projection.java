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

package smile.projection;

/**
 * A projection is a kind of feature extraction technique that transforms data
 * from the input space to a feature space, linearly or nonlinearly. Often,
 * projections are used to reduce dimensionality, for example PCA and random
 * projection. However, kernel-based methods, e.g. Kernel PCA, can actually map
 * the data into a much higher dimensional space.
 *
 * @author Haifeng Li
 */
public interface Projection<T> {
    /**
     * Project a data point to the feature space.
     */
    public double[] project(T x);

    /**
     * Project a set of data toe the feature space.
     */
    public double[][] project(T[] x);
}
