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

/**
 * Random number generator interface.
 * 
 * @author Haifeng Li
 */
public interface RandomNumberGenerator {
    /**
     * Initialize the random generator with a seed.
     */
    public void setSeed(long seed);

    /**
     * Returns up to 32 random bits.
     */
    public int next(int numbits);

    /**
     * Returns the next pseudorandom, uniformly distributed int value
     * from this random number generator's sequence.
     */
    public int nextInt();
    
    /**
     * Returns a pseudorandom, uniformly distributed int value
     * between 0 (inclusive) and the specified value (exclusive),
     * drawn from this random number generator's sequence.
     */
    public int nextInt(int n);
    
    /**
     * Returns the next pseudorandom, uniformly distributed long value
     * from this random number generator's sequence.
     */
    public long nextLong();
    
    /**
     * Returns the next pseudorandom, uniformly distributed double value
     * between 0.0 and 1.0 from this random number generator's sequence.
     */
    public double nextDouble();
    
    /**
     * Returns a vector of pseudorandom, uniformly distributed double values
     * between 0.0 and 1.0 from this random number generator's sequence.
     */
    public void nextDoubles(double[] d);
}