/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.gap;

/**
 * Artificial chromosomes in genetic algorithm/programming encoding candidate
 * solutions to an optimization problem. Note that chromosomes have to
 * implement Comparable interface to support comparison of their fitness.
 *
 * @author Haifeng Li
 */
public interface Chromosome extends Comparable<Chromosome> {

    /**
     * Returns the fitness of chromosome.
     */
    public double fitness();

    /**
     * Returns a new random instance.
     */
    public Chromosome newInstance();

    /**
     * Returns a pair of offsprings by crossovering this one with another one
     * according to the crossover rate, which determines how often will be
     * crossover performed. If there is no crossover, offspring is exact copy of
     * parents. Various crossover strategies can be employed.
     */
    public Chromosome[] crossover(Chromosome another);

    /**
     * For genetic algorithms, this method mutates the chromosome randomly.
     * The offspring may have no changes since the mutation rate is usually
     * very low. For Lamarckian algorithms, this method actually does the local
     * search such as such as hill-climbing.
     */
    public void mutate();
}
