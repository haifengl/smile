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
package smile.math.random;

/**
 * The so-called "Universal Generator" based on multiplicative congruential
 * method, which originally appeared in "Toward a Universal Random Number
 * Generator" by Marsaglia, Zaman and Tsang. It was later modified by F. James
 * in "A Review of Pseudo-random Number Generators". It passes ALL the tests
 * for random number generators and has a period of 2<sup>144</sup>. It is
 * completely portable (gives bit-identical results on all machines with at
 * least 24-bit mantissas in the floating point representation).
 *
 * @author Haifeng Li
 */
public class UniversalGenerator implements RandomNumberGenerator {

    /**
     * Default seed.
     */
    private static final int DEFAULT_SEED = 54217137;
    /**
     * The 46,009,220nd prime number, the largest prime less than {@code 9E8}.
     * It may be used as a modulus for RNGs because this RNG needs a seed between 0
     * and {@code 9E8} and BIG_PRIME isn't commensurate with any regular period.
     */
    private static final int BIG_PRIME = 899999963;
    private double c;
    private double cd;
    private double cm;
    private double[] u;
    private int i97, j97;

    /**
     * Initialize Random with default seed.
     */
    public UniversalGenerator() {
        setSeed(DEFAULT_SEED);
    }

    /**
     * Initialize Random with a specified integer seed
     * @param seed the seed of random numbers.
     */
    public UniversalGenerator(int seed) {
        setSeed(seed);
    }

    /**
     * Initialize Random with a specified long seed
     * @param seed the seed of random numbers.
     */
    public UniversalGenerator(long seed) {
        setSeed(seed);
    }

    @Override
    public void setSeed(long seed) {
        u = new double[97];

        int ijkl = Math.abs((int) (seed % BIG_PRIME));
        int ij = ijkl / 30082;
        int kl = ijkl % 30082;
        // ijkl in [0, BIG_PRIME-1]; ij = ijkl/30082 ∈ [0, 29900] ⊆ [0, 31328]
        // kl = ijkl%30082 ∈ [0, 30081]; both within valid ranges by construction.

        int i = ((ij / 177) % 177) + 2;
        int j = (ij % 177) + 2;
        int k = ((kl / 169) % 178) + 1;
        int l = kl % 169;

        int m;
        double s, t;
        for (int ii = 0; ii < 97; ii++) {
            s = 0.0;
            t = 0.5;
            for (int jj = 0; jj < 24; jj++) {
                m = (((i * j) % 179) * k) % 179;
                i = j;
                j = k;
                k = m;
                l = (53 * l + 1) % 169;
                if (((l * m) % 64) >= 32) {
                    s += t;
                }
                t *= 0.5;
            }
            u[ii] = s;
        }

        c = 362436.0 / 16777216.0;
        cd = 7654321.0 / 16777216.0;
        cm = 16777213.0 / 16777216.0;
        i97 = 96;
        j97 = 32;
    }

    @Override
    public double nextDouble() {
        double uni;

        uni = u[i97] - u[j97];
        if (uni < 0.0) {
            uni += 1.0;
        }

        u[i97] = uni;
        if (--i97 < 0) {
            i97 = 96;
        }

        if (--j97 < 0) {
            j97 = 96;
        }

        c -= cd;
        if (c < 0.0) {
            c += cm;
        }

        uni -= c;
        if (uni < 0.0) {
            uni++;
        }

        return uni;
    }

    @Override
    public void nextDoubles(double[] d) {
        int n = d.length;

        double uni;

        for (int i = 0; i < n; i++) {
            uni = u[i97] - u[j97];
            if (uni < 0.0) {
                uni += 1.0;
            }
            u[i97] = uni;
            if (--i97 < 0) {
                i97 = 96;
            }
            if (--j97 < 0) {
                j97 = 96;
            }
            c -= cd;
            if (c < 0.0) {
                c += cm;
            }
            uni -= c;
            if (uni < 0.0) {
                uni += 1.0;
            }
            d[i] = uni;
        }
    }

    @Override
    public int next(int numbits) {
        return nextInt() >>> (32 - numbits);
    }

    @Override
    public int nextInt() {
        // Scale [0,1) to the full int range by mapping to [0, 2^32)
        // then reinterpreting as signed int, avoiding overflow.
        return (int) (long) (nextDouble() * 0x1p32);
    }

    @Override
    public int nextInt(int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("n must be positive");
        }
        int result = (int) (nextDouble() * n);
        // Guard against rare case where floating-point rounds up to n
        return result < n ? result : n - 1;
    }

    @Override
    public long nextLong() {
        // Combine two 32-bit halves from separate nextDouble() draws
        long hi = (long) (nextDouble() * 0x1p32);
        long lo = (long) (nextDouble() * 0x1p32);
        return (hi << 32) | (lo & 0xFFFFFFFFL);
    }
}