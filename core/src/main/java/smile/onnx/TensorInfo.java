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
package smile.onnx;

import java.util.Arrays;

/**
 * Describes the type and shape of a tensor.
 *
 * <p>A dimension value of {@code -1} indicates a dynamic (symbolic) dimension
 * whose size is not known until runtime.
 *
 * @param elementType the element data type of the tensor.
 * @param shape       the shape of the tensor. Each element is the size of
 *                    that dimension, or {@code -1} for a dynamic dimension.
 * @param symbolicDimensions optional symbolic names for each dimension
 *                           (may be {@code null} or contain {@code null}
 *                           entries for unnamed dimensions).
 *
 * @author Haifeng Li
 */
public record TensorInfo(ElementType elementType, long[] shape, String[] symbolicDimensions) {

    /**
     * Constructor without symbolic dimension names.
     * @param elementType the element data type.
     * @param shape the shape of the tensor.
     */
    public TensorInfo(ElementType elementType, long[] shape) {
        this(elementType, shape, null);
    }

    /**
     * Returns the number of dimensions (rank) of this tensor.
     * @return the rank.
     */
    public int rank() {
        return shape.length;
    }

    /**
     * Returns the total number of elements in the tensor, or {@code -1}
     * if any dimension is dynamic.
     * @return the total element count, or -1.
     */
    public long elementCount() {
        long count = 1;
        for (long dim : shape) {
            if (dim < 0) return -1;
            count *= dim;
        }
        return count;
    }

    /**
     * Returns {@code true} if the tensor has any dynamic dimensions.
     * @return true if any dimension is -1.
     */
    public boolean isDynamic() {
        for (long dim : shape) {
            if (dim < 0) return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return "TensorInfo{elementType=" + elementType + ", shape=" + Arrays.toString(shape) + "}";
    }
}

