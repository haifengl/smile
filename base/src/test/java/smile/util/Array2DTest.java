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
public class Array2DTest {

    @Nested
    @DisplayName("when creating and manipulating 2D arrays")
    class CreationAndManipulation {

        @Test
        @DisplayName("should create a 2D array with correct dimensions")
        void shouldCreate2DArrayWithCorrectDimensions() {
            var arr = new Array2D(3, 4);
            assertEquals(3, arr.nrow());
            assertEquals(4, arr.ncol());
        }

        @Test
        @DisplayName("should fill 2D array with a value")
        void shouldFill2DArrayWithValue() {
            var arr = new Array2D(2, 2);
            arr.fill(7.5);
            for (int i = 0; i < arr.nrow(); i++) {
                for (int j = 0; j < arr.ncol(); j++) {
                    assertEquals(7.5, arr.get(i, j));
                }
            }
        }
    }
}
