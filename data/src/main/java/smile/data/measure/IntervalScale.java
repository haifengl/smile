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

/**
 * The interval scale allows for the degree of difference between items,
 * but not the ratio between them. Examples include temperature with
 * the Celsius scale, which has two defined points (the freezing and
 * boiling point of water at specific conditions) and then separated
 * into 100 intervals. Ratios are not meaningful since 20 °C
 * cannot be said to be "twice as hot" as 10 °C. Other examples include
 * date when measured from an arbitrary epoch (such as AD) since
 * multiplication/division cannot be carried out between any two dates
 * directly. However, ratios of differences can be expressed; for example,
 * one difference can be twice another.
 *
 * The mode, median, and arithmetic mean are allowed to measure central
 * tendency of interval variables, while measures of statistical dispersion
 * include range and standard deviation. Since one can only divide by
 * differences, one cannot define measures that require some ratios,
 * such as the coefficient of variation. More subtly, while one can define
 * moments about the origin, only central moments are meaningful, since
 * the choice of origin is arbitrary. One can define standardized moments,
 * since ratios of differences are meaningful, but one cannot define the
 * coefficient of variation, since the mean is a moment about the origin,
 * unlike the standard deviation, which is (the square root of) a central
 * moment.
 *
 * @author Haifeng Li
 */
public class IntervalScale extends ContinuousMeasure {

    /** Constructor. */
    public IntervalScale(NumberFormat format) {
        super(format);
    }

    @Override
    public String toString() {
        return "interval";
    }
}
