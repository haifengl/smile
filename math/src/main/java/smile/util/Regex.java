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

package smile.util;

import java.util.regex.Pattern;

/**
 * Regular expression patterns.
 *
 * @author Haifeng Li
 */
public interface Regex {
    /** Integer regular expression. */
    String INTEGER_REGEX = "[-+]?\\d{1,9}";
    /** Double regular expression. */
    String DOUBLE_REGEX = "[-+]?[0-9]*\\.?[0-9]+(?:[eE][-+]?[0-9]+)?";
    /** Boolean regular expression. */
    String BOOLEAN_REGEX = "(true|false)";

    /** Boolean regular expression pattern. */
    Pattern BOOLEAN = Pattern.compile(BOOLEAN_REGEX, Pattern.CASE_INSENSITIVE);
    /** Integer regular expression pattern. */
    Pattern INTEGER = Pattern.compile(INTEGER_REGEX);
    /** Long regular expression pattern. */
    Pattern LONG = Pattern.compile("[-+]?\\d{1,19}");
    /** Double regular expression pattern. */
    Pattern DOUBLE = Pattern.compile(DOUBLE_REGEX);
    /** Date regular expression pattern. */
    Pattern DATE = Pattern.compile("\\d{4}(-|\\/)((0[1-9])|(1[0-2]))(-|\\/)((0[1-9])|([1-2][0-9])|(3[0-1]))");
    /** Time regular expression pattern. */
    Pattern TIME = Pattern.compile("(([0-1][0-9])|(2[0-3])):([0-5][0-9])(:([0-5][0-9]))?");
    /** Datetime regular expression pattern. */
    Pattern DATETIME = Pattern.compile("\\d{4}(-|\\/)((0[1-9])|(1[0-2]))(-|\\/)((0[1-9])|([1-2][0-9])|(3[0-1]))(T|\\s)(([0-1][0-9])|(2[0-3])):([0-5][0-9]):([0-5][0-9])");

    /** Cardinal numbers. */
    Pattern CARDINAL_NUMBER = Pattern.compile("^-?[0-9]+(\\.[0-9]+)?$");
    /** Cardinal numbers, optionally thousands are separated by comma. */
    Pattern CARDINAL_NUMBER_WITH_COMMA = Pattern.compile("^\\d{1,3},(\\d{3},)*\\d{3}(\\.\\d+)?$");
    /** U.S. phone number. */
    Pattern PHONE_NUMBER = Pattern.compile("^(((\\d{3})|(\\d{3}-)){0,1}\\d{3}-\\d{4})((x|ext)\\d{1,5}){0,1}$");
    /** U.S. phone number extension. */
    Pattern PHONE_NUMBER_EXTENSION = Pattern.compile("^(x|ext)\\d{1,5}$");
    /** Internet URLs. */
    Pattern URL = Pattern.compile("^((mailto\\:|(news|(ht|f)tp(s?))\\://){1}\\S+)$");
    /** Email address. */
    Pattern EMAIL_ADDRESS = Pattern.compile("^(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|\"(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21\\x23-\\x5b\\x5d-\\x7f]|\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])*\")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\\[(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21-\\x5a\\x53-\\x7f]|\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])+)\\])$");
}
