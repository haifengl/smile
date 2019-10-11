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

import java.text.NumberFormat;
import java.util.Arrays;

/**
 * The ratio scale allows for both difference and ratio of two values.
 * Examples include mass, length, duration, plane angle, energy and
 * electric charge. The ratio type takes its name from the fact that
 * measurement is the estimation of the ratio between a magnitude of
 * a continuous quantity and a unit magnitude of the same kind.
 * A ratio scale possesses a meaningful (unique and non-arbitrary)
 * zero value. In comparison with Celsius scale, the Kelvin
 * temperature scale is a ratio scale because it has a unique,
 * non-arbitrary zero point called absolute zero.
 * Most measurement in the physical sciences and engineering
 * is done on ratio scales.
 *
 * The geometric mean and the harmonic mean are allowed to measure
 * the central tendency of ratio variables, in addition to the mode,
 * median, and arithmetic mean. In fact, all statistical measures are
 * allowed because all necessary mathematical operations are defined
 * for the ratio scale.
 *
 * @author Haifeng Li
 */
public class RatioScale extends ContinuousMeasure {

    /** Constructor. */
    public RatioScale(NumberFormat format) {
        super(format);
    }

    @Override
    public String toString() {
        return "ratio";
    }
}
