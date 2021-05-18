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
}