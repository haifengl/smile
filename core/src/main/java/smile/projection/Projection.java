/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
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
    double[] project(T x);

    /**
     * Project a set of data to the feature space.
     */
    double[][] project(T[] x);
}
