/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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
 *******************************************************************************/

package smile.data.measure;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import smile.data.type.DataType;
import smile.data.type.DataTypes;

/**
 * Discrete data can only take particular values. There may potentially
 * be an infinite number of those values, but each is distinct.
 *
 * Both integer and string variables can be made into discrete measure,
 * but a discrete measure's levels will always be string values.
 *
 * @author Haifeng Li
 */
public abstract class DiscreteMeasure implements Measure {
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
     * Constructor.
     * @param levels the levels of discrete values.
     */
    public DiscreteMeasure(String... levels) {
        this(IntStream.range(0, levels.length).toArray(), levels);
    }

    /**
     * Constructor.
     * @param levels the levels of discrete values.
     */
    public DiscreteMeasure(List<String> levels) {
        this(levels.toArray(new String[levels.size()]));
    }

    /**
     * Constructor.
     * @param values the valid values.
     * @param levels the levels of discrete values.
     */
    public DiscreteMeasure(int[] values, String[] levels) {
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
    }

    /** Returns the ordinal values of an enum. */
    static int[] values(Class<? extends Enum> clazz) {
        return Arrays.stream(clazz.getEnumConstants())
                .mapToInt(e -> ((Enum) e).ordinal())
                .toArray();
    }

    /** Returns the string values of an enum. */
    static String[] levels(Class<? extends Enum> clazz) {
        return Arrays.stream(clazz.getEnumConstants())
                .map(Object::toString)
                .toArray(String[]::new);
    }

    /** Returns the number of levels. */
    public int size() {
        return levels.length;
    }

    /** Returns the valid value set. */
    public int[] values() {
        return values;
    }

    /** Returns the levels. */
    public String[] levels() {
        return levels;
    }

    /** Returns the level string representation. */
    public String level(int i) {
        return value2level.get(i);
    }

    /** Returns the data type that is suitable for this measure scale. */
    public DataType type() {
        if (levels.length <= Byte.MAX_VALUE + 1) {
            return DataTypes.ByteType;
        } else if (levels.length <= Short.MAX_VALUE + 1) {
            return DataTypes.ShortType;
        } else {
            return DataTypes.IntegerType;
        }
    }

    /** Returns the string value of a level. */
    public String toString(int level) {
        return level(level);
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
        if (o instanceof DiscreteMeasure) {
            DiscreteMeasure measure = (DiscreteMeasure) o;
            return Arrays.equals(levels, measure.levels) && Arrays.equals(values, values);
        }

        return false;
    }
}
