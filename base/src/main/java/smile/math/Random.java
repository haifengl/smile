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
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.math;

import java.util.stream.IntStream;
import smile.math.random.MersenneTwister;
import smile.math.random.UniversalGenerator;

/**
 * A high-quality random number generator that combines two complementary
 * generators:
 * <ul>
 * <li>A {@link UniversalGenerator} (Marsaglia–Zaman–Tsang) for uniform
 *     floating-point values. It has a period of 2<sup>144</sup> and passes
 *     all standard statistical tests.</li>
 * <li>A {@link MersenneTwister} (MT19937) for integer values. It has a period
 *     of 2<sup>19937</sup>−1 and excellent equidistribution properties.</li>
 * </ul>
 * Both generators are seeded together by {@link #setSeed(long)} so that the
 * combined stream is reproducible from a single seed.
 *
 * @author Haifeng Li
 */
public class Random {

    private final UniversalGenerator real;
    private final MersenneTwister twister;
    /** Cached spare Gaussian value from Box-Muller transform. */
    private double spareGaussian;
    /** True if a cached Gaussian value is available. */
    private boolean hasSpareGaussian = false;

    /**
     * Initialize with default random number generator engine.
     */
    public Random() {
        real = new UniversalGenerator();
        twister = new MersenneTwister();
    }

    /**
     * Initialize with given seed for default random number generator engine.
     * @param seed the RNG seed.
     */
    public Random(long seed) {
        real = new UniversalGenerator(seed);
        twister = new MersenneTwister(seed);
    }

    /**
     * Initialize the random generator with a seed.
     * @param seed the RNG seed.
     */
    public void setSeed(long seed) {
        real.setSeed(seed);
        twister.setSeed(seed);
        hasSpareGaussian = false;
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
     * Generate a uniform random number in the range [lo, hi).
     * @param lo lower limit of range (inclusive).
     * @param hi upper limit of range (exclusive).
     * @return a uniform random real in the range [lo, hi).
     * @throws IllegalArgumentException if {@code lo >= hi}.
     */
    public double nextDouble(double lo, double hi) {
        if (lo >= hi) {
            throw new IllegalArgumentException(
                String.format("lo (%s) must be less than hi (%s)", lo, hi));
        }
        return lo + (hi - lo) * nextDouble();
    }

    /**
     * Generate n uniform random numbers in the range [lo, hi).
     * @param d     array to fill with random numbers.
     * @param lo    lower limit of range (inclusive).
     * @param hi    upper limit of range (exclusive).
     * @throws IllegalArgumentException if {@code lo >= hi}.
     */
    public void nextDoubles(double[] d, double lo, double hi) {
        if (lo >= hi) {
            throw new IllegalArgumentException(
                String.format("lo (%s) must be less than hi (%s)", lo, hi));
        }
        real.nextDoubles(d);
        double range = hi - lo;
        for (int i = 0; i < d.length; i++) {
            d[i] = lo + range * d[i];
        }
    }

    /**
     * Returns a random integer.
     * @return a random integer.
     */
    public int nextInt() {
        return twister.nextInt();
    }

    /**
     * Returns a random integer in [0, n).
     * @param n the upper bound of random number (exclusive); must be positive.
     * @return a random integer in [0, n).
     */
    public int nextInt(int n) {
        return twister.nextInt(n);
    }

    /**
     * Returns a random long integer.
     * @return a random long integer.
     */
    public long nextLong() {
        return twister.nextLong();
    }

    /**
     * Returns a random boolean value.
     * @return {@code true} or {@code false} with equal probability.
     */
    public boolean nextBoolean() {
        return twister.nextInt() < 0;  // sign bit of a uniform int is unbiased
    }

    /**
     * Returns a random float uniformly distributed in [0, 1).
     * @return a random float.
     */
    public float nextFloat() {
        return (twister.nextInt() >>> 8) / ((float) (1 << 24));
    }

    /**
     * Returns a permutation of <code>(0, 1, 2, ..., n-1)</code>.
     *
     * @param n the upper bound.
     * @return the permutation of <code>(0, 1, 2, ..., n-1)</code>.
     */
    public int[] permutate(int n) {
        int[] x = IntStream.range(0, n).toArray();
        permutate(x);
        return x;
    }

    /**
     * Permutates an array in-place using the Fisher-Yates shuffle.
     * @param x the array.
     */
    public void permutate(int[] x) {
        // Stop at length-1: the last element has nowhere else to go.
        for (int i = x.length - 1; i > 0; i--) {
            int j = nextInt(i + 1);
            MathEx.swap(x, i, j);
        }
    }

    /**
     * Permutates an array in-place using the Fisher-Yates shuffle.
     * @param x the array.
     */
    public void permutate(float[] x) {
        for (int i = x.length - 1; i > 0; i--) {
            int j = nextInt(i + 1);
            MathEx.swap(x, i, j);
        }
    }

    /**
     * Permutates an array in-place using the Fisher-Yates shuffle.
     * @param x the array.
     */
    public void permutate(double[] x) {
        for (int i = x.length - 1; i > 0; i--) {
            int j = nextInt(i + 1);
            MathEx.swap(x, i, j);
        }
    }

    /**
     * Permutates an array in-place using the Fisher-Yates shuffle.
     * @param x the array.
     */
    public void permutate(Object[] x) {
        for (int i = x.length - 1; i > 0; i--) {
            int j = nextInt(i + 1);
            MathEx.swap(x, i, j);
        }
    }

    /**
     * Returns a random number from the standard normal distribution N(0, 1).
     * Uses the polar form of the Box-Muller transform; pairs of values are
     * generated and the spare is cached for the next call.
     *
     * @return a standard normal random number.
     */
    public double nextGaussian() {
        if (hasSpareGaussian) {
            hasSpareGaussian = false;
            return spareGaussian;
        }

        double u, v, s;
        do {
            u = nextDouble() * 2.0 - 1.0;
            v = nextDouble() * 2.0 - 1.0;
            s = u * u + v * v;
        } while (s >= 1.0 || s == 0.0);

        double mul = Math.sqrt(-2.0 * Math.log(s) / s);
        spareGaussian = v * mul;
        hasSpareGaussian = true;
        return u * mul;
    }

    /**
     * Returns a random number from the normal distribution N(mu, sigma).
     *
     * @param mu    the mean.
     * @param sigma the standard deviation.
     * @return a normal random number.
     */
    public double nextGaussian(double mu, double sigma) {
        return mu + sigma * nextGaussian();
    }

    /**
     * Randomly samples {@code k} distinct indices from the range
     * {@code [0, n)} without replacement, using a partial Fisher-Yates
     * shuffle. The result is returned in an unspecified order.
     *
     * @param n the population size; must be positive.
     * @param k the sample size; must satisfy {@code 0 <= k <= n}.
     * @return an array of {@code k} distinct indices.
     * @throws IllegalArgumentException if {@code k < 0} or {@code k > n}.
     */
    public int[] sample(int n, int k) {
        if (k < 0 || k > n) {
            throw new IllegalArgumentException(
                String.format("k (%d) must be in [0, n=%d]", k, n));
        }
        int[] result = new int[k];
        if (k == 0) return result;

        // Partial Fisher-Yates: build only the first k elements of a
        // permutation of [0, n) using a HashMap for the "swapped" positions
        // so we never allocate the full n-element array.
        java.util.HashMap<Integer, Integer> swapped = new java.util.HashMap<>();
        for (int i = 0; i < k; i++) {
            int j = i + nextInt(n - i);
            int xi = swapped.getOrDefault(i, i);
            int xj = swapped.getOrDefault(j, j);
            swapped.put(j, xi);
            result[i] = xj;
        }
        return result;
    }
}