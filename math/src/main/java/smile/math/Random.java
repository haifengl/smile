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

package smile.math;

import smile.math.random.RandomNumberGenerator;
import smile.math.random.UniversalGenerator;

/**
 * This is a high quality random number generator as a replacement of
 * the standard Random class of Java system.
 * 
 * @author Haifeng Li
 */
public class Random {

    private RandomNumberGenerator rng;

    /**
     * Initialize with default random number generator engine.
     */
    public Random() {
        this(new UniversalGenerator());
    }

    /**
     * Initialize with given seed for default random number generator engine.
     */
    public Random(long seed) {
        this(new UniversalGenerator(seed));
    }

    /**
     * Initialize with given random number generator engine.
     */
    public Random(RandomNumberGenerator rng) {
        this.rng = rng;
    }

    /**
     * Generator a random number uniformly distributed in [0, 1).
     * @return a pseudo random number
     */
    public double nextDouble() {
        return rng.nextDouble();
    }

    /**
     * Generate n uniform random numbers in the range [0, 1)
     * @param d array of random numbers to be generated
     */
    public void nextDoubles(double[] d) {
        rng.nextDoubles(d);
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
        rng.nextDoubles(d);

        double l = hi - lo;        
        int n = d.length;
        for (int i = 0; i < n; i++) {
            d[i] = lo + l * d[i];
        }
    }

    /**
     * Initialize the random generator with a seed.
     */
    public void setSeed(long seed) {
        rng.setSeed(seed);
    }

    /**
     * Returns a random integer.
     */
    public int nextInt() {
        return rng.nextInt();
    }
    
    /**
     * Returns a random integer in [0, n).
     */
    public int nextInt(int n) {
        return rng.nextInt(n);
    }

    public long nextLong() {
        return rng.nextLong();
    }

    /**
     * Generates a permutation of 0, 1, 2, ..., n-1, which is useful for
     * sampling without replacement.
     */
    public int[] permutate(int n) {
        int[] x = new int[n];
        for (int i = 0; i < n; i++) {
            x[i] = i;
        }

        permutate(x);

        return x;
    }

    /**
     * Generates a permutation of given array.
     */
    public void permutate(int[] x) {
        for (int i = 0; i < x.length; i++) {
            int j = i + nextInt(x.length - i);
            Math.swap(x, i, j);
        }
    }

    /**
     * Generates a permutation of given array.
     */
    public void permutate(float[] x) {
        for (int i = 0; i < x.length; i++) {
            int j = i + nextInt(x.length - i);
            Math.swap(x, i, j);
        }
    }

    /**
     * Generates a permutation of given array.
     */
    public void permutate(double[] x) {
        for (int i = 0; i < x.length; i++) {
            int j = i + nextInt(x.length - i);
            Math.swap(x, i, j);
        }
    }

    /**
     * Generates a permutation of given array.
     */
    public void permutate(Object[] x) {
        for (int i = 0; i < x.length; i++) {
            int j = i + nextInt(x.length - i);
            Math.swap(x, i, j);
        }
    }
}