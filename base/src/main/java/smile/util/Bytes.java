/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Byte string.
 *
 * @param array the array buffer of bytes.
 */
public record Bytes(byte[] array) {
    /**
     * Constructor with a string input.
     * @param s the UTF8 encoding of string will be stored.
     */
    public Bytes(String s) {
        this(s.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Returns the length of byte string.
     * @return the length of byte string.
     */
    public int length() {
        return array.length;
    }

    /**
     * Returns a copy of byte string slice.
     * @param start the initial index of the range to be copied, inclusive
     * @param end the final index of the range to be copied, exclusive.
     * @return a copy of byte string slice.
     */
    public Bytes slice(int start, int end) {
        return new Bytes(Arrays.copyOfRange(array, start, end));
    }

    @Override
    public String toString() {
        return new String(array, StandardCharsets.UTF_8);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(array);
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof Bytes bytes) {
            return Arrays.equals(array, bytes.array);
        }
        return false;
    }
}
