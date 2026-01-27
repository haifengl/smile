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
package smile.gap;

/**
 * Artificial chromosomes in genetic algorithm/programming encoding candidate
 * solutions to an optimization problem. Note that chromosomes have to
 * implement Comparable interface to support comparison of their fitness.
 *
 * @param <T> the type of the Chromosome.
 * @author Haifeng Li
 */
public interface Chromosome<T extends Chromosome<T>> extends Comparable<Chromosome<T>> {

    /**
     * Returns the fitness of chromosome.
     * @return the fitness of chromosome.
     */
    double fitness();

    /**
     * Returns a new random instance.
     * @return a new random instance.
     */
    T newInstance();

    /**
     * Returns a pair of offsprings by crossovering this one with another one
     * according to the crossover rate, which determines how often will be
     * crossover performed. If there is no crossover, offspring is exact copy of
     * parents. Various crossover strategies can be employed.
     * @param other the other parent.
     * @return a pair of offsprings.
     */
    T[] crossover(T other);

    /**
     * For genetic algorithms, this method mutates the chromosome randomly.
     * The offspring may have no changes since the mutation rate is usually
     * very low. For Lamarckian algorithms, this method actually does the local
     * search such as hill-climbing.
     */
    void mutate();
}
