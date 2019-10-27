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

import java.util.Arrays;

/**
 * A genetic algorithm (GA) is a search heuristic that mimics the process of
 * natural evolution. This heuristic is routinely used to generate useful
 * solutions to optimization and search problems. Genetic algorithms belong
 * to the larger class of evolutionary algorithms (EA), which generate solutions
 * to optimization problems using techniques inspired by natural evolution,
 * such as inheritance, mutation, selection, and crossover.
 * <p>
 * In a genetic algorithm, a population of strings (called chromosomes), which
 * encode candidate solutions (called individuals) to an optimization problem,
 * evolves toward better solutions. Traditionally, solutions are represented in binary as strings
 * of 0s and 1s, but other encodings are also possible. The evolution usually
 * starts from a population of randomly generated individuals and happens in
 * generations. In each generation, the fitness of every individual in the
 * population is evaluated, multiple individuals are stochastically selected
 * from the current population (based on their fitness), and modified
 * (recombined and possibly randomly mutated) to form a new population. The
 * new population is then used in the next iteration of the algorithm. Commonly,
 * the algorithm terminates when either a maximum number of generations has been
 * produced, or a satisfactory fitness level has been reached for the population.
 * If the algorithm has terminated due to a maximum number of generations, a
 * satisfactory solution may or may not have been reached.
 * <p>
 * A typical genetic algorithm requires:
 * <ul>
 * <li> a genetic representation of the solution domain,
 * <li>a fitness function to evaluate the solution domain.
 * </ul>
 * A standard representation of the solution is as an array of bits. Arrays
 * of other types and structures can be used in essentially the same way.
 * The main property that makes these genetic representations convenient is
 * that their parts are easily aligned due to their fixed size, which
 * facilitates simple crossover operations. Variable length representations
 * may also be used, but crossover implementation is more complex in this case.
 * Tree-like representations are explored in genetic programming and graph-form
 * representations are explored in evolutionary programming.
 * <p>
 * The fitness function is defined over the genetic representation and measures
 * the quality of the represented solution. The fitness function is always
 * problem dependent.
 * <p>
 * Once we have the genetic representation and the fitness function defined,
 * GA proceeds to initialize a population of solutions randomly, then improve
 * it through repetitive application of selection, crossover and mutation operators.
 * <p>
 * Here are some basic recommendations of typical parameter values on binary
 * encoding based on empiric studies.
 * <dl>
 * <dt>Crossover Rate</dt>
 * <dd> Crossover rate determines how often will be crossover performed. If
 * there is no crossover, offspring is exact copy of parents. If there is a
 * crossover, offspring is made from parts of parents' chromosome. If crossover
 * rate is 100%, then all offspring is made by crossover. If it is 0%, whole
 * new generation is made from exact copies of chromosomes from old population.
 * However, it this does not mean that the new generation is the same because
 * of mutation. Crossover is made in hope that new chromosomes will have good
 * parts of old chromosomes and maybe the new chromosomes will be better.
 * However it is good to leave some part of population survive to next
 * generation. Crossover rate generally should be high, about 80% - 95%.
 * However some results show that for some problems crossover rate about 60% is
 * the best.</dd>
 * <dt>Mutation Rate</dt>
 * <dd> Mutation rate determines how often will be parts of chromosome mutated.
 * If there is no mutation, offspring is taken after crossover (or copy) without
 * any change. If mutation is performed, part of chromosome is changed.
 * Mutation is made to prevent falling GA into local extreme, but it should not
 * occur very often, because then GA will in fact change to random search.
 * Best rates reported are about 0.5% - 1%.</dd>
 * <dt>Population Size</dt>
 * <dd> Population size is the number of chromosomes in population (in one
 * generation). If there are too few chromosomes, GA have a few possibilities
 * to perform crossover and only a small part of search space is explored.
 * On the other hand, if there are too many chromosomes, GA slows down. Good
 * population size is about 20-30, however sometimes sizes 50-100 are reported
 * as best. Some research also shows, that best population size depends on
 * encoding, on size of encoded string.</dd>
 * <dt>Selection</dt>
 * <dd> Tournament selection has become the primary selection technique used for
 * the Genetic Algorithm. Basic roulette wheel selection can be used, but
 * sometimes rank selection can be better. In general, elitism should be used
 * unless other method is used to save the best found solution.</dd>
 * </dl>
 * This implementation also supports Lamarckian algorithm that is a hybrid of
 * of evolutionary computation and a local improver such as hill-climbing.
 * Lamarckian algorithm augments an EA with some
 * hill-climbing during the fitness assessment phase to revise each individual
 * as it is being assessed. The revised individual replaces the original one
 * in the population.
 * <p>
 * The number of iterations t during each fitness assessment is a knob that
 * adjusts the degree of exploitation in the algorithm. If t is very large,
 * then we're doing more hill-climbing and thus more exploiting; whereas if
 * t is very small, then we're spending more time in the outer algorithm and
 * thus doing more exploring.
 * 
 * @author Haifeng Li
 */
public class GeneticAlgorithm <T extends Chromosome> {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(GeneticAlgorithm.class);

    /**
     * Population size.
     */
    private int size;
    /**
     * Population.
     */
    private T[] population;
    /**
     * Selection strategy.
     */
    private Selection selection;
    /**
     * The number of best chromosomes to copy to new population. When creating
     * new population by crossover and mutation, we have a big chance, that we
     * will loose the best chromosome. Elitism first copies the best chromosome
     * (or a few best chromosomes) to new population. The rest is done in
     * classical way. Elitism can very rapidly increase performance of GA,
     * because it prevents losing the best found solution.
     */
    private int elitism;
    /**
     * The number of iterations of local search in case Lamarckian algorithm.
     */
    private int t = 5;
    
    /**
     * Constructor. The default selection strategy is tournament selection
     * of size 3 and probability 0.95.
     * Elitism is also used (the best chromosome is copied to next generation).
     * @param seeds the initial population, which is usually randomly generated.
     */
    public GeneticAlgorithm(T[] seeds) {
        this(seeds, Selection.Tournament(3, 0.95), 1);
    }

    /**
     * Constructor. By default, elitism is used (the best chromosome is copied
     * to next generation).
     * @param selection the selection strategy.
     * @param seeds the initial population, which is usually randomly generated.
     * @param elitism the number of best chromosomes to copy to new population.
     *                When creating new population by crossover and mutation,
     *                we have a big chance, that we will loose the best chromosome.
     *                Elitism first copies the best chromosome (or a few best
     *                chromosomes) to new population. The rest is done in classical
     *                way. Elitism can very rapidly increase performance of GA,
     *                because it prevents losing the best found solution.
     */
    public GeneticAlgorithm(T[] seeds, Selection selection, int elitism) {
        if (elitism < 0 || elitism >= seeds.length) {
            throw new IllegalArgumentException("Invalid elitism: " + elitism);
        }

        this.size = seeds.length;
        this.population = seeds;
        this.selection = selection;
        this.elitism = elitism;
    }

    /**
     * Returns the population of current generation.
     */
    public T[] population() {
        return population;
    }

    /**
     * Sets the number of iterations of local search for Lamarckian algorithm.
     * Sets it be zero to disable local search.
     * @param t the number of iterations of local search.
     */
    public GeneticAlgorithm<T> setLocalSearchSteps(int t) {
        if (t < 0) {
            throw new IllegalArgumentException("Invalid of number of iterations of local search: " + t);
        }
        
        this.t = t;
        return this;
    }
    
    /**
     * Gets the number of iterations of local search for Lamarckian algorithm.
     * @return t the number of iterations of local search.
     */
    public int getLocalSearchSteps() {
        return t;
    }
    
    /**
     * Performs genetic algorithm for a given number of generations.
     * @param generation the number of iterations.
     * @return the best chromosome of last generation in terms of fitness.
     */
    public T evolve(int generation) {
        return evolve(generation, Double.POSITIVE_INFINITY);
    }

    /**
     * Performs genetic algorithm until the given number of generations is reached
     * or the best fitness is larger than the given threshold.
     *
     * @param generation the maximum number of iterations.
     * @param threshold the fitness threshold. The algorithm stops when a
     * solution is found that satisfies minimum criteria.
     * @return the best chromosome of last generation in terms of fitness.
     */
    public T evolve(int generation, double threshold) {
        if (generation <= 0) {
            throw new IllegalArgumentException("Invalid number of generations to go: " + generation);
        }
        
        // Calculate the fitness of each chromosome.
        Arrays.stream(population).parallel().forEach(chromosome -> {
            if (chromosome instanceof LamarckianChromosome) {
                LamarckianChromosome ch = (LamarckianChromosome) chromosome;
                for (int j = 0; j < t; j++) {
                    ch.evolve();
                }
            }

            chromosome.fitness();
        });

        Arrays.sort(population);
        T best = population[size-1];

        Chromosome[] offsprings = new Chromosome[size];
        for (int g = 1; g <= generation && best.fitness() < threshold; g++) {
            for (int i = 0; i < elitism; i++) {
                offsprings[i] = population[size-i-1];
            }

            for (int i = elitism; i < size; i+=2) {
                T father = selection.apply(population);
                T mother = selection.apply(population);
                while (mother == father) {
                    mother = selection.apply(population);
                }

                Chromosome[] children = father.crossover(mother);
                offsprings[i] = children[0];
                offsprings[i].mutate();
                if (i + 1 < size) {
                    offsprings[i + 1] = children[1];
                    offsprings[i + 1].mutate();
                }
            }

            System.arraycopy(offsprings, 0, population, 0, size);

            // Calculate the fitness of each chromosome.
            Arrays.stream(population).parallel().forEach(chromosome -> {
                if (chromosome instanceof LamarckianChromosome) {
                    LamarckianChromosome ch = (LamarckianChromosome) chromosome;
                    for (int j = 0; j < t; j++) {
                        ch.evolve();
                    }
                }

                chromosome.fitness();
            });

            Arrays.sort(population);
            best = population[size - 1];
            
            double avg = 0.0;
            for (Chromosome ch : population) {
                avg += ch.fitness();
            }
            avg /= size;

            logger.info(String.format("Generation %d, best fitness %G, average fitness %G", g, best.fitness(), avg));
        }

        return best;
    }
}
