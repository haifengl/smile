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

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;
import smile.math.MathEx;

/**
 * An on-heap MemorySegment based implementation of the Tensor interface.
 *
 * @author Haifeng Li
 */
class TensorImpl extends AbstractTensor {
    /**
     * The arena controls the lifecycle of native memory segments.
     */
    private final transient Arena arena;
    /**
     * A memory segment that stores tensor elements.
     */
    private final transient MemorySegment memory;
    /**
     * The data type of tensor elements.
     */
    private final transient ValueLayout valueLayout;

    /**
     * Constructor.
     * @param memory the memory segment of data.
     * @param valueLayout the data type of tensor elements.
     * @param shape the shape of tensor.
     */
    public TensorImpl(Arena arena, MemorySegment memory, ValueLayout valueLayout, int[] shape) {
        super(shape);
        this.arena = arena;
        this.memory = memory;
        this.valueLayout = valueLayout;
    }

    /**
     * Constructor.
     * @param memory the memory segment of data.
     * @param valueLayout the data type of tensor elements.
     * @param shape the shape of tensor.
     */
    public TensorImpl(MemorySegment memory, ValueLayout valueLayout, int[] shape) {
        this(null, memory, valueLayout, shape);
        if (memory.isNative()) {
            throw new UnsupportedOperationException("No arena Tensor with native memory segment");
        }
    }

    @Override
    public void close() {
        if (arena != null) {
            arena.close();
        }
    }

    @Override
    public ValueLayout valueLayout() {
        return valueLayout;
    }

    @Override
    public MemorySegment memory() {
        return memory;
    }

    @Override
    public Tensor reshape(int... shape) {
        long p1 = MathEx.product(shape);
        long p2 = length();
        if (p1 != p2) {
            throw new IllegalArgumentException(String.format("The length of new shape %d != %d", p1, p2));
        }
        return new TensorImpl(memory, valueLayout, shape);
    }

    @Override
    public Tensor set(boolean value, int... index) {
        long offset = offset(index);
        memory.setAtIndex((ValueLayout.OfBoolean) valueLayout, offset, value);
        return this;
    }

    @Override
    public Tensor set(byte value, int... index) {
        long offset = offset(index);
        memory.setAtIndex((ValueLayout.OfByte) valueLayout, offset, value);
        return this;
    }

    @Override
    public Tensor set(short value, int... index) {
        long offset = offset(index);
        memory.setAtIndex((ValueLayout.OfShort) valueLayout, offset, value);
        return this;
    }

    @Override
    public Tensor set(int value, int... index) {
        long offset = offset(index);
        memory.setAtIndex((ValueLayout.OfInt) valueLayout, offset, value);
        return this;
    }

    @Override
    public Tensor set(long value, int... index) {
        long offset = offset(index);
        memory.setAtIndex((ValueLayout.OfLong) valueLayout, offset, value);
        return this;
    }

    @Override
    public Tensor set(float value, int... index) {
        long offset = offset(index);
        memory.setAtIndex((ValueLayout.OfFloat) valueLayout, offset, value);
        return this;
    }

    @Override
    public Tensor set(double value, int... index) {
        long offset = offset(index);
        memory.setAtIndex((ValueLayout.OfDouble) valueLayout, offset, value);
        return this;
    }

    @Override
    public Tensor set(Tensor value, int... index) {
        if (value instanceof TensorImpl B) {
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
    public boolean getBoolean(int... index) {
        long offset = offset(index);
        return memory.getAtIndex((ValueLayout.OfBoolean) valueLayout, offset);
    }

    @Override
    public byte getByte(int... index) {
        long offset = offset(index);
        return memory.getAtIndex((ValueLayout.OfByte) valueLayout, offset);
    }

    @Override
    public short getShort(int... index) {
        long offset = offset(index);
        return memory.getAtIndex((ValueLayout.OfShort) valueLayout, offset);
    }

    @Override
    public int getInt(int... index) {
        long offset = offset(index);
        return memory.getAtIndex((ValueLayout.OfInt) valueLayout, offset);
    }

    @Override
    public long getLong(int... index) {
        long offset = offset(index);
        return memory.getAtIndex((ValueLayout.OfLong) valueLayout, offset);
    }

    @Override
    public float getFloat(int... index) {
        long offset = offset(index);
        return memory.getAtIndex((ValueLayout.OfFloat) valueLayout, offset);
    }

    @Override
    public double getDouble(int... index) {
        long offset = offset(index);
        return memory.getAtIndex((ValueLayout.OfDouble) valueLayout, offset);
    }

    @Override
    public Tensor get(int... index) {
        long offset = offset(index) * valueLayout.byteSize();
        return new TensorImpl(memory.asSlice(offset), valueLayout, Arrays.copyOfRange(shape, index.length, shape.length));
    }
}
