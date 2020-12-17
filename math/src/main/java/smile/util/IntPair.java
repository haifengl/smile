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

/** A pair of integer. */
public class IntPair {
    /** The first integer. */
    public final int i;
    /** The second integer. */
    public final int j;

    /**
     * Constructor.
     * @param i the first integer.
     * @param j the second integer.
     */
    public IntPair(int i, int j) {
        this.i = i;
        this.j = j;
    }

    @Override
    public int hashCode() {
        return i * 31 + j;
    }

    @Override
    public String toString() {
        return String.format("(%d, %d)", i, j);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof IntPair) {
            IntPair p = (IntPair) o;
            return i == p.i && j == p.j;
        }

        return false;
    }
}