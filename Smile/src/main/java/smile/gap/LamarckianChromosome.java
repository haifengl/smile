/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.gap;

/**
 * Artificial chromosomes used in Lamarckian algorithm that is a hybrid of
 * of evolutionary computation and a local improver such as hill-climbing.
 * Lamarckian algorithm augments an EA with some
 * hill-climbing during the fitness assessment phase to revise each individual
 * as it is being assessed. The revised individual replaces the original one
 * in the population.
 *
 * @author Haifeng Li
 */
public interface LamarckianChromosome extends Chromosome {
    /**
     * Performs a step of (hill-climbing) local search to evolve this chromosome.
     */
    public void evolve();
}
