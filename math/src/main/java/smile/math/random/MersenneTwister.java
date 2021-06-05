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
 * 32-bit Mersenne Twister. This implements the MT19937 (Mersenne Twister)
 * pseudo random number generator algorithm based upon the original C code
 * by Makoto Matsumoto and Takuji Nishimura (<a href="http://www.math.sci.hiroshima-u.ac.jp/~m-mat/MT/emt.html">
 * http://www.math.sci.hiroshima-u.ac.jp/~m-mat/MT/emt.html</a>).
 * <p>
 * As a subclass of java.util.Random this class provides a single canonical
 * method {@code next()} for generating bits in the pseudo random
 * number sequence. The user should invoke the public inherited methods
 * ({@code nextInt()}, {@code nextFloat()} etc.) to obtain values
 * as usual. This class should provide a drop-in replacement for the standard
 * implementation of {@code java.util.Random} with the additional advantage
 * of having a far longer period and the ability to use a far larger seed value.
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
public class MersenneTwister implements RandomNumberGenerator {
    /** Mask: Most significant 17 bits */
    private final static int UPPER_MASK = 0x80000000;
    /** Mask: Least significant 15 bits */
    private final static int LOWER_MASK = 0x7fffffff;
    /** Size of the bytes pool. */
    private final static int N = 624;
    /** Period second parameter. */
    private final static int M = 397;
    private final static int[] MAGIC = {0x0, 0x9908b0df};
    /** The factors used in state initialization. */
    private final static int MAGIC_FACTOR1 = 1812433253;
    private final static int MAGIC_FACTOR2 = 1664525;
    private final static int MAGIC_FACTOR3 = 1566083941;
    private final static int MAGIC_MASK1 = 0x9d2c5680;
    private final static int MAGIC_MASK2 = 0xefc60000;
    /** The default seed. */
    private final static int MAGIC_SEED = 19650218;
    /** Internal state */
    private final int[] mt = new int[N];
    /** Current index in the internal state. */
    private int mti;

    /**
     * Constructor.
     */
    public MersenneTwister() {
        this(MAGIC_SEED);
    }

    /**
     * Constructor.
     * @param seed the seed of random numbers.
     */
    public MersenneTwister(int seed) {
        setSeed(seed);
    }

    /**
     * Constructor.
     * @param seed the seed of random numbers.
     */
    public MersenneTwister(long seed) {
        setSeed(seed);
    }

    @Override
    public void setSeed(long seed) {
        // Integer.MAX_VALUE (2,147,483,647) is the 8th Mersenne prime.
        // Therefore, it is good as a modulus for RNGs.
        setSeed((int) (seed % Integer.MAX_VALUE));
    }

    /**
     * Sets the seed of random numbers.
     * @param seed the seed of random numbers.
     */
    public void setSeed(int seed) {
        mt[0] = seed;
        for (mti = 1; mti < N; mti++) {
            mt[mti] = (MAGIC_FACTOR1 * (mt[mti - 1] ^ (mt[mti - 1] >>> 30)) + mti);
        }
    }

    /**
     * Sets the seed of random numbers.
     * @param seed the seed of random numbers.
     */
    public void setSeed(int[] seed) {
        setSeed(MAGIC_SEED);

        if (seed == null || seed.length == 0) {
            return;
        }

        int i = 1, j = 0;
        for (int k = Math.max(N, seed.length); k > 0; k--) {
            mt[i] = (mt[i] ^ ((mt[i - 1] ^ (mt[i - 1] >>> 30)) * MAGIC_FACTOR2)) + seed[j] + j;
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

        for (int k = N - 1; k > 0; k--) {
            mt[i] = (mt[i] ^ ((mt[i - 1] ^ (mt[i - 1] >>> 30)) * MAGIC_FACTOR3)) - i;
            i++;
            if (i >= N) {
                mt[0] = mt[N - 1];
                i = 1;
            }
        }
        mt[0] |= UPPER_MASK; // MSB is 1; assuring non-zero initial array
    }

    @Override
    public int next(int numbits) {
        return nextInt() >>> (32 - numbits);
    }
    
    @Override
    public double nextDouble() {
        return (nextInt() >>> 1) / (double) Integer.MAX_VALUE;
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
        int x, i;
        
        if (mti >= N) {
            // generate N words at one time
            for (i = 0; i < N - M; i++) {
                x = (mt[i] & UPPER_MASK) | (mt[i + 1] & LOWER_MASK);
                mt[i] = mt[i + M] ^ (x >>> 1) ^ MAGIC[x & 0x1];
            }
            for (; i < N - 1; i++) {
                x = (mt[i] & UPPER_MASK) | (mt[i + 1] & LOWER_MASK);
                mt[i] = mt[i + (M - N)] ^ (x >>> 1) ^ MAGIC[x & 0x1];
            }
            x = (mt[N - 1] & UPPER_MASK) | (mt[0] & LOWER_MASK);
            mt[N - 1] = mt[M - 1] ^ (x >>> 1) ^ MAGIC[x & 0x1];

            mti = 0;
        }

        x = mt[mti++];

        // Tempering
        x ^= (x >>> 11);
        x ^= (x << 7) & MAGIC_MASK1;
        x ^= (x << 15) & MAGIC_MASK2;
        x ^= (x >>> 18);

        return x;
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
        long x = nextInt();
        return (x << 32) | nextInt();
    }
}
