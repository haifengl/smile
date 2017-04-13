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
package smile.gap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import smile.math.Math;
import smile.util.MulticoreExecutor;

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
    private static final Logger logger = LoggerFactory.getLogger(GeneticAlgorithm.class);

    /**
     * The way to select chromosomes from the population as parents to crossover.
     */
    public enum Selection {

        /**
         * Roulette Wheel Selection, also called fitness proportionate selection.
         * Parents are selected by the ratio of its fitness to the fitness of
         * other members of the current population.
         * */
        ROULETTE_WHEEL,
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
        SCALED_ROULETTE_WHEEL,
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
        RANK,
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
         */
        TOURNAMENT
    }

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
    private Selection selection = Selection.TOURNAMENT;
    /**
     * The number of best chromosomes to copy to new population. When creating
     * new population by crossover and mutation, we have a big chance, that we
     * will loose the best chromosome. Elitism first copies the best chromosome
     * (or a few best chromosomes) to new population. The rest is done in
     * classical way. Elitism can very rapidly increase performance of GA,
     * because it prevents losing the best found solution.
     */
    private int elitism = 1;
    /**
     * The tournament size in tournament selection.
     */
    private int tournamentSize = 3;
    /**
     * The probability of best player wins in tournament selection.
     */
    private double tournamentProbability = 0.95;
    /**
     * The number of iterations of local search in case Lamarckian algorithm.
     */
    private int t = 5;
    /**
     * Parallel tasks to evaluate the fitness of population.
     */
    private List<Task> tasks = new ArrayList<>();
    
    /**
     * Constructor. The default selection strategy is tournament selection
     * of size 2.
     * Elitism is also used (the best chromosome is copied to next generation).
     * @param seeds the initial population, which is usually randomly generated.
     */
    public GeneticAlgorithm(T[] seeds) {
        this(seeds, Selection.TOURNAMENT);
    }

    /**
     * Constructor. By default, elitism is used (the best chromosome is copied
     * to next generation).
     * @param selection the selection strategy.
     * @param seeds the initial population, which is usually randomly generated.
     */
    public GeneticAlgorithm(T[] seeds, Selection selection) {
        this.size = seeds.length;
        this.population = seeds;
        this.selection = selection;
        for (int i = 0; i < size; i++) {
            tasks.add(new Task(i));
        }
    }

    /**
     * Returns the population of current generation.
     */
    public T[] population() {
        return population;
    }

    /**
     * Sets the number of best chromosomes to copy to new population. Sets the
     * value to zero to disable elitism selection. When creating new population
     * by crossover and mutation, we have a big chance, that we will loose the
     * best chromosome. Elitism first copies the best chromosome (or a few best
     * chromosomes) to new population. The rest is done in classical way.
     * Elitism can very rapidly increase performance of GA, because it prevents
     * losing the best found solution.
     */
    public GeneticAlgorithm<T> setElitism(int elitism) {
        if (elitism < 0 || elitism >= size) {
            throw new IllegalArgumentException("Invalid elitism: " + elitism);
        }
        this.elitism = elitism;
        return this;
    }

    /**
     * Returns the number of best chromosomes to copy to new population.
     */
    public int getElitism() {
        return elitism;
    }

    /**
     * Set the tournament size and the best-player-wins probability in
     * tournament selection.
     * @param size the size of tournament pool.
     * @param p the best-player-wins probability.
     */
    public GeneticAlgorithm<T> setTournament(int size, double p) {
        if (size < 1) {
            throw new IllegalArgumentException("Invalid tournament size: " + size);
        }
        
        if (p < 0.5 || p > 1.0) {
            throw new IllegalArgumentException("Invalid best-player-wins probability: " + p);
        }
        
        tournamentSize = size;
        tournamentProbability = p;
        return this;
    }

    /**
     * Returns the tournament size in tournament selection.
     */
    public int getTournamentSize() {
        return tournamentSize;
    }

    /**
     * Returns the best-player-wins probability in tournament selection.
     */
    public double getTournamentProbability() {
        return tournamentProbability;
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
        try {
            MulticoreExecutor.run(tasks);
        } catch (Exception ex) {
            logger.error("Failed to run Genetic Algorithm on multi-core", ex);

            for (Task task : tasks) {
                task.call();
            }
        }

        Arrays.sort(population);
        T best = population[size-1];

        Chromosome[] offsprings = new Chromosome[size];
        for (int g = 1; g <= generation && best.fitness() < threshold; g++) {
            for (int i = 0; i < elitism; i++) {
                offsprings[i] = population[size-i-1];
            }

            for (int i = elitism; i < size; i+=2) {
                T father = select(population);
                T mother = select(population);
                while (mother == father) {
                    mother = select(population);
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

            try {
                MulticoreExecutor.run(tasks);
            } catch (Exception ex) {
                logger.error("Failed to run Genetic Algorithm on multi-core", ex);

                for (Task task : tasks) {
                    task.call();
                }
            }

            Arrays.sort(population);
            best = population[size - 1];
            
            double avg = 0.0;
            for (Chromosome ch : population) {
                avg += ch.fitness();
            }
            avg /= size;

            logger.info(String.format("Genetic Algorithm: generation %d, best fitness %g, average fitness %g", g, best.fitness(), avg));
        }

        return best;
    }

    /**
     * Select a chromosome with replacement from the population based on their
     * fitness. Note that the population should be in ascending order in terms
     * of fitness.
     */
    @SuppressWarnings("unchecked")
    private T select(T[] population) {
        double worst = population[0].fitness();
        double[] fitness = new double[size];
        switch (selection) {
            case ROULETTE_WHEEL:
                if (worst > 0.0) {
                    worst = 0.0;
                }
                
                // In Roulete wheel selection, we don't do such scaling in
                // general. However, in case of negative fitness socres,
                // we need scale them to positive.
                for (int i = 0; i < size; i++) {
                    fitness[i] = population[i].fitness() - worst;
                }

                Math.unitize1(fitness);

                return population[Math.random(fitness)];

            case SCALED_ROULETTE_WHEEL:
                for (int i = 0; i < size; i++) {
                    fitness[i] = population[i].fitness() - worst;
                }

                Math.unitize1(fitness);

                return population[Math.random(fitness)];

            case RANK:
                for (int i = 0; i < size; i++) {
                    fitness[i] = i + 1;
                }

                Math.unitize1(fitness);

                return population[Math.random(fitness)];

            case TOURNAMENT:
                Chromosome[] pool = new Chromosome[tournamentSize];
                for (int i = 0; i < tournamentSize; i++) {
                    pool[i] = population[Math.randomInt(size)];
                }

                Arrays.sort(pool);
                for (int i = 1; i <= tournamentSize; i++) {
                    double p = Math.random();
                    if (p < tournamentProbability) {
                        return (T) pool[tournamentSize - i];
                    }
                }
                
                return (T) pool[tournamentSize - 1];
        }

        return null;
    }

    /**
     * Adapter for calculating fitness in thread pool.
     */
    class Task implements Callable<Task> {

        /**
         * The index of chromosome in the population.
         */
        final int i;

        Task(int i) {
            this.i = i;
        }

        @Override
        public Task call() {
            Chromosome chromosome = population[i];
            if (chromosome instanceof LamarckianChromosome) {
                LamarckianChromosome ch = (LamarckianChromosome) chromosome;
                for (int j = 0; j < t; j++) {
                    ch.evolve();
                }
            }
            
            chromosome.fitness();
            return this;
        }
    }
}
