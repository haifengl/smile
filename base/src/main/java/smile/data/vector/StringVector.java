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
package smile.data.vector;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import smile.data.measure.CategoricalMeasure;
import smile.data.measure.NominalScale;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.util.Index;

/**
 * A string vector.
 *
 * @author Haifeng Li
 */
public class StringVector extends ObjectVector<String> {
    /**
     * Constructor.
     * @param name the name of vector.
     * @param vector the elements of vector.
     */
    public StringVector(String name, String[] vector) {
        this(new StructField(name, DataTypes.StringType), vector);
    }

    /**
     * Constructor.
     * @param field the struct field of vector.
     * @param vector the elements of vector.
     */
    public StringVector(StructField field, String[] vector) {
        if (field.dtype() != DataTypes.StringType) {
            throw new IllegalArgumentException("Invalid data type: " + field);
        }
        super(field, vector);
    }

    /**
     * Returns a nominal scale of measure based on distinct values in
     * the vector.
     * @return the nominal scale.
     */
    public NominalScale nominal() {
        List<String> levels = distinct();
        Collections.sort(levels);
        return new NominalScale(levels);
    }

    /**
     * Converts strings to discrete measured values. Depending on how many levels
     * in the nominal scale, the type of returned vector may be byte, short
     * or integer. The missing values/nulls will be converted to -1.
     * @param scale the categorical measure.
     * @return the factorized vector.
     */
    public ValueVector factorize(CategoricalMeasure scale) {
        switch (scale.type().id()) {
            case Byte: {
                byte[] data = new byte[size()];
                for (int i = 0; i < data.length; i++) {
                    String s = get(i);
                    data[i] = s == null ? (byte) -1 : scale.valueOf(s).byteValue();
                }

                StructField field = new StructField(name(), DataTypes.ByteType, scale);
                return new ByteVector(field, data);
            }
            case Short: {
                short[] data = new short[size()];
                for (int i = 0; i < data.length; i++) {
                    String s = get(i);
                    data[i] = s == null ? (short) -1 : scale.valueOf(s).shortValue();
                }

                StructField field = new StructField(name(), DataTypes.ShortType, scale);
                return new ShortVector(field, data);
            }
            case Int: {
                int[] data = new int[size()];
                for (int i = 0; i < data.length; i++) {
                    String s = get(i);
                    data[i] = s == null ? -1 : scale.valueOf(s).intValue();
                }

                StructField field = new StructField(name(), DataTypes.IntType, scale);
                return new IntVector(field, data);
            }
            default:
                // we should never reach here.
                throw new UnsupportedOperationException("Unsupported data type for nominal measure: " + scale.type());
        }
    }

    @Override
    public StringVector get(Index index) {
        String[] data = new String[index.size()];
        for (int i = 0; i < data.length; i++) {
            data[i] = vector[index.apply(i)];
        }
        return new StringVector(field, data);
    }

    @Override
    public StringVector withName(String name) {
        return new StringVector(field.withName(name), vector);
    }

    @Override
    public IntStream intStream() {
        return Arrays.stream(vector).filter(Objects::nonNull).mapToInt(Integer::parseInt);
    }

    @Override
    public LongStream longStream() {
        return Arrays.stream(vector).filter(Objects::nonNull).mapToLong(Long::parseLong);
    }

    @Override
    public DoubleStream doubleStream() {
        return Arrays.stream(vector).filter(Objects::nonNull).mapToDouble(Double::parseDouble);
    }

    @Override
    public boolean getBoolean(int i) {
        return Boolean.parseBoolean(vector[i]);
    }

    @Override
    public char getChar(int i) {
        String s = vector[i];
        return s.isEmpty() ? '\u0000' : s.charAt(0);
    }

    @Override
    public byte getByte(int i) {
        return Byte.parseByte(vector[i]);
    }

    @Override
    public short getShort(int i) {
        return Short.parseShort(vector[i]);
    }

    @Override
    public int getInt(int i) {
        return Integer.parseInt(vector[i]);
    }

    @Override
    public long getLong(int i) {
        return Long.parseLong(vector[i]);
    }

    @Override
    public float getFloat(int i) {
        return Float.parseFloat(vector[i]);
    }

    @Override
    public double getDouble(int i) {
        return Double.parseDouble(vector[i]);
    }
}
