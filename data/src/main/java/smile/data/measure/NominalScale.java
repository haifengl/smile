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
     * @param values the levels of nominal values.
     */
    public NominalScale(String... values) {
        super(values);
    }

    /**
     * Constructor.
     *
     * @param clazz an Enum class.
     */
    public NominalScale(Class<Enum<?>> clazz) {
        super(Arrays.stream(clazz.getEnumConstants())
                .map(Object::toString)
                .toArray(String[]::new)
        );
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
