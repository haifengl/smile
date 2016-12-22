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

package smile.math.distance;

import java.io.Serializable;
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
public class HammingDistance<T> implements Metric<T[]>, Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Constructor.
     */
    private HammingDistance() {
    }

    @Override
    public String toString() {
        return "Hamming distance";
    }

    /**
     * Returns Hamming distance between the two arrays.
     */
    @Override
    public double d(T[] x, T[] y) {
        if (x.length != y.length)
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));

        int dist = 0;
        for (int i = 0; i < x.length; i++) {
            if (!x[i].equals(y[i]))
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

    /**
     * Returns Hamming distance between the two BitSets.
     */
    public static int d(BitSet x, BitSet y) {
        if (x.size() != y.size())
            throw new IllegalArgumentException(String.format("BitSets have different length: x[%d], y[%d]", x.size(), y.size()));

        int dist = 0;
        for (int i = 0; i < x.size(); i++) {
            if (x.get(i) != y.get(i))
                dist++;
        }

        return dist;
    }
}
