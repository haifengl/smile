/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.data.formula;

/**
 * The date/time features.
 *
 * @author Haifeng Li
 */
public enum DateFeature {
    /**
     * The year represented by an integer.
     */
    YEAR,
    /**
     *
     */
    QUARTER,
    /**
     * The month represented by an integer from 1 to 12;
     * 1 is January, 2 is February, and so forth; thus 12 is December.
     */
    MONTH,
    /**
     * The count of weeks within the year. If the first week starts
     * after the start of the year then the period before is week zero.
     */
    WEEK_OF_YEAR,
    /**
     * The count of weeks within the month. If the first week starts
     * after the start of the month then the period before is week zero.
     */
    WEEK_OF_MONTH,
    /**
     * The day of year represented by an integer from 1 to 365, or 366 in a leap year.
     */
    DAY_OF_YEAR,
    /**
     * The day of month represented by an integer from 1 to 31 in the usual
     * manner.
     */
    DAY_OF_MONTH,
    /**
     * The day of week represented by an integer from 1 to 7;
     * 1 is Monday, 2 is Tuesday, and so forth; thus 7 is Sunday.
     */
    DAY_OF_WEEK,
    /**
     * The hours represented by an integer from 0 to 23. Thus, the hour
     * from midnight to 1 a.m. is hour 0, and the hour from noon to 1 p.m.
     * is hour 12.
     */
    HOUR,
    /**
     * The minutes represented by an integer from 0 to 59
     * in the usual manner.
     */
    MINUTE,
    /**
     * The seconds represented by an integer from 0 to 59
     * in the usual manner.
     */
    SECOND,
}
