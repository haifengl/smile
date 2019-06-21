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

import java.io.Serializable;
import java.text.NumberFormat;

/**
 * Level of measurement or scale of measure is a classification that
 * describes the nature of information within the values assigned to
 * variables. Psychologist Stanley Smith Stevens developed the best-known
 * classification with four levels, or scales, of measurement: nominal,
 * ordinal, interval, and ratio. Each scale of measurement has certain
 * properties which in turn determines the appropriateness for use of
 * certain statistical analyses.
 *
 * @author Haifeng Li
 */
public interface Measure extends Serializable {
    /** Currency. */
    RatioScale Currency = new RatioScale(NumberFormat.getCurrencyInstance());

    /** Percent. */
    RatioScale Percent = new RatioScale(NumberFormat.getPercentInstance());

    /** Returns a measurement value object represented by the argument string s. */
    Number valueOf(String s) throws NumberFormatException;

    /** Returns the string representation of a value of the measure. */
    String toString(Object o);
}
