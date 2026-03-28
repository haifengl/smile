/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.data.transform;

import java.io.Serializable;
import java.util.function.Function;
import smile.data.DataFrame;
import smile.data.Tuple;

/**
 * Data transformation interface. In general, learning algorithms benefit
 * from standardization of the data set. If some outliers are present in
 * the set, robust transformers are more appropriate.
 *
 * @author Haifeng Li
 */
public interface Transform extends Function<Tuple, Tuple>, Serializable {
    /**
     * Fits a pipeline of data transforms.
     *
     * @param data the training data.
     * @param trainers the training algorithm to fit the transforms to apply one after one.
     * @return a composed transform.
     */
    @SafeVarargs
    static Transform fit(DataFrame data, Function<DataFrame, Transform>... trainers) {
        Transform pipeline = trainers[0].apply(data);
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
    static Transform pipeline(Transform... transforms) {
        Transform pipeline = transforms[0];
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
        var result = data.stream().map(this).toList();
        return DataFrame.of(result.getFirst().schema(), result);
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
    default Transform andThen(Transform after) {
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
    default Transform compose(Transform before) {
        return (Tuple t) -> apply(before.apply(t));
    }
}
