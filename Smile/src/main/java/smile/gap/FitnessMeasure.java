/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.gap;

/**
 * A measure to evaluate the fitness of chromosomes.
 *
 * @author Haifeng Li
 */
public interface FitnessMeasure <T extends Chromosome> {

    /**
     * Returns the non-negative fitness value of a chromosome. Large values
     * indicate better fitness.
     */
    public double fit(T chromosome);
}
