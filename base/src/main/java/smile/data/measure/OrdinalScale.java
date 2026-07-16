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
package smile.data.measure;

import java.util.Arrays;
import java.util.List;

/**
 * The ordinal type allows for rank order (1st, 2nd, 3rd, etc.) by which
 * data can be sorted, but still does not allow for relative degree of
 * difference between them. The psychological measurement, such as
 * measurement of opinions, usually operates on ordinal scales.
 * For example, 'completely agree', 'mostly agree', 'mostly disagree',
 * 'completely disagree' when measuring opinion.
 * <p>
 * The median, i.e. middle-ranked, item is allowed as the measure of
 * central tendency. But means and standard deviations have no validity.
 * In particular, IQ scores reflect an ordinal scale,
 * in which all scores are meaningful for comparison only. There is no
 * absolute zero, and a 10-point difference may carry different meanings
 * at different points of the scale.
 *
 * @author Haifeng Li
 */
public class OrdinalScale extends CategoricalMeasure {
    /**
     * Constructor.
     * @param levels the levels of ordinal values.
     */
    public OrdinalScale(String... levels) {
        super(levels);
    }

    /**
     * Constructor.
     * @param values the valid values (will be sorted in ascending order,
     *               with levels reordered to match).
     * @param levels the levels of discrete values.
     */
    public OrdinalScale(int[] values, String[] levels) {
        super(sortedValues(values), sortedLevels(values, levels));
    }

    /**
     * Returns values sorted in ascending order.
     */
    private static int[] sortedValues(int[] values) {
        int[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        return sorted;
    }

    /**
     * Returns levels reordered to match the sorted values.
     */
    private static String[] sortedLevels(int[] values, String[] levels) {
        // Build index array that maps sorted position -> original position
        Integer[] idx = new Integer[values.length];
        for (int i = 0; i < values.length; i++) idx[i] = i;
        java.util.Arrays.sort(idx, java.util.Comparator.comparingInt(i -> values[i]));
        String[] sorted = new String[levels.length];
        for (int i = 0; i < idx.length; i++) sorted[i] = levels[idx[i]];
        return sorted;
    }

    /**
     * Constructor.
     * @param levels the levels of discrete values.
     */
    public OrdinalScale(List<String> levels) {
        super(levels);
    }

    /**
     * Constructor.
     *
     * @param clazz an Enum class.
     */
    public OrdinalScale(Class<? extends Enum<?>> clazz) {
        super(values(clazz), levels(clazz));
    }

    @Override
    public String toString() {
        return String.format("ordinal%s", Arrays.toString(levels));
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof OrdinalScale scale) {
            return Arrays.equals(levels, scale.levels) && Arrays.equals(values, scale.values);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(levels) + Arrays.hashCode(values);
    }
}
