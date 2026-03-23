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

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author SMILE Agent
 */
public class DatesTest {
    @Test
    void testRangeFromTo() {
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 1, 5);
        LocalDate[] range = smile.util.Dates.range(start, end);
        assertArrayEquals(new LocalDate[]{
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 1, 2),
            LocalDate.of(2024, 1, 3),
            LocalDate.of(2024, 1, 4)
        }, range);
    }

    @Test
    void testRangeFromDaysPositive() {
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate[] range = smile.util.Dates.range(start, 3);
        assertArrayEquals(new LocalDate[]{
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 1, 2),
            LocalDate.of(2024, 1, 3)
        }, range);
    }

    @Test
    void testRangeFromDaysNegative() {
        LocalDate start = LocalDate.of(2024, 1, 5);
        LocalDate[] range = smile.util.Dates.range(start, -3);
        assertArrayEquals(new LocalDate[]{
            LocalDate.of(2024, 1, 5),
            LocalDate.of(2024, 1, 4),
            LocalDate.of(2024, 1, 3)
        }, range);
    }
}
