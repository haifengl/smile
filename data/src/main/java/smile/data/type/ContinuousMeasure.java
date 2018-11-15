/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package smile.data.type;

/**
 * Continuous data are numerical data that can theoretically be measured
 * in infinitely small units. The statistical analysis of continuous data
 * is more powerful than that of categorical data.
 *
 * @author Haifeng Li
 */
public enum ContinuousMeasure {
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
     */
    IntervalScale,

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
     */
    RatioScale
}
