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
import smile.data.type.DataType;
import smile.data.type.DataTypes;

/**
 * Discrete data can only take particular values. There may potentially
 * be an infinite number of those values, but each is distinct.
 * The integer encoding of values starts with zero.
 *
 * Both integer and string variables can be made into discrete measure,
 * but a discrete measure's levels will always be string values.
 *
 * @author Haifeng Li
 */
public abstract class DiscreteMeasure implements Measure {
    /**
     * The string values of the discrete scale.
     */
    final String[] levels;
    /**
     * Map a string to an integer level.
     */
    final Map<String, Number> map;

    /**
     * Constructor.
     * @param values the levels of discrete values.
     */
    public DiscreteMeasure(String... values) {
        this.levels = values;
        map = new HashMap<>();
        if (values.length <= Byte.MAX_VALUE + 1) {
            for (byte i = 0; i < values.length; i++) {
                map.put(values[i], i);
            }
        } else if (values.length <= Short.MAX_VALUE + 1) {
            for (short i = 0; i < values.length; i++) {
                map.put(values[i], i);
            }
        } else {
            for (int i = 0; i < values.length; i++) {
                map.put(values[i], i);
            }
        }
    }

    /**
     * Constructor.
     * @param values the levels of discrete values.
     */
    public DiscreteMeasure(List<String> values) {
        this(values.toArray(new String[values.size()]));
    }

    /** Returns the number of levels. */
    public int size() {
        return levels.length;
    }

    /** Returns the levels. */
    public String[] levels() {
        return levels;
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
        return levels[level];
    }

    @Override
    public String toString(Object o) {
        return levels[((Number) o).intValue()];
    }

    @Override
    public Number valueOf(String s) {
        return map.get(s);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof DiscreteMeasure) {
            DiscreteMeasure measure = (DiscreteMeasure) o;
            return Arrays.equals(levels, measure.levels);
        }

        return false;
    }
}
