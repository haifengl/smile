/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
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
 * This class provides a skeletal implementation of the Tensor interface,
 * to minimize the effort required to implement this interface.
 *
 * @author Haifeng Li
 */
public abstract class AbstractTensor implements Tensor {
    /**
     * The shape of tensor. That is a list of the extent of each dimension.
     */
    final int[] shape;
    /**
     * In row-major order, the stride of a dimension is equal to
     * the product of the sizes of the lower-order dimensions.
     */
    final long[] stride;

    /**
     * Constructor.
     * @param shape the shape of tensor.
     */
    public AbstractTensor(int... shape) {
        this.shape = shape;
        int dim = shape.length;
        stride = new long[dim];
        stride[dim-1] = 1;
        for (int i = dim - 2; i >= 0; i--) {
            stride[i] = shape[i+1] * stride[i+1];
        }
    }

    /**
     * Returns the string representation of a floating number.
     * @param x the floating number.
     * @return the string representation of a floating number.
     */
    static String format(double x) {
        if (MathEx.isZero(x, 1E-7f)) {
            return "0.0000";
        }

        double ax = Math.abs(x);
        if (ax >= 1E-3 && ax < 1E7) {
            return String.format("%.4f", x);
        }

        return String.format("%.4e", x);
    }

    @Override
    public int dim() {
        return shape.length;
    }

    @Override
    public int size(int dim) {
        return shape[dim];
    }

    @Override
    public int[] shape() {
        return shape;
    }

    /**
     * Returns the offset of cell at the given index.
     * @param index the cell index.
     * @return the offset.
     */
    long offset(int[] index) {
        long offset = 0;
        for (int i = 0; i < index.length; i++) {
            offset += index[i] * stride[i];
        }
        return offset;
    }
}
