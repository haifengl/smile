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
package smile.data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import smile.util.Index;

/**
 * DataFrame row index. A data frame may have a vector of row labels which has
 * same length as the number of rows in the data frame, and contains neither
 * missing nor duplicated values.
 *
 * @param values the row index values.
 * @param loc the map of row index value to ordinal index.
 * @author Karl Li
 */
public record RowIndex(Object[] values, Map<Object, Integer> loc) implements Serializable {
    /**
     * Constructor.
     * @param values the row values.
     */
    public RowIndex(Object[] values) {
        this(values, new HashMap<>(values.length * 4 / 3));
        for (int i = 0; i < values.length; i++) {
            var value = values[i];
            if (value == null) {
                throw new IllegalArgumentException("Null index value: " + i);
            }
            if (loc.putIfAbsent(value, i) != null) {
                throw new IllegalArgumentException("Duplicate index value: " + value);
            }
        }
    }

    /**
     * Returns the number of elements in the index.
     * @return the number of elements in the index.
     */
    public int size() {
        return values.length;
    }

    /**
     * Returns the row index.
     * @param label the row label.
     * @return the row index.
     */
    public int apply(Object label) {
        return loc.get(label);
    }

    /**
     * Returns a slice of the index.
     * @param index the index to selected rows.
     * @return a slice of the index.
     */
    public RowIndex get(Index index) {
        return new RowIndex(index.stream().mapToObj(i -> values[i]).toArray());
    }
}
