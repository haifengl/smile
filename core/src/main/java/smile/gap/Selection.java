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

import smile.math.MathEx;

import java.util.Arrays;

/**
 * The way to select chromosomes from the population as parents to crossover.
 */
public interface Selection {

    /**
     * Select a chromosome with replacement from the population based on their
     * fitness. Note that the population should be in ascending order in terms
     * of fitness.
     */
    <T extends Chromosome> T apply(T[] population);

    /**
     * Roulette Wheel Selection, also called fitness proportionate selection.
     * Parents are selected by the ratio of its fitness to the fitness of
     * other members of the current population.
     * */
    static Selection RouletteWheel() {
        return new Selection() {
            @Override
            public <T extends Chromosome> T apply(T[] population) {
                int size = population.length;

                double worst = population[0].fitness();
                if (worst > 0.0) worst = 0.0;

                // In Roulete wheel selection, we don't do such scaling in
                // general. However, in case of negative fitness socres,
                // we need scale them to positive.
                double[] fitness = new double[size];
                for (int i = 0; i < size; i++) {
                    fitness[i] = population[i].fitness() - worst;
                }

                MathEx.unitize1(fitness);
                return population[MathEx.random(fitness)];
            }
        };
    }

    /**
     * Scaled Roulette Wheel Selection. As the average fitness of
     * chromosomes in the population increases, the variance of fitness
     * decreases in the population. There may be little difference between
     * the best and worst chromosome in the population after several
     * generations, and the selective pressure based on fitness is
     * corresponding reduced. This problem can partially be addressed by
     * using some form of fitness scaling. In the simplest case, on can
     * subtract the fitness of the worst chromosome in the population
     * from the fitnesses of all chromosomes in the population.
     * Alternatively, one may use rank based selection.
     */
    static Selection ScaledRouletteWheel() {
        return new Selection() {
            @Override
            public <T extends Chromosome> T apply(T[] population) {
                int size = population.length;
                double worst = population[0].fitness();

                double[] fitness = new double[size];
                for (int i = 0; i < size; i++) {
                    fitness[i] = population[i].fitness() - worst;
                }

                MathEx.unitize1(fitness);
                return population[MathEx.random(fitness)];
            }
        };
    }

    /**
     * Rank Selection. The Roulette Wheel Selection will have problems when
     * the fitnesses differs very much. For example, if the best chromosome
     * fitness is 90% of all the roulette wheel then the other chromosomes
     * will have very few chances to be selected. Rank selection first ranks
     * the population and then every chromosome receives fitness from this
     * ranking. The worst will have fitness 1 and the best will have fitness
     * N (number of chromosomes in population). After this all the
     * chromosomes have a chance to be selected. But this method can lead
     * to slower convergence, because the best chromosomes do not differ
     * so much from other ones.
     */
    static Selection Rank() {
        return new Selection() {
            @Override
            public <T extends Chromosome> T apply(T[] population) {
                int size = population.length;

                double[] fitness = new double[size];
                for (int i = 0; i < size; i++) {
                    fitness[i] = i + 1;
                }

                MathEx.unitize1(fitness);
                return population[MathEx.random(fitness)];
            }
        };
    }

    /**
     * Tournament Selection. Tournament selection returns the fittest
     * individual of some t individuals picked at random, with replacement,
     * from the population. First choose t (the tournament size) individuals
     * from the population at random. Then choose the best individual from
     * tournament with probability p, choose the second best individual with
     * probability p*(1-p), choose the third best individual with
     * probability p*((1-p)^2), and so on... Tournament Selection has become
     * the primary selection technique used for the Genetic Algorithm.
     * First, it's not sensitive to the particulars of the fitness function.
     * Second, it's very simple, requires no preprocessing, and works well
     * with parallel algorithms. Third, it's tunable: by setting the
     * tournament size t, you can change how selective the technique is.
     * At the extremes, if t = 1, this is just random search. If t is very
     * large (much larger than the population size itself), then the
     * probability that the fittest individual in the population will appear
     * in the tournament approaches 1.0, and so Tournament Selection just
     * picks the fittest individual each time.
     *
     * @param size the size of tournament pool.
     * @param probability the best-player-wins probability.
     */
    static Selection Tournament(int size, double probability) {
        return new Selection() {
            @Override
            @SuppressWarnings("unchecked")
            public <T extends Chromosome> T apply(T[] population) {
                Chromosome[] pool = new Chromosome[size];
                for (int i = 0; i < size; i++) {
                    pool[i] = population[MathEx.randomInt(population.length)];
                }

                Arrays.sort(pool);
                for (int i = 1; i <= size; i++) {
                    double p = MathEx.random();
                    if (p < probability) {
                        return (T) pool[size - i];
                    }
                }

                return (T) pool[size - 1];
            }
        };
    }
}

