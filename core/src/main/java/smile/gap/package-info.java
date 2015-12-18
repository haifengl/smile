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

/**
 * Genetic algorithm and programming. A genetic algorithm (GA) is a search
 * heuristic that mimics the process of natural evolution. This heuristic
 * is routinely used to generate useful solutions to optimization and search
 * problems. Genetic algorithms belong to the larger class of evolutionary
 * algorithms (EA), which generate solutions to optimization problems using
 * techniques inspired by natural evolution, such as inheritance, mutation,
 * selection, and crossover.
 * <p>
 * In a genetic algorithm, a population of strings (called chromosomes),
 * which encode candidate solutions (called individuals) to an optimization
 * problem, evolves toward better solutions. Traditionally, solutions are
 * represented in binary as strings of 0s and 1s, but other encodings are
 * also possible. The evolution usually starts from a population of randomly
 * generated individuals and happens in generations. In each generation, the
 * fitness of every individual in the population is evaluated, multiple
 * individuals are stochastically selected from the current population (based
 * on their fitness), and modified (recombined and possibly randomly mutated)
 * to form a new population. The new population is then used in the next
 * iteration of the algorithm. Commonly, the algorithm terminates when either
 * a maximum number of generations has been produced, or a satisfactory fitness
 * level has been reached for the population. If the algorithm has terminated
 * due to a maximum number of generations, a satisfactory solution may or may
 * not have been reached.
 * <p>
 * A typical genetic algorithm requires:
 * <ol>
 * <li> a genetic representation of the solution domain. </li>
 * <li> a fitness function to evaluate the solution domain. </li>
 * </ol>
 * A standard representation of the solution is as an array of bits.
 * Arrays of other types and structures can be used in essentially the
 * same way. The main property that makes these genetic representations
 * convenient is that their parts are easily aligned due to their fixed
 * size, which facilitates simple crossover operations. Variable length
 * representations may also be used, but crossover implementation is more
 * complex in this case. Tree-like representations are explored in genetic
 * programming and graph-form representations are explored in evolutionary
 * programming.
 * <p>
 * The fitness function is defined over the genetic representation and
 * measures the quality of the represented solution. The fitness function
 * is always problem dependent. In some problems, it is hard
 * or even impossible to define the fitness expression; in these cases,
 * interactive genetic algorithms are used.
 * <p>
 * Once we have the genetic representation and the fitness function defined,
 * GA proceeds to initialize a population of solutions randomly, then improve
 * it through repetitive application of mutation, crossover, inversion and
 * selection operators.
 * <p>
 * The population size depends on the nature of the problem, but typically
 * contains several hundreds or thousands of possible solutions. Traditionally,
 * the population is generated randomly, covering the entire range of possible
 * solutions (the search space). Occasionally, the solutions may be "seeded"
 * in areas where optimal solutions are likely to be found.
 * <p>
 * During each successive generation, a proportion of the existing population
 * is selected to breed a new generation. Individual solutions are selected
 * through a fitness-based process, where fitter solutions (as measured by
 * a fitness function) are typically more likely to be selected. Certain
 * selection methods rate the fitness of each solution and preferentially
 * select the best solutions. Other methods rate only a random sample of the
 * population, as this process may be very time-consuming.
 * <p>
 * After selection, a second generation population of solutions is generated
 * through genetic operators: crossover (also called recombination), and/or
 * mutation. For each new solution to be produced, a pair of "parent" solutions
 * is selected for breeding from the pool selected previously. By producing a
 * "child" solution using the above methods of crossover and mutation, a new
 * solution is created which typically shares many of the characteristics of
 * its "parents". New parents are selected for each new child, and the process
 * continues until a new population of solutions of appropriate size is
 * generated. Generally the average fitness will have increased by this
 * procedure for the population.
 * <p>
 * Although Crossover and Mutation are known as the main genetic operators,
 * it is possible to use other operators such as regrouping,
 * colonization-extinction, or migration in genetic algorithms.
 * <p>
 * This generational process is repeated until a termination condition has been
 * reached. Common terminating conditions are:
 * <ul>
 * <li> A solution is found that satisfies minimum criteria </li>
 * <li> Fixed number of generations reached </li>
 * <li> The highest ranking solution's fitness is reaching or has reached
 * a plateau such that successive iterations no longer produce better results</li>
 * </ul>
 * 
 * @author Haifeng Li
 */
package smile.gap;
