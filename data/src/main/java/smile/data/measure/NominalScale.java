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
 * Nominal variables take on a limited number of unordered values.
 * Such variables are often referred to as categorical variables.
 * Examples include gender, nationality, ethnicity, language,
 * genre, style, biological species, etc.
 *
 * The nominal type differentiates between items or subjects based
 * only on their names or (meta-)categories and other qualitative
 * classifications they belong to. Numbers may be used to represent
 * the variables but the numbers have no specific numerical value
 * or meaning. No form of arithmetic computation (+, −, ×, etc.)
 * may be performed on nominal measures.
 *
 * @author Haifeng Li
 */
public class NominalScale extends DiscreteMeasure {
    /**
     * Constructor.
     * @param levels the levels of nominal values.
     */
    public NominalScale(String... levels) {
        super(levels);
    }

    /**
     * Constructor.
     * @param values the valid values.
     * @param levels the levels of discrete values.
     */
    public NominalScale(int[] values, String[] levels) {
        super(values, levels);
    }

    /**
     * Constructor.
     * @param levels the levels of discrete values.
     */
    public NominalScale(List<String> levels) {
        super(levels);
    }

    /**
     * Constructor.
     *
     * @param clazz an Enum class.
     */
    public NominalScale(Class<? extends Enum> clazz) {
        super(values(clazz), levels(clazz));
    }

    @Override
    public String toString() {
        return String.format("nominal%s", Arrays.toString(levels));
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof NominalScale) {
            NominalScale scale = (NominalScale) o;
            return Arrays.equals(levels, scale.levels);
        }

        return false;
    }
}
