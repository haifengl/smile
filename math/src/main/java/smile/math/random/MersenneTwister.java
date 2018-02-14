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
package smile.math.random;

/*
 * Mersenne Twister 32-bit.
 * <p>
 * This implements the MT19937 (Mersenne Twister) pseudo random number generator
 * algorithm based upon the original C code by Makoto Matsumoto and Takuji
 * Nishimura (<a
 * href="http://www.math.sci.hiroshima-u.ac.jp/~m-mat/MT/emt.html">
 * http://www.math.sci.hiroshima-u.ac.jp/~m-mat/MT/emt.html</a>). <p> As a
 * subclass of java.util.Random this class provides a single canonical method
 * next() for generating bits in the pseudo random number sequence. Anyone using
 * this class should invoke the public inherited methods (nextInt(), nextFloat
 * etc.) to obtain values as normal. This class should provide a drop-in
 * replacement for the standard implementation of java.util.Random with the
 * additional advantage of having a far longer period and the ability to use a
 * far larger seed value.
 * <ul>
 * <li>  Makato Matsumoto and Takuji Nishimura,
 * <a href="http://www.math.sci.hiroshima-u.ac.jp/~m-mat/MT/ARTICLES/mt.pdf">"Mersenne Twister: A 623-Dimensionally Equidistributed Uniform Pseudo-Random Number Generator"</a>,
 * <i>ACM Transactions on Modeling and Computer Simulation, </i> Vol. 8, No. 1,
 * January 1998, pp 3--30.</li>
 * </ul>
 *
 * @author Haifeng Li
 */
public class MersenneTwister implements RandomNumberGenerator {

    // Constants used in the original C implementation
    private final static int UPPER_MASK = 0x80000000;
    private final static int LOWER_MASK = 0x7fffffff;
    private final static int N = 624;
    private final static int M = 397;
    private final static int MAGIC[] = {0x0, 0x9908b0df};
    private final static int MAGIC_FACTOR1 = 1812433253;
    //private final static int MAGIC_FACTOR2 = 1664525;
    //private final static int MAGIC_FACTOR3 = 1566083941;
    private final static int MAGIC_MASK1 = 0x9d2c5680;
    private final static int MAGIC_MASK2 = 0xefc60000;
    private final static int MAGIC_SEED = 19650218;
    // The seed used in the paper.
    //private final static int DEFAULT_SEED = 5489;
    // Internal state
    private transient int[] mt = new int[N];
    private transient int mti;

    /**
     * Constructor.
     */
    public MersenneTwister() {
        this(MAGIC_SEED);
    }

    /**
     * Constructor.
     */
    public MersenneTwister(int seed) {
        setSeed(seed);
    }

    /**
     * Constructor.
     */
    public MersenneTwister(long seed) {
        setSeed(seed);
    }

    @Override
    public void setSeed(long seed) {
        setSeed((int) (seed % UniversalGenerator.BIG_PRIME));
    }

    public void setSeed(int seed) {
        mt[0] = seed;
        for (mti = 1; mti < N; mti++) {
            mt[mti] = (MAGIC_FACTOR1 * (mt[mti - 1] ^ (mt[mti - 1] >>> 30)) + mti);
        }
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
        
        if (mti >= N) {             // generate N words at one time

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
