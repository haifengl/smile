/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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
 *******************************************************************************/

package smile.math.distance;

import java.util.BitSet;

/**
 * In information theory, the Hamming distance between two strings of equal
 * length is the number of positions for which the corresponding symbols are
 * different. Put another way, it measures the minimum number of substitutions
 * required to change one into the other, or the number of errors that
 * transformed one string into the other. For a fixed length n, the Hamming
 * distance is a metric on the vector space of the words of that length.
 *
 * @author Haifeng Li
 */
public class HammingDistance implements Distance<BitSet> {

    /** Constructor. */
    public HammingDistance() {

    }

    @Override
    public String toString() {
        return "Hamming Distance";
    }

    @Override
    public double d(BitSet x, BitSet y) {
        if (x.size() != y.size())
            throw new IllegalArgumentException(String.format("BitSets have different length: x[%d], y[%d]", x.size(), y.size()));

        int dist = 0;
        for (int i = 0; i < x.size(); i++) {
            if (x.get(i) != y.get(i))
                dist++;
        }

        return dist;
    }

    /**
     * Returns Hamming distance between the two bytes.
     */
    public static int d(byte x, byte y) {
        return d((int)x, (int)y);
    }
    
    /**
     * Returns Hamming distance between the two shorts.
     */
    public static int d(short x, short y) {
        return d((int)x, (int)y);
    }
    
    /**
     * Returns Hamming distance between the two integers.
     */
    public static int d(int x, int y) {
        int dist = 0;
        int val = x ^ y;

        // Count the number of set bits (Knuth's algorithm)
        while (val != 0) {
            ++dist;
            val &= val - 1;
        }

        return dist;
    }
    
    /**
     * Returns Hamming distance between the two long integers.
     */
    public static int d(long x, long y) {
        int dist = 0;
        long val = x ^ y;

        // Count the number of set bits (Knuth's algorithm)
        while (val != 0) {
            ++dist;
            val &= val - 1;
        }

        return dist;
    }

    /**
     * Returns Hamming distance between the two byte arrays.
     */
    public static int d(byte[] x, byte[] y) {
        if (x.length != y.length)
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));

        int dist = 0;
        for (int i = 0; i < x.length; i++) {
            if (x[i] != y[i])
                dist++;
        }

        return dist;
    }

    /**
     * Returns Hamming distance between the two short arrays.
     */
    public static int d(short[] x, short[] y) {
        if (x.length != y.length)
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));

        int dist = 0;
        for (int i = 0; i < x.length; i++) {
            if (x[i] != y[i])
                dist++;
        }

        return dist;
    }

    /**
     * Returns Hamming distance between the two integer arrays.
     */
    public static int d(int[] x, int[] y) {
        if (x.length != y.length)
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));

        int dist = 0;
        for (int i = 0; i < x.length; i++) {
            if (x[i] != y[i])
                dist++;
        }

        return dist;
    }
}
