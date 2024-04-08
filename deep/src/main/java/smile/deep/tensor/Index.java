/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
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
package smile.deep.tensor;

import org.bytedeco.pytorch.SymInt;
import org.bytedeco.pytorch.SymIntOptional;
import org.bytedeco.pytorch.TensorIndex;
import org.bytedeco.pytorch.Tensor;

/**
 * Indexing a tensor.
 *
 * @author Haifeng Li
 */
public class Index {
    /** PyTorch tensor index. */
    TensorIndex value;

    /**
     * Constructor.
     * @param index PyTorch tensor index.
     */
    Index(TensorIndex index) {
        this.value = index;
    }

    /**
     * The ellipsis (...) is used to slice higher-dimensional data structures as in numpy.
     * It's designed to mean at this point, insert as many full slices (:) to extend
     * the multi-dimensional slice to all dimensions.
     */
    public static Index Ellipsis = new Index(new TensorIndex("..."));

    /**
     * The colon (:) is used to slice all elements of a dimension.
     */
    public static Index Colon = new Index(new TensorIndex(":"));

    /**
     * Returns the index of a single element in a dimension.
     *
     * @param i the element index.
     * @return the index.
     */
    public static Index of(long i) {
        return new Index(new TensorIndex(i));
    }

    /**
     * Returns the index of multiple elements in a dimension.
     *
     * @param indices the indices of multiple elements.
     * @return the index.
     */
    public static Index of(long... indices) {
        return new Index(new TensorIndex(Tensor.create(indices)));
    }

    /**
     * Returns the slice index for [start, end) with step 1.
     *
     * @param start the start index.
     * @param end the end index.
     * @return the slice.
     */
    public static Index slice(Long start, Long end) {
        return slice(start, end, 1L);
    }

    /**
     * Returns the slice index for [start, end) with the given step.
     *
     * @param start the start index.
     * @param end the end index.
     * @param step the incremental step.
     * @return the slice.
     */
    public static Index slice(Long start, Long end, Long step) {
        return new Index(new TensorIndex(new org.bytedeco.pytorch.Slice(
                start == null ? new SymIntOptional() : new SymIntOptional(new SymInt(start.longValue())),
                end == null ? new SymIntOptional() : new SymIntOptional(new SymInt(end.longValue())),
                step == null ? new SymIntOptional() : new SymIntOptional(new SymInt(step.longValue()))
        )));
    }
}
