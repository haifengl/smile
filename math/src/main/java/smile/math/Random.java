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

package smile.math;

import java.util.stream.IntStream;
import smile.math.random.MersenneTwister;
import smile.math.random.UniversalGenerator;

/**
 * This is a high quality random number generator as a replacement of
 * the standard Random class of Java system.
 * 
 * @author Haifeng Li
 */
public class Random {

    private UniversalGenerator real;
    private MersenneTwister twister;

    /**
     * Initialize with default random number generator engine.
     */
    public Random() {
        real = new UniversalGenerator();
        twister = new MersenneTwister();
    }

    /**
     * Initialize with given seed for default random number generator engine.
     */
    public Random(long seed) {
        real = new UniversalGenerator(seed);
        twister = new MersenneTwister(seed);
    }

    /**
     * Initialize the random generator with a seed.
     */
    public void setSeed(long seed) {
        real.setSeed(seed);
        twister.setSeed(seed);
    }

    /**
     * Generator a random number uniformly distributed in [0, 1).
     * @return a pseudo random number
     */
    public double nextDouble() {
        return real.nextDouble();
    }

    /**
     * Generate n uniform random numbers in the range [0, 1)
     * @param d array of random numbers to be generated
     */
    public void nextDoubles(double[] d) {
        real.nextDoubles(d);
    }

    /**
     * Generate a uniform random number in the range [lo, hi)
     * @param lo lower limit of range
     * @param hi upper limit of range
     * @return a uniform random real in the range [lo, hi)
     */
    public double nextDouble(double lo, double hi) {
        return (lo + (hi - lo) * nextDouble());
    }

    /**
     * Generate n uniform random numbers in the range [lo, hi)
     * @param lo lower limit of range
     * @param hi upper limit of range
     * @param d array of random numbers to be generated
     */
    public void nextDoubles(double[] d, double lo, double hi) {
        real.nextDoubles(d);

        double l = hi - lo;        
        int n = d.length;
        for (int i = 0; i < n; i++) {
            d[i] = lo + l * d[i];
        }
    }

    /**
     * Returns a random integer.
     */
    public int nextInt() {
        return twister.nextInt();
    }
    
    /**
     * Returns a random integer in [0, n).
     */
    public int nextInt(int n) {
        return twister.nextInt(n);
    }

    /**
     * Returns a random long integer.
     */
    public long nextLong() {
        return twister.nextLong();
    }

    /**
     * Generates a permutation of 0, 1, 2, ..., n-1, which is useful for
     * sampling without replacement.
     */
    public int[] permutate(int n) {
        int[] x = IntStream.range(0, n).toArray();
        permutate(x);
        return x;
    }

    /**
     * Generates a permutation of given array.
     */
    public void permutate(int[] x) {
        for (int i = 0; i < x.length; i++) {
            int j = i + nextInt(x.length - i);
            MathEx.swap(x, i, j);
        }
    }

    /**
     * Generates a permutation of given array.
     */
    public void permutate(float[] x) {
        for (int i = 0; i < x.length; i++) {
            int j = i + nextInt(x.length - i);
            MathEx.swap(x, i, j);
        }
    }

    /**
     * Generates a permutation of given array.
     */
    public void permutate(double[] x) {
        for (int i = 0; i < x.length; i++) {
            int j = i + nextInt(x.length - i);
            MathEx.swap(x, i, j);
        }
    }

    /**
     * Generates a permutation of given array.
     */
    public void permutate(Object[] x) {
        for (int i = 0; i < x.length; i++) {
            int j = i + nextInt(x.length - i);
            MathEx.swap(x, i, j);
        }
    }
}