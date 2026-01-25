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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.util;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Date and time utility functions.
 *
 * @author Haifeng Li
 */
public interface Dates {
    /**
     * Returns the dates in the range.
     * @param from the inclusive start date.
     * @param to   the exclusive end date.
     * @return the dates in the range.
     */
    static LocalDate[] range(LocalDate from, LocalDate to) {
        int days = (int) ChronoUnit.DAYS.between(from, to);
        return range(from, days);
    }

    /**
     * Returns the dates in the range.
     * @param from the inclusive start date.
     * @param days the number of days. If negative, returns the proceeding dates.
     * @return the dates in the range.
     */
    static LocalDate[] range(LocalDate from, int days) {
        int n = Math.abs(days);
        int step = days > 0 ? 1 : -1;
        var dates = new LocalDate[n];
        for (int i = 0; i < n; i++) {
            dates[i] = from.plusDays(i * step);
        }
        return dates;
    }
}
