/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.data.measure;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import smile.data.type.DataType;
import smile.data.type.DataTypes;

/**
 * Categorical data can be stored into groups or categories with the aid of
 * names or labels. Also known as qualitative data, each element of a
 * categorical data can be placed in only one category according to
 * its qualities, where each of the categories is mutually exclusive.
 * <p>
 * Categorical data may be subdivided into nominal data and ordinal data.
 * <p>
 * Both integer and string variables can be made into categorical measure,
 * but a categorical measure's levels will always be string values.
 *
 * @author Haifeng Li
 */
public abstract class CategoricalMeasure implements Measure {
    /**
     * The valid values.
     */
    final int[] values;
    /**
     * The levels of measurement.
     */
    final String[] levels;
    /**
     * Map an integer to string.
     */
    final Map<Number, String> value2level;
    /**
     * Map a string to an integer level.
     */
    final Map<String, Number> level2value;
    /**
     * The flag if the values are of standard factor [0, 1, 2, ..., k).
     */
    final boolean factor;

    /**
     * Constructor.
     * @param levels the levels of discrete values.
     */
    public CategoricalMeasure(String... levels) {
        this(IntStream.range(0, levels.length).toArray(), levels);
    }

    /**
     * Constructor.
     * @param levels the levels of discrete values.
     */
    public CategoricalMeasure(List<String> levels) {
        this(levels.toArray(new String[0]));
    }

    /**
     * Constructor.
     * @param values the valid values.
     */
    public CategoricalMeasure(int[] values) {
        this(values, Arrays.stream(values).mapToObj(Integer::toString).toArray(String[]::new));
    }

    /**
     * Constructor.
     * @param values the valid values.
     * @param levels the levels of discrete values.
     */
    public CategoricalMeasure(int[] values, String[] levels) {
        if (values.length != levels.length) {
            throw new IllegalArgumentException("The size of values and levels don't match");
        }

        this.values = values;
        this.levels = levels;
        this.value2level = new HashMap<>();
        this.level2value = new HashMap<>();

        if (levels.length <= Byte.MAX_VALUE + 1) {
            for (int i = 0; i < values.length; i++) {
                value2level.put(values[i], levels[i]);
                level2value.put(levels[i], (byte) values[i]);
            }
        } else if (levels.length <= Short.MAX_VALUE + 1) {
            for (int i = 0; i < values.length; i++) {
                value2level.put(values[i], levels[i]);
                level2value.put(levels[i], (short) values[i]);
            }
        } else {
            for (int i = 0; i < values.length; i++) {
                value2level.put(values[i], levels[i]);
                level2value.put(levels[i], values[i]);
            }
        }

        boolean factor = true;
        for (int i = 0; i < values.length; i++) {
            if (values[i] != i) {
                factor = false;
                break;
            }
        }
        this.factor = factor;
    }

    /**
     * Returns the ordinal values of an enum.
     * @param clazz the Class of an enum.
     * @return the ordinal values of an enum.
     */
    static int[] values(Class<? extends Enum> clazz) {
        return Arrays.stream(clazz.getEnumConstants())
                .mapToInt(Enum::ordinal)
                .toArray();
    }

    /**
     * Returns the string values of an enum.
     * @param clazz the Class of an enum.
     * @return the string values of an enum.
     */
    static String[] levels(Class<? extends Enum> clazz) {
        return Arrays.stream(clazz.getEnumConstants())
                .map(Object::toString)
                .toArray(String[]::new);
    }

    /**
     * Returns the number of levels.
     * @return the number of levels.
     */
    public int size() {
        return levels.length;
    }

    /**
     * Returns the valid value set.
     * @return the valid value set.
     */
    public int[] values() {
        return values;
    }

    /**
     * Returns the levels.
     * @return the levels.
     */
    public String[] levels() {
        return levels;
    }

    /**
     * Returns the level string representation.
     * @param value the level value.
     * @return the level string representation.
     */
    public String level(int value) {
        return value2level.get(value);
    }

    /**
     * Returns the factor value (in range [0, size)) of level.
     * @param value the level value.
     * @return the factor value.
     */
    public int factor(int value) {
        if (factor) return value;

        for (int j = 0; j < values.length; j++) {
            if (values[j] == value) return j;
        }

        throw new IllegalArgumentException("Invalid level: " + value);
    }

    /**
     * Returns the data type that is suitable for this measure scale.
     * @return the data type that is suitable for this measure scale.
     */
    public DataType type() {
        if (levels.length <= Byte.MAX_VALUE + 1) {
            return DataTypes.ByteType;
        } else if (levels.length <= Short.MAX_VALUE + 1) {
            return DataTypes.ShortType;
        } else {
            return DataTypes.IntegerType;
        }
    }

    /**
     * Returns the string value of a level.
     * @param value the level value.
     * @return the string value of a level.
     */
    public String toString(int value) {
        return level(value);
    }

    @Override
    public String toString(Object o) {
        return level(((Number) o).intValue());
    }

    @Override
    public Number valueOf(String s) {
        return level2value.get(s);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof CategoricalMeasure) {
            CategoricalMeasure measure = (CategoricalMeasure) o;
            return Arrays.equals(levels, measure.levels) && Arrays.equals(values, measure.values);
        }

        return false;
    }
}
