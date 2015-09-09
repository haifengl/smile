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

package smile.math.distance;

/**
 * An interface to calculate a distance measure between two objects. A distance
 * function maps pairs of points into the nonnegative reals and has to satisfy
 * <ul>
 * <li> non-negativity: d(x, y) &ge; 0
 * <li> isolation: d(x, y) = 0 if and only if x = y
 * <li> symmetry: d(x, y) = d(x, y)
 * </ul>.
 * Note that a distance function is not required to satisfy triangular inequality
 * |x - y| + |y - z| &ge; |x - z|, which is necessary for a metric.
 *
 * @author Haifeng Li
 */
public interface Distance<T> {
    /**
     * Returns the distance measure between two objects.
     */
    public double d(T x, T y);
}
