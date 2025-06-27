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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.util.Arrays;
import smile.math.MathEx;

/**
 * This class provides a skeletal implementation of the Tensor interface,
 * to minimize the effort required to implement this interface.
 *
 * @author Haifeng Li
 */
public class AbstractTensor implements Tensor, Externalizable {
    /**
     * A memory segment that stores tensor values.
     */
    private final MemorySegment segment;
    /**
     * The layout that models values of basic data types.
     */
    private final ValueLayout valueLayout;
    /**
     * The shape of tensor. That is a list of the extent of each dimension.
     */
    private final int[] shape;
    /**
     * In row-major order, the stride of a dimension is equal to
     * the product of the sizes of the lower-order dimensions.
     */
    private final long[] stride;

    /**
     * Private default constructor for readExternal.
     */
    private AbstractTensor() {
        segment = null;
        valueLayout = null;
        shape = null;
        stride = null;
    }

    /**
     * Constructor.
     * @param segment the memory segment of data.
     * @param valueLayout the layout that models values of basic data types.
     * @param shape the shape of tensor.
     */
    public AbstractTensor(MemorySegment segment, ValueLayout valueLayout, int[] shape) {
        this.segment = segment;
        this.valueLayout = valueLayout;
        this.shape = shape;
        int dim = shape.length;
        stride = new long[dim];
        stride[dim-1] = 1;
        for (int i = dim - 2; i >= 0; i--) {
            stride[i] = shape[i+1] * stride[i+1];
        }
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(shape);
        out.writeObject(stride);
        out.writeObject(valueLayout);
        out.writeObject(segment.asByteBuffer());
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        shape = (int[]) in.readObject();
        stride = (long[]) in.readObject();
        valueLayout = (ValueLayout) in.readObject();
        ByteBuffer buffer = (ByteBuffer) in.readObject();
        segment = MemorySegment.ofBuffer(buffer);
    }

    @Override
    public ValueLayout valueLayout() {
        return valueLayout;
    }

    @Override
    public ByteBuffer asByteBuffer() {
        return segment.asByteBuffer();
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

    @Override
    public Tensor reshape(int... shape) {
        long p1 = MathEx.product(shape);
        long p2 = MathEx.product(this.shape);
        if (p1 != p2) {
            throw new IllegalArgumentException(String.format("The length of new shape %d != %d", p1, p2));
        }
        return new AbstractTensor(segment, valueLayout, shape);
    }

    /**
     * Returns the offset of cell at the given index.
     * @param index the cell index.
     * @return the offset.
     */
    private long offset(int[] index) {
        long offset = 0;
        for (int i = 0; i < index.length; i++) {
            offset += index[i] * stride[i];
        }
        return offset;
    }

    @Override
    public Tensor set(boolean value, int... index) {
        long offset = offset(index);
        segment.setAtIndex((ValueLayout.OfBoolean) valueLayout, offset, value);
        return this;
    }

    @Override
    public Tensor set(byte value, int... index) {
        long offset = offset(index);
        segment.setAtIndex((ValueLayout.OfByte) valueLayout, offset, value);
        return this;
    }

    @Override
    public Tensor set(short value, int... index) {
        long offset = offset(index);
        segment.setAtIndex((ValueLayout.OfShort) valueLayout, offset, value);
        return this;
    }

    @Override
    public Tensor set(int value, int... index) {
        long offset = offset(index);
        segment.setAtIndex((ValueLayout.OfInt) valueLayout, offset, value);
        return this;
    }

    @Override
    public Tensor set(long value, int... index) {
        long offset = offset(index);
        segment.setAtIndex((ValueLayout.OfLong) valueLayout, offset, value);
        return this;
    }

    @Override
    public Tensor set(float value, int... index) {
        long offset = offset(index);
        segment.setAtIndex((ValueLayout.OfFloat) valueLayout, offset, value);
        return this;
    }

    @Override
    public Tensor set(double value, int... index) {
        long offset = offset(index);
        segment.setAtIndex((ValueLayout.OfDouble) valueLayout, offset, value);
        return this;
    }

    @Override
    public Tensor set(Tensor value, int... index) {
        if (value instanceof AbstractTensor B) {
            if (!valueLayout.equals(B.valueLayout)) {
                throw new UnsupportedOperationException("set with tensor of different ValueLayout: " + B.valueLayout);
            }
            long offset = offset(index) * valueLayout.byteSize();
            MemorySegment.copy(B.segment, 0, segment, offset, B.segment.byteSize());
            return this;
        }
        throw new UnsupportedOperationException("Unsupported Tensor type: " + value.getClass());
    }

    @Override
    public boolean getBoolean(int... index) {
        long offset = offset(index);
        return segment.getAtIndex((ValueLayout.OfBoolean) valueLayout, offset);
    }

    @Override
    public byte getByte(int... index) {
        long offset = offset(index);
        return segment.getAtIndex((ValueLayout.OfByte) valueLayout, offset);
    }

    @Override
    public short getShort(int... index) {
        long offset = offset(index);
        return segment.getAtIndex((ValueLayout.OfShort) valueLayout, offset);
    }

    @Override
    public int getInt(int... index) {
        long offset = offset(index);
        return segment.getAtIndex((ValueLayout.OfInt) valueLayout, offset);
    }

    @Override
    public long getLong(int... index) {
        long offset = offset(index);
        return segment.getAtIndex((ValueLayout.OfLong) valueLayout, offset);
    }

    @Override
    public float getFloat(int... index) {
        long offset = offset(index);
        return segment.getAtIndex((ValueLayout.OfFloat) valueLayout, offset);
    }

    @Override
    public double getDouble(int... index) {
        long offset = offset(index);
        return segment.getAtIndex((ValueLayout.OfDouble) valueLayout, offset);
    }

    @Override
    public Tensor get(int... index) {
        long offset = offset(index) * valueLayout.byteSize();
        return new AbstractTensor(segment.asSlice(offset), valueLayout, Arrays.copyOfRange(shape, index.length, shape.length));
    }
}
