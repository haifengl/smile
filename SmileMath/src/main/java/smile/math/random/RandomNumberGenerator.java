/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/
package smile.math.random;

/**
 * Random number generator interface.
 * 
 * @author Haifeng Li
 */
public interface RandomNumberGenerator {
    
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