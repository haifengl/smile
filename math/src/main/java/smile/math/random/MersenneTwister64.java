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

package smile.math.random;

/**
 * Mersenne Twister 64-bit.
 * <p>
 * Similar to the regular Mersenne Twister but is implemented use
 * 64-bit registers (Java <code>long</code>) and produces
 * different output.
 * <ul>
 * <li>  Makato Matsumoto and Takuji Nishimura,
 * <a href="http://www.math.sci.hiroshima-u.ac.jp/~m-mat/MT/ARTICLES/mt.pdf">"Mersenne Twister: A 623-Dimensionally Equidistributed Uniform Pseudo-Random Number Generator"</a>,
 * <i>ACM Transactions on Modeling and Computer Simulation, </i> Vol. 8, No. 1,
 * January 1998, pp 3--30.</li>
 * </ul>
 *
 * @author Haifeng Li
 */
public class MersenneTwister64 implements RandomNumberGenerator {
    private static final int NN = 312;

    private static final int MM = 156;

    private static final long MATRIX_A = 0xB5026F5AA96619E9L;

    /**
     * Mask: Most significant 33 bits
     */
    private static final long UM = 0xFFFFFFFF80000000L;

    /**
     * Mask: Least significant 31 bits
     */
    private static final long LM = 0x7FFFFFFFL;

    private static final long[] mag01 = {0L, MATRIX_A};

    private long[] mt = new long[NN];

    private int mti = NN + 1;

    /**
     * Internal to hold 64 bits, that might
     * used to generate two 32 bit values.
     */
    private long bits64;
    private boolean bitState = true;

    private final static long MAGIC_SEED = (180927757L << 32) | 976716835L;
    private final static long MAGIC_FACTOR1 = 6364136223846793005L;

    /**
     * Constructor.
     */
    public MersenneTwister64() {
        this(MAGIC_SEED);
    }

    /**
     * Constructor.
     *
     * @param seed
     */
    public MersenneTwister64(long seed) {
        setSeed(seed);
    }

    @Override
    public void setSeed(long seed) {
        mt[0] = seed;
        for (mti = 1; mti < NN; mti++) {
            mt[mti] = (MAGIC_FACTOR1 * (mt[mti - 1] ^ (mt[mti - 1] >>> 62)) + mti);
        }
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

        if (mti >= NN) { /* generate NN words at one time */
            for (i = 0; i < NN - MM; i++) {
                x = (mt[i] & UM) | (mt[i + 1] & LM);
                mt[i] = mt[i + MM] ^ (x >>> 1) ^ mag01[(int) (x & 1L)];
            }

            for (; i < NN - 1; i++) {
                x = (mt[i] & UM) | (mt[i + 1] & LM);
                mt[i] = mt[i + (MM - NN)] ^ (x >>> 1) ^ mag01[(int) (x & 1L)];
            }
            x = (mt[NN - 1] & UM) | (mt[0] & LM);
            mt[NN - 1] = mt[MM - 1] ^ (x >>> 1) ^ mag01[(int) (x & 1L)];

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

