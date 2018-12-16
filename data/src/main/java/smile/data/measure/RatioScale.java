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
package smile.data.measure;

import java.text.NumberFormat;

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
}
