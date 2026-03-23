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
 * Artificial chromosomes used in Lamarckian algorithm that is a hybrid of
 * evolutionary computation and a local improver such as hill-climbing.
 * Lamarckian algorithm augments an EA with some
 * hill-climbing during the fitness assessment phase to revise each individual
 * as it is being assessed. The revised individual replaces the original one
 * in the population.
 *
 * @param <T> the type of the Chromosome.
 * @author Haifeng Li
 */
public interface LamarckianChromosome<T extends Chromosome<T>> extends Chromosome<T> {
    /**
     * Performs a step of (hill-climbing) local search to evolve this chromosome.
     */
    void evolve();
}
