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
import java.util.List;

/**
 * The ordinal type allows for rank order (1st, 2nd, 3rd, etc.) by which
 * data can be sorted, but still does not allow for relative degree of
 * difference between them. The psychological measurement, such as
 * measurement of opinions, usually operates on ordinal scales.
 * For example, 'completely agree', 'mostly agree', 'mostly disagree',
 * 'completely disagree' when measuring opinion.
 *
 * The median, i.e. middle-ranked, item is allowed as the measure of
 * central tendency. But means and standard deviations have no validity.
 * In particular, IQ scores reflect an ordinal scale,
 * in which all scores are meaningful for comparison only. There is no
 * absolute zero, and a 10-point difference may carry different meanings
 * at different points of the scale.
 *
 * @author Haifeng Li
 */
public class OrdinalScale extends DiscreteMeasure {
    /**
     * Constructor.
     * @param levels the levels of ordinal values.
     */
    public OrdinalScale(String... levels) {
        super(levels);
    }

    /**
     * Constructor.
     * @param values the valid values.
     * @param levels the levels of discrete values.
     */
    public OrdinalScale(int[] values, String[] levels) {
        super(values, levels);
        Arrays.sort(values);
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
    public OrdinalScale(Class<? extends Enum> clazz) {
        super(values(clazz), levels(clazz));
    }

    @Override
    public String toString() {
        return String.format("ordinal%s", Arrays.toString(levels));
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof OrdinalScale) {
            OrdinalScale scale = (OrdinalScale) o;
            return Arrays.equals(levels, scale.levels);
        }

        return false;
    }
}
