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

import java.io.Serializable;
import java.util.function.Function;

/**
 * Data transformation interface.
 *
 * @author Haifeng Li
 */
public interface DataTransform extends Function<Tuple, Tuple>, Serializable {
    /**
     * Fits a pipeline of data transforms.
     *
     * @param data the training data.
     * @param trainers the training algorithm to fit the transforms to apply one after one.
     * @return a composed transform.
     */
    @SafeVarargs
    static DataTransform fit(DataFrame data, Function<DataFrame, DataTransform>... trainers) {
        DataTransform pipeline = trainers[0].apply(data);
        for (int i = 1; i < trainers.length; i++) {
            data = pipeline.apply(data);
            pipeline = pipeline.andThen(trainers[i].apply(data));
        }
        return pipeline;
    }

    /**
     * Returns a pipeline of data transforms.
     *
     * @param transforms the transforms to apply one after one.
     * @return a composed transform.
     */
    static DataTransform pipeline(DataTransform... transforms) {
        DataTransform pipeline = transforms[0];
        for (int i = 1; i < transforms.length; i++) {
            pipeline = pipeline.andThen(transforms[i]);
        }
        return pipeline;
    }

    /**
     * Applies this transform to the given argument.
     * @param data the input data frame.
     * @return the transformed data frame.
     */
    default DataFrame apply(DataFrame data) {
        return data.stream().map(this).collect(DataFrame.Collectors.collect());
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
    default DataTransform andThen(DataTransform after) {
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
    default DataTransform compose(DataTransform before) {
        return (Tuple t) -> apply(before.apply(t));
    }
}
