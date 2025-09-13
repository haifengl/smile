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

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;
import smile.math.MathEx;

/**
 * A simple on-heap Tensor implementation.
 *
 * @author Haifeng Li
 */
public class JTensor extends AbstractTensor {
    /**
     * An on-heap memory segment that stores tensor elements.
     */
    private final transient MemorySegment memory;
    /**
     * The data type of tensor elements.
     */
    private final transient ValueLayout valueLayout;

    /**
     * Private constructor.
     * @param memory the memory segment of data.
     * @param valueLayout the data type of tensor elements.
     * @param shape the shape of tensor.
     */
    private JTensor(MemorySegment memory, ValueLayout valueLayout, int[] shape) {
        super(shape);
        this.memory = memory;
        this.valueLayout = valueLayout;
    }

    /**
     * Returns the layout that models values of basic data types.
     * @return the layout that models values of basic data types.
     */
    public ValueLayout valueLayout() {
        return valueLayout;
    }

    /**
     * Returns the memory segment of underlying data.
     * @return the memory segment.
     */
    public MemorySegment memory() {
        return memory;
    }

    @Override
    public String toString() {
        return "Tensor" + Arrays.toString(shape());
    }

    @Override
    public ScalarType scalarType() {
        return switch (valueLayout) {
            case ValueLayout.OfByte layout -> ScalarType.Int8;
            case ValueLayout.OfShort layout -> ScalarType.Int16;
            case ValueLayout.OfInt layout -> ScalarType.Int32;
            case ValueLayout.OfLong layout -> ScalarType.Int64;
            case ValueLayout.OfFloat layout -> ScalarType.Float32;
            case ValueLayout.OfDouble layout -> ScalarType.Float64;
            case ValueLayout.OfBoolean layout -> ScalarType.Int8;
            case ValueLayout.OfChar layout -> ScalarType.Int16;
            default -> throw new IllegalStateException("Unsupported ValueLayout: " + valueLayout);
        };
    }

    @Override
    public Tensor reshape(int... shape) {
        long p1 = MathEx.product(shape);
        long p2 = length();
        if (p1 != p2) {
            throw new IllegalArgumentException(String.format("The length of new shape %d != %d", p1, p2));
        }
        return new JTensor(memory, valueLayout, shape);
    }

    @Override
    public Tensor set(Tensor value, int... index) {
        if (value instanceof JTensor B) {
            if (!valueLayout.equals(B.valueLayout)) {
                throw new UnsupportedOperationException("set with tensor of different ValueLayout: " + B.valueLayout);
            }
            long offset = offset(index) * valueLayout.byteSize();
            MemorySegment.copy(B.memory, 0, memory, offset, B.memory.byteSize());
            return this;
        }
        throw new UnsupportedOperationException("Unsupported Tensor type: " + value.getClass());
    }

    @Override
    public JTensor get(int... index) {
        long offset = offset(index) * valueLayout.byteSize();
        return new JTensor(memory.asSlice(offset), valueLayout, Arrays.copyOfRange(shape, index.length, shape.length));
    }

    /**
     * Updates an element in place.
     *
     * @param value the new element value.
     * @param index the element index.
     * @return this tensor.
     */
    public Tensor set(boolean value, int... index) {
        long offset = offset(index);
        memory.setAtIndex((ValueLayout.OfBoolean) valueLayout, offset, value);
        return this;
    }

    /**
     * Updates an element in place.
     *
     * @param value the new element value.
     * @param index the element index.
     * @return this tensor.
     */
    public Tensor set(byte value, int... index) {
        long offset = offset(index);
        memory.setAtIndex((ValueLayout.OfByte) valueLayout, offset, value);
        return this;
    }

    /**
     * Updates an element in place.
     *
     * @param value the new element value.
     * @param index the element index.
     * @return this tensor.
     */
    public Tensor set(short value, int... index) {
        long offset = offset(index);
        memory.setAtIndex((ValueLayout.OfShort) valueLayout, offset, value);
        return this;
    }

    /**
     * Updates an element in place.
     *
     * @param value the new element value.
     * @param index the element index.
     * @return this tensor.
     */
    public Tensor set(int value, int... index) {
        long offset = offset(index);
        memory.setAtIndex((ValueLayout.OfInt) valueLayout, offset, value);
        return this;
    }

    /**
     * Updates an element in place.
     *
     * @param value the new element value.
     * @param index the element index.
     * @return this tensor.
     */
    public Tensor set(long value, int... index) {
        long offset = offset(index);
        memory.setAtIndex((ValueLayout.OfLong) valueLayout, offset, value);
        return this;
    }

    /**
     * Updates an element in place.
     *
     * @param value the new element value.
     * @param index the element index.
     * @return this tensor.
     */
    public Tensor set(float value, int... index) {
        long offset = offset(index);
        memory.setAtIndex((ValueLayout.OfFloat) valueLayout, offset, value);
        return this;
    }

    /**
     * Updates an element in place.
     *
     * @param value the new element value.
     * @param index the element index.
     * @return this tensor.
     */
    public Tensor set(double value, int... index) {
        long offset = offset(index);
        memory.setAtIndex((ValueLayout.OfDouble) valueLayout, offset, value);
        return this;
    }

    /**
     * Returns the boolean value of element at given index.
     *
     * @param index the index along the dimensions.
     * @return the element value.
     */
    public boolean getBoolean(int... index) {
        long offset = offset(index);
        return memory.getAtIndex((ValueLayout.OfBoolean) valueLayout, offset);
    }

    /**
     * Returns the byte value of element at given index.
     *
     * @param index the index along the dimensions.
     * @return the element value.
     */
    public byte getByte(int... index) {
        long offset = offset(index);
        return memory.getAtIndex((ValueLayout.OfByte) valueLayout, offset);
    }

    /**
     * Returns the short value of element at given index.
     *
     * @param index the index along the dimensions.
     * @return the element value.
     */
    public short getShort(int... index) {
        long offset = offset(index);
        return memory.getAtIndex((ValueLayout.OfShort) valueLayout, offset);
    }

    /**
     * Returns the int value of element at given index.
     *
     * @param index the index along the dimensions.
     * @return the element value.
     */
    public int getInt(int... index) {
        long offset = offset(index);
        return memory.getAtIndex((ValueLayout.OfInt) valueLayout, offset);
    }

    /**
     * Returns the long value of element at given index.
     *
     * @param index the index along the dimensions.
     * @return the element value.
     */
    public long getLong(int... index) {
        long offset = offset(index);
        return memory.getAtIndex((ValueLayout.OfLong) valueLayout, offset);
    }

    /**
     * Returns the float value of element at given index.
     *
     * @param index the index along the dimensions.
     * @return the element value.
     */
    public float getFloat(int... index) {
        long offset = offset(index);
        return memory.getAtIndex((ValueLayout.OfFloat) valueLayout, offset);
    }

    /**
     * Returns the double value of element at given index.
     *
     * @param index the index along the dimensions.
     * @return the element value.
     */
    public double getDouble(int... index) {
        long offset = offset(index);
        return memory.getAtIndex((ValueLayout.OfDouble) valueLayout, offset);
    }

    /**
     * Wraps an array in a tensor.
     * @param data the data array.
     * @param shape the shape of tensor.
     * @return the tensor.
     */
    static JTensor of(byte[] data, int... shape) {
        long length = MathEx.product(shape);
        if (length != data.length) {
            throw new IllegalArgumentException(String.format("The length of shape %d != %d the length of array", length, data.length));
        }
        var memory = MemorySegment.ofArray(data);
        return new JTensor(memory, ValueLayout.JAVA_BYTE, shape);
    }

    /**
     * Wraps an array in a tensor.
     * @param data the data array.
     * @param shape the shape of tensor.
     * @return the tensor.
     */
    static JTensor of(int[] data, int... shape) {
        long length = MathEx.product(shape);
        if (length != data.length) {
            throw new IllegalArgumentException(String.format("The length of shape %d != %d the length of array", length, data.length));
        }
        var memory = MemorySegment.ofArray(data);
        return new JTensor(memory, ValueLayout.JAVA_INT, shape);
    }

    /**
     * Wraps an array in a tensor.
     * @param data the data array.
     * @param shape the shape of tensor.
     * @return the tensor.
     */
    static JTensor of(float[] data, int... shape) {
        long length = MathEx.product(shape);
        if (length != data.length) {
            throw new IllegalArgumentException(String.format("The length of shape %d != %d the length of array", length, data.length));
        }
        var memory = MemorySegment.ofArray(data);
        return new JTensor(memory, ValueLayout.JAVA_FLOAT, shape);
    }

    /**
     * Wraps an array in a tensor.
     * @param data the data array.
     * @param shape the shape of tensor.
     * @return the tensor.
     */
    static JTensor of(double[] data, int... shape) {
        long length = MathEx.product(shape);
        if (length != data.length) {
            throw new IllegalArgumentException(String.format("The length of shape %d != %d the length of array", length, data.length));
        }
        var memory = MemorySegment.ofArray(data);
        return new JTensor(memory, ValueLayout.JAVA_DOUBLE, shape);
    }
}
