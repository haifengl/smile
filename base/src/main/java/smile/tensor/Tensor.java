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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.tensor;

import smile.math.MathEx;

/**
 * A Tensor is a multidimensional array containing elements of a single data type.
 *
 * @author Haifeng Li
 */
public interface Tensor {
    /**
     * Returns the element data type.
     * @return the element data type.
     */
    ScalarType scalarType();

    /**
     * Returns the number of dimensions of tensor.
     * @return the number of dimensions of tensor
     */
    int dim();

    /**
     * Returns the size of given dimension.
     * @param dim dimension index.
     * @return the size of given dimension.
     */
    int size(int dim);

    /**
     * Returns the number of tensor elements. For tensors with packed storage
     * (e.g., BandMatrix, SparseMatrix, SymmMatrix), it returns the number of
     * non-zero elements.
     * @return the number of tensor elements.
     */
    default long length() {
        return MathEx.product(shape());
    }

    /**
     * Returns the shape of tensor. That is a list of the extent of each dimension.
     * @return the shape of tensor.
     */
    int[] shape();

    /**
     * Returns a tensor with the same data and number of elements
     * but with the specified shape. This method returns a view
     * if shape is compatible with the current shape.
     *
     * @param shape the new shape of tensor.
     * @return the tensor with the specified shape.
     */
    Tensor reshape(int... shape);

    /**
     * Updates a sub-tensor in place.
     *
     * @param value the sub-tensor.
     * @param index the index.
     * @return this tensor.
     */
    Tensor set(Tensor value, int... index);

    /**
     * Returns a portion of tensor given the index.
     * @param index the index along the dimensions.
     * @return the sub-tensor.
     */
    Tensor get(int... index);
}
