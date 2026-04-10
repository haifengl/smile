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
 * Random number generator interface.
 * 
 * @author Haifeng Li
 */
public interface RandomNumberGenerator {
    /**
     * Initialize the random generator with a seed.
     * @param seed the seed of random numbers.
     */
    void setSeed(long seed);

    /**
     * Returns up to 32 random bits.
     * @param numbits the number of random bits to generate.
     * @return random bits.
     */
    int next(int numbits);

    /**
     * Returns the next pseudorandom, uniformly distributed int value
     * from this random number generator's sequence.
     * @return random number.
     */
    int nextInt();

    /**
     * Returns a pseudorandom, uniformly distributed int value
     * between 0 (inclusive) and the specified value (exclusive),
     * drawn from this random number generator's sequence.
     * @param n the upper bound of random number (exclusive).
     * @return random number.
     */
    int nextInt(int n);

    /**
     * Returns the next pseudorandom, uniformly distributed long value
     * from this random number generator's sequence.
     * @return random number.
     */
    long nextLong();

    /**
     * Returns the next pseudorandom, uniformly distributed double value
     * between 0.0 and 1.0 from this random number generator's sequence.
     * @return random number.
     */
    double nextDouble();

    /**
     * Returns a vector of pseudorandom, uniformly distributed double values
     * between 0.0 and 1.0 from this random number generator's sequence.
     * @param d the output random numbers.
     */
    void nextDoubles(double[] d);

    /**
     * Returns the next pseudorandom, Gaussian ("normally") distributed double
     * value with mean 0.0 and standard deviation 1.0. Uses the Box-Muller
     * transform on two uniform samples.
     * @return a Gaussian-distributed random number.
     */
    default double nextGaussian() {
        double u, v, s;
        do {
            u = 2.0 * nextDouble() - 1.0;
            v = 2.0 * nextDouble() - 1.0;
            s = u * u + v * v;
        } while (s >= 1.0 || s == 0.0);
        return u * Math.sqrt(-2.0 * Math.log(s) / s);
    }
}