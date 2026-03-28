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

import smile.data.DataFrame;
import smile.data.Tuple;

/**
 * Invertible data transformation.
 *
 * @author Haifeng Li
 */
public interface InvertibleTransform extends Transform {
    /**
     * Inverse transform a tuple.
     * @param x a tuple.
     * @return the inverse transformed tuple.
     */
    Tuple invert(Tuple x);

    /**
     * Inverse transform a data frame.
     * @param data a data frame.
     * @return the inverse transformed data frame.
     */
    DataFrame invert(DataFrame data);
}
