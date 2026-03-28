/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author SMILE Agent
 */
class IntDoubleHashMapTest {

    @Nested
    @DisplayName("when manipulating the map")
    class Manipulation {

        @Test
        @DisplayName("should put and get values")
        void shouldPutAndGetValues() {
            IntDoubleHashMap map = new IntDoubleHashMap();
            map.put(1, 2.5);
            map.put(2, 3.5);
            assertEquals(2.5, map.get(1));
            assertEquals(3.5, map.get(2));
        }

        @Test
        @DisplayName("should return default for missing key")
        void shouldReturnDefaultForMissingKey() {
            IntDoubleHashMap map = new IntDoubleHashMap();
            assertEquals(Double.NaN, map.get(42)); // default value
        }

        @Test
        @DisplayName("should remove values")
        void shouldRemoveValues() {
            IntDoubleHashMap map = new IntDoubleHashMap();
            map.put(1, 2.5);
            double removed = map.remove(1);
            assertEquals(2.5, removed);
            assertEquals(Double.NaN, map.get(1));
        }
    }
}
