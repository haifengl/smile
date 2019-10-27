/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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
 *******************************************************************************/

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
    void evolve();
}
