/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.data;

/**
 * Data frame transformation interface.
 *
 * @author Haifeng Li
 */
public interface DataFrameTransform extends DataTransform<Tuple, Tuple> {
    /**
     * Applies this transform to the given argument.
     * @param data the input data frame.
     * @return the transformed data frame.
     */
    default DataFrame apply(DataFrame data) {
        return data.stream().map(this::apply).collect(DataFrame.Collectors.collect());
    }

    /**
     * Returns a composed function that first applies this function
     * to its input, and then applies the <code>after</code> function
     * to the result.
     *
     * @param after the transform to apply after this transform is applied.
     * @return a composed transform that first applies this transform and
     *         then applies the <code>after</code> transform.
     */
    default DataFrameTransform andThen(DataFrameTransform after) {
        return (Tuple t) -> after.apply(apply(t));
    }

    /**
     * Returns a composed function that first applies the <code>before</code>
     * function to its input, and then applies this function to the result.
     *
     * @param before the transform to apply before this transform is applied.
     * @return a composed transform that first applies the <code>before</code>
     *         transform and then applies this transform.
     */
    default DataFrameTransform compose(DataFrameTransform before) {
        return (Tuple v) -> apply(before.apply(v));
    }
}
