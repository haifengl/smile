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

import java.util.Arrays;
import static smile.tensor.ScalarType.*;

/**
 * A scalar is a single number.
 * @param value the scalar value.
 *
 * @author Haifeng Li
 */
public record Scalar(Number value) implements Tensor {
    /** Static shape. */
    private static final int[] shape = { 1 };

    @Override
    public ScalarType scalarType() {
        return switch (value) {
            case Byte b -> Int8;
            case Short b -> Int16;
            case Integer b -> Int32;
            case Long b -> Int64;
            case Float b -> Int32;
            case Double b -> Int64;
            default -> throw new RuntimeException("Unsupported Scalar value type: " + value.getClass());
        };
    }

    @Override
    public int dim() {
        return 0;
    }

    @Override
    public int size(int dim) {
        return 1;
    }

    @Override
    public long length() {
        return 1;
    }

    @Override
    public int[] shape() {
        return shape;
    }

    @Override
    public Tensor reshape(int... shape) {
        if (shape.length > 1 || shape[0] != 1) {
            throw new UnsupportedOperationException("Cannot reshape a scalar to higher rank: " + Arrays.toString(shape));
        }
        return this;
    }

    @Override
    public Tensor set(Tensor value, int... index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Tensor get(int... index) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the boolean value of scalar.
     *
     * @return the boolean value.
     */
    public boolean toBoolean() {
        return value.intValue() != 0;
    }

    /**
     * Returns the byte value of scalar.
     *
     * @return the scalar value.
     */
    public byte toByte() {
        return value.byteValue();
    }

    /**
     * Returns the short value of scalar.
     *
     * @return the scalar value.
     */
    public short toShort() {
        return value.shortValue();
    }

    /**
     * Returns the int value of scalar.
     *
     * @return the scalar value.
     */
    public int toInt() {
        return value.intValue();
    }

    /**
     * Returns the long value of scalar.
     *
     * @return the scalar value.
     */
    public long toLong() {
        return value.longValue();
    }

    /**
     * Returns the float value of scalar.
     *
     * @return the scalar value.
     */
    public float toFloat() {
        return value.floatValue();
    }

    /**
     * Returns the double value of scalar.
     *
     * @return the scalar value.
     */
    public double toDouble() {
        return value.doubleValue();
    }
}
