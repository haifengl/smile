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
    double[] rank(double[][] x, int[] y);
    
}
