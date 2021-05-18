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

package smile.math.random;

/**
 * 64-bit Mersenne Twister. Similar to the regular Mersenne Twister
 * but is implemented use 64-bit registers (Java <code>long</code>)
 * and produces different output.
 *
 * <h2>References</h2>
 * <ol>
 * <li> Makato Matsumoto and Takuji Nishimura,
 * <a href="http://www.math.sci.hiroshima-u.ac.jp/~m-mat/MT/ARTICLES/mt.pdf">"Mersenne Twister: A 623-Dimensionally Equidistributed Uniform Pseudo-Random Number Generator"</a>,
 * <i>ACM Transactions on Modeling and Computer Simulation, </i> Vol. 8, No. 1,
 * January 1998, pp 3--30.</li>
 * </ol>
 *
 * @author Haifeng Li
 */
public class MersenneTwister64 implements RandomNumberGenerator {
    /** Size of the bytes pool. */
    private static final int N = 312;
    /** Period second parameter. */
    private static final int M = 156;
    /** Mask: Most significant 33 bits */
    private static final long UPPER_MASK = 0xFFFFFFFF80000000L;
    /** Mask: Least significant 31 bits */
    private static final long LOWER_MASK = 0x7FFFFFFFL;
    /** X * MATRIX_A for X = {0, 1}. */
    private static final long[] MAGIC = {0L, 0xB5026F5AA96619E9L};
    /** The default seed. */
    private final static long MAGIC_SEED = (180927757L << 32) | 976716835L;
    /** The factors used in state initialization. */
    private final static long MAGIC_FACTOR1 = 6364136223846793005L;
    private final static long MAGIC_FACTOR2 = 3935559000370003845L;
    private final static long MAGIC_FACTOR3 = 2862933555777941757L;
    /** Internal state */
    private final long[] mt = new long[N];
    /** Current index in the bytes pool. */
    private int mti;

    /**
     * Internal state to hold 64 bits, that might
     * used to generate two 32 bit values.
     */
    private long bits64;
    private boolean bitState = true;

    /**
     * Constructor.
     */
    public MersenneTwister64() {
        this(MAGIC_SEED);
    }

    /**
     * Constructor.
     *
     * @param seed the seed of random numbers.
     */
    public MersenneTwister64(long seed) {
        setSeed(seed);
    }

    @Override
    public void setSeed(long seed) {
        mt[0] = seed;
        for (mti = 1; mti < N; mti++) {
            mt[mti] = (MAGIC_FACTOR1 * (mt[mti - 1] ^ (mt[mti - 1] >>> 62)) + mti);
        }
    }

    /**
     * Sets the seed of random numbers.
     * @param seed the seed of random numbers.
     */
    public void setSeed(long[] seed) {
        setSeed(MAGIC_SEED);

        if (seed == null || seed.length == 0) {
            return;
        }

        int i = 1, j = 0;
        for (int k = Math.max(N, seed.length); k != 0; k--) {
            final long mm1 = mt[i - 1];
            mt[i] = (mt[i] ^ ((mm1 ^ (mm1 >>> 62)) * MAGIC_FACTOR2)) + seed[j] + j; // non linear
            i++;
            j++;
            if (i >= N) {
                mt[0] = mt[N - 1];
                i = 1;
            }
            if (j >= seed.length) {
                j = 0;
            }
        }

        for (int k = N - 1; k != 0; k--) {
            final long mm1 = mt[i - 1];
            mt[i] = (mt[i] ^ ((mm1 ^ (mm1 >>> 62)) * MAGIC_FACTOR3)) - i; // non linear
            i++;
            if (i >= N) {
                mt[0] = mt[N - 1];
                i = 1;
            }
        }

        mt[0] = 0x8000000000000000L; // MSB is 1; assuring non-zero initial array
    }

    @Override
    public int next(int numbits) {
        if (bitState) {
            bits64 = nextLong();
            bitState = false;
            return (int) (bits64 >>> (64 - numbits));
        } else {
            bitState = true;
            return ((int) bits64) >>> (32 - numbits);
        }
    }

    @Override
    public double nextDouble() {
        return (nextLong() >>> 1) / (double) Long.MAX_VALUE;
    }

    @Override
    public void nextDoubles(double[] d) {
        int n = d.length;
        for (int i = 0; i < n; i++) {
            d[i] = nextDouble();
        }
    }

    @Override
    public int nextInt() {
        return next(32);
    }

    @Override
    public int nextInt(int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("n must be positive");
        }

        // n is a power of 2
        if ((n & -n) == n) {
            return (int) ((n * (long) next(31)) >> 31);
        }

        int bits, val;
        do {
            bits = next(31);
            val = bits % n;
        } while (bits - val + (n - 1) < 0);

        return val;
    }

    @Override
    public long nextLong() {
        int i;
        long x;

        if (mti >= N) { /* generate NN words at one time */
            for (i = 0; i < N - M; i++) {
                x = (mt[i] & UPPER_MASK) | (mt[i + 1] & LOWER_MASK);
                mt[i] = mt[i + M] ^ (x >>> 1) ^ MAGIC[(int) (x & 1L)];
            }

            for (; i < N - 1; i++) {
                x = (mt[i] & UPPER_MASK) | (mt[i + 1] & LOWER_MASK);
                mt[i] = mt[i + (M - N)] ^ (x >>> 1) ^ MAGIC[(int) (x & 1L)];
            }
            x = (mt[N - 1] & UPPER_MASK) | (mt[0] & LOWER_MASK);
            mt[N - 1] = mt[M - 1] ^ (x >>> 1) ^ MAGIC[(int) (x & 1L)];

            mti = 0;
        }

        x = mt[mti++];

        // Tempering
        x ^= (x >>> 29) & 0x5555555555555555L;
        x ^= (x << 17) & 0x71D67FFFEDA60000L;
        x ^= (x << 37) & 0xFFF7EEE000000000L;
        x ^= (x >>> 43);

        return x;
    }
}

