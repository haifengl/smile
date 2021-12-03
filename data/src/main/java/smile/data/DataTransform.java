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
     * Fits the transform on the data.
     * @param data the training data.
     */
    void fit(DataFrame data);

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
        final DataTransform self = this;
        return new DataTransform() {
            @Override
            public void fit(DataFrame data) {
                self.fit(data);
                after.fit(self.apply(data));
            }

            @Override
            public Tuple apply(Tuple tuple) {
                return after.apply(self.apply(tuple));
            }
        };
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
        final DataTransform self = this;
        return new DataTransform() {
            @Override
            public void fit(DataFrame data) {
                before.fit(data);
                self.fit(before.apply(data));
            }

            @Override
            public Tuple apply(Tuple tuple) {
                return self.apply(before.apply(tuple));
            }
        };
    }
}
