/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
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
package smile.data.vector;

import java.util.Collections;
import java.util.List;
import smile.data.measure.CategoricalMeasure;
import smile.data.measure.NominalScale;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.util.Index;

/**
 * An immutable string vector.
 *
 * @author Haifeng Li
 */
public class StringVector extends ObjectVector<String> {

    /** Constructor. */
    public StringVector(String name, String[] vector) {
        super(name, vector);
    }

    /** Constructor. */
    public StringVector(StructField field, String[] vector) {
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
            case Integer: {
                int[] data = new int[size()];
                for (int i = 0; i < data.length; i++) {
                    String s = get(i);
                    data[i] = s == null ? -1 : scale.valueOf(s).intValue();
                }

                StructField field = new StructField(name(), DataTypes.IntegerType, scale);
                return new IntVector(field, data);
            }
            default:
                // we should never reach here.
                throw new UnsupportedOperationException("Unsupported data type for nominal measure: " + scale.type());
        }
    }

    @Override
    public StringVector get(Index index) {
        StringVector copy = new StringVector(field, vector);
        return slice(copy, index);
    }

    @Override
    public boolean getBoolean(int i) {
        return Boolean.parseBoolean(get(i));
    }

    @Override
    public char getChar(int i) {
        String s = get(i);
        return s.isEmpty() ? '\u0000' : s.charAt(0);
    }

    @Override
    public byte getByte(int i) {
        return Byte.parseByte(get(i));
    }

    @Override
    public short getShort(int i) {
        return Short.parseShort(get(i));
    }

    @Override
    public int getInt(int i) {
        return Integer.parseInt(get(i));
    }

    @Override
    public long getLong(int i) {
        return Long.parseLong(get(i));
    }

    @Override
    public float getFloat(int i) {
        return Float.parseFloat(get(i));
    }

    @Override
    public double getDouble(int i) {
        return Double.parseDouble(get(i));
    }
}