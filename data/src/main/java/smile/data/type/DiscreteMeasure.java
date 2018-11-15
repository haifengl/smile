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

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;

/**
 * Discrete data can only take particular values. There may potentially
 * be an infinite number of those values, but each is distinct.
 * The integer encoding of values starts with zero.
 *
 * Both integer and string variables can be made into discrete measure,
 * but a discrete measure's levels will always be string values.
 *
 * @author Haifeng Li
 */
public abstract class DiscreteMeasure implements Serializable {
    /**
     * The string values of the discrete scale.
     */
    String[] levels;
    /**
     * Map a string to an integer level.
     */
    HashMap<String, Integer> map = new HashMap<>();

    /**
     * Constructor.
     * @param values the levels of discrete values.
     */
    public DiscreteMeasure(String... values) {
        this.levels = values;
        for (int i = 0; i < values.length; i++) {
            map.put(values[i], i);
        }
    }

    /** Returns the string value of a level. */
    public String toString(int level) {
        return levels[level];
    }

    /** Returns the level of a string value. */
    public int valueOf(String s) {
        return map.get(s);
    }

    /** Returns the number of levels. */
    public int size() {
        return levels.length;
    }

    /** Returns the levels. */
    public String[] levels() {
        return levels;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof DiscreteMeasure) {
            DiscreteMeasure measure = (DiscreteMeasure) o;

            if (levels.length == measure.levels.length) {
                for (int i = 0; i < levels.length; i++) {
                    if (!levels[i].equals(measure.levels[i]))
                        return false;
                }
                return true;
            }
        }

        return false;
    }
}
