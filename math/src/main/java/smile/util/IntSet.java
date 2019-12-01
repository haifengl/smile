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

package smile.util;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;
import smile.math.MathEx;

/**
 * A set of integers.
 *
 * @author Haifeng Li
 */
public class IntSet implements Serializable {
    private static final long serialVersionUID = 2L;

    /** Map of index to original values. */
    public final int[] values;
    /** Map of values to index. */
    protected final Map<Integer, Integer> index;
    /** The minimum of values. */
    public final int min;
    /** The maximum of values. */
    public final int max;

    /**
     * Constructor.
     * @param values the unique values.
     */
    public IntSet(int[] values) {
        this.values = values;
        this.min = MathEx.min(values);
        this.max = MathEx.max(values);
        this.index = new HashMap<>();
        for (int i = 0; i < values.length; i++) {
            index.put(values[i], i);
        }
    }

    /** Returns the number of values. */
    public int size() {
        return values.length;
    }

    /**
     * Maps an index to the corresponding value.
     *
     * @param index the index.
     * @return the value.
     */
    public int valueOf(int index) {
        return values[index];
    }

    /** Maps the value to index. */
    public int indexOf(int x) {
        return index.get(x);
    }

    /**
     * Returns an IntSet of [0, k).
     *
     * @param k the number of unique values.
     */
    public static IntSet of(int k) {
        int[] values = IntStream.range(0, k).toArray();
        return new IntSet(values);
    }

    /**
     * Finds the unique values from samples.
     */
    public static IntSet of(int[] y) {
        int[] values = MathEx.unique(y);
        Arrays.sort(values);
        return new IntSet(values);
    }
}
