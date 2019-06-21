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

package smile.hash;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Perfect hash based immutable map. This can be used as a lookup table with
 * constant worst-case access time.
 */
public class PerfectMap<T> {
    /** Perfect hash of keywords. */
    private PerfectHash hash;
    /** The value set. */
    private List<T> values;

    /** Builder of perfect map. */
    public static class Builder<T> {
        /** Key-value map. */
        private Map<String, T> map = new HashMap<>();

        /** Constructor. */
        public Builder() {

        }

        /** Constructor. */
        public Builder(Map<String, T> map) {
            this.map.putAll(map);
        }

        /** Add a new key-value pair. */
        public Builder add(String key, T value) {
            map.put(key, value);
            return this;
        }

        /** Builds the perfect map. */
        public PerfectMap<T> build() {
            String[] keys = new String[map.size()];
            List<T> values = new ArrayList<>();
            int i = 0;
            for (Map.Entry<String, T> e : map.entrySet()) {
                keys[i++] = e.getKey();
                values.add(e.getValue());
            }
            return new PerfectMap<>(new PerfectHash(keys), values);
        }
    }

    /**
     * Private constructor. The user should use the Builder
     * to create the immutable map.
     */
    private PerfectMap(PerfectHash hash, List<T> values) {
        this.hash = hash;
        this.values = values;
    }

    /**
     * Returns the value associated with the key. Returns null if
     * the key doesn't exist in the map.
     */
    public T get(String key) {
        int i = hash.get(key);
        if (i < 0) return null;
        return values.get(i);
    }
}
