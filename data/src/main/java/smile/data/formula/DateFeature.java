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
package smile.data.formula;

/**
 * The date/time features.
 */
public enum DateFeature {
    /**
     * The year represented by an integer.
     */
    YEAR,
    /**
     * The month represented by an integer from 1 to 12;
     * 1 is January, 2 is February, and so forth; thus 12 is December.
     */
    MONTH,
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
    HOURS,
    /**
     * The minutes represented by an integer from 0 to 59
     * in the usual manner.
     */
    MINUTES,
    /**
     * The seconds represented by an integer from 0 to 59
     * in the usual manner.
     */
    SECONDS,
}
