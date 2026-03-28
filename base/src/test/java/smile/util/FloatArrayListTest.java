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
public class FloatArrayListTest {

    @Nested
    @DisplayName("when manipulating the list")
    class Manipulation {

        @Test
        @DisplayName("should add and get elements")
        void shouldAddAndGetElements() {
            FloatArrayList list = new FloatArrayList();
            list.add(1.5f);
            list.add(2.5f);
            assertEquals(2, list.size());
            assertEquals(1.5f, list.get(0));
            assertEquals(2.5f, list.get(1));
        }

        @Test
        @DisplayName("should remove elements")
        void shouldRemoveElements() {
            FloatArrayList list = new FloatArrayList();
            list.add(1.5f);
            list.add(2.5f);
            float removed = list.remove(0);
            assertEquals(1.5f, removed);
            assertEquals(1, list.size());
            assertEquals(2.5f, list.get(0));
        }

        @Test
        @DisplayName("should clear the list")
        void shouldClearList() {
            FloatArrayList list = new FloatArrayList();
            list.add(1.5f);
            list.add(2.5f);
            list.clear();
            assertEquals(0, list.size());
        }
    }
}
