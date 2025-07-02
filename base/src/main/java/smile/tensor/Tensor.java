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

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * A Tensor is a multidimensional array containing elements of a single data type.
 *
 * @author Haifeng Li
 */
public interface Tensor extends AutoCloseable {
    /**
     * Creates a tensor.
     * @param valueLayout the data type of tensor elements.
     * @param shape the shape of tensor.
     * @return the tensor.
     */
    static Tensor of(ValueLayout valueLayout, int... shape) {
        long length = MathEx.product(shape);
        var arena = Arena.ofShared();
        var memory = arena.allocate(valueLayout, length);
        return new TensorImpl(arena, memory, valueLayout, shape);
    }

    /**
     * Wraps an array in a tensor.
     * @param data the data array.
     * @param shape the shape of tensor.
     * @return the tensor.
     */
    static Tensor of(byte[] data, int... shape) {
        long length = MathEx.product(shape);
        if (length != data.length) {
            throw new IllegalArgumentException(String.format("The length of shape %d != %d the length of array", length, data.length));
        }
        var memory = MemorySegment.ofArray(data);
        return new TensorImpl(memory, ValueLayout.JAVA_BYTE, shape);
    }

    /**
     * Wraps an array in a tensor.
     * @param data the data array.
     * @param shape the shape of tensor.
     * @return the tensor.
     */
    static Tensor of(int[] data, int... shape) {
        long length = MathEx.product(shape);
        if (length != data.length) {
            throw new IllegalArgumentException(String.format("The length of shape %d != %d the length of array", length, data.length));
        }
        var memory = MemorySegment.ofArray(data);
        return new TensorImpl(memory, ValueLayout.JAVA_INT, shape);
    }

    /**
     * Wraps an array in a tensor.
     * @param data the data array.
     * @param shape the shape of tensor.
     * @return the tensor.
     */
    static Tensor of(float[] data, int... shape) {
        long length = MathEx.product(shape);
        if (length != data.length) {
            throw new IllegalArgumentException(String.format("The length of shape %d != %d the length of array", length, data.length));
        }
        var memory = MemorySegment.ofArray(data);
        return new TensorImpl(memory, ValueLayout.JAVA_FLOAT, shape);
    }

    /**
     * Wraps an array in a tensor.
     * @param data the data array.
     * @param shape the shape of tensor.
     * @return the tensor.
     */
    static Tensor of(double[] data, int... shape) {
        long length = MathEx.product(shape);
        if (length != data.length) {
            throw new IllegalArgumentException(String.format("The length of shape %d != %d the length of array", length, data.length));
        }
        var memory = MemorySegment.ofArray(data);
        return new TensorImpl(memory, ValueLayout.JAVA_DOUBLE, shape);
    }

    /**
     * Returns the layout that models values of basic data types.
     * @return the layout that models values of basic data types.
     */
    ValueLayout valueLayout();

    /**
     * Returns the memory segment of underlying data.
     * @return the memory segment.
     */
    MemorySegment memory();

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
     * Returns the number of tensor elements.
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
     * Updates an element in place.
     *
     * @param value the new element value.
     * @param index the element index.
     * @return this tensor.
     */
    Tensor set(boolean value, int... index);

    /**
     * Updates an element in place.
     *
     * @param value the new element value.
     * @param index the element index.
     * @return this tensor.
     */
    Tensor set(byte value, int... index);

    /**
     * Updates an element in place.
     *
     * @param value the new element value.
     * @param index the element index.
     * @return this tensor.
     */
    Tensor set(short value, int... index);

    /**
     * Updates an element in place.
     *
     * @param value the new element value.
     * @param index the element index.
     * @return this tensor.
     */
    Tensor set(int value, int... index);

    /**
     * Updates an element in place.
     *
     * @param value the new element value.
     * @param index the element index.
     * @return this tensor.
     */
    Tensor set(long value, int... index);

    /**
     * Updates an element in place.
     *
     * @param value the new element value.
     * @param index the element index.
     * @return this tensor.
     */
    Tensor set(float value, int... index);

    /**
     * Updates an element in place.
     *
     * @param value the new element value.
     * @param index the element index.
     * @return this tensor.
     */
    Tensor set(double value, int... index);

    /**
     * Returns a portion of tensor given the index.
     * @param index the index along the dimensions.
     * @return the sub-tensor.
     */
    Tensor get(int... index);

    /**
     * Returns the boolean value of element at given index.
     *
     * @param index the index along the dimensions.
     * @return the element value.
     */
    boolean getBoolean(int... index);

    /**
     * Returns the byte value of element at given index.
     *
     * @param index the index along the dimensions.
     * @return the element value.
     */
    byte getByte(int... index);

    /**
     * Returns the short value of element at given index.
     *
     * @param index the index along the dimensions.
     * @return the element value.
     */
    short getShort(int... index);

    /**
     * Returns the int value of element at given index.
     *
     * @param index the index along the dimensions.
     * @return the element value.
     */
    int getInt(int... index);

    /**
     * Returns the long value of element at given index.
     *
     * @param index the index along the dimensions.
     * @return the element value.
     */
    long getLong(int... index);

    /**
     * Returns the float value of element at given index.
     *
     * @param index the index along the dimensions.
     * @return the element value.
     */
    float getFloat(int... index);

    /**
     * Returns the double value of element at given index.
     *
     * @param index the index along the dimensions.
     * @return the element value.
     */
    double getDouble(int... index);
}
