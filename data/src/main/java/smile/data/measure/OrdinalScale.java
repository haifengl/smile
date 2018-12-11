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

import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.Collectors;

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
     * @param values the levels of ordinal values.
     */
    public OrdinalScale(String... values) {
        super(values);
    }

    /**
     * Constructor.
     *
     * @param clazz an Enum class.
     */
    public OrdinalScale(Class<Enum<?>> clazz) {
        super(Arrays.stream(clazz.getEnumConstants())
                .map(Object::toString)
                .toArray(String[]::new)
        );
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
