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

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class GeneticAlgorithmTest {
    
    public GeneticAlgorithmTest() {
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
    }
    
    @BeforeEach
    public void setUp() {
    }
    
    @AfterEach
    public void tearDown() {
    }

    static class Knapnack implements Fitness<BitString> {

        int limit = 9; // weight limit
        int[] weight = {2, 3, 4, 4, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
        double[] reward = {6, 6, 6, 5, 1.3, 1.2, 1.1, 1.0, 1.1, 1.3, 1.0, 1.0, 0.9, 0.8, 0.6};

        @Override
        public double score(BitString chromosome) {
            double wsum = 0.0;
            double rew = 0.0;
            byte[] bits = chromosome.bits();
            for (int i = 0; i < weight.length; i++) {
                if (bits[i] == 1) {
                    wsum += weight[i];
                    rew += reward[i];
                }
            }
            
            // subtract penalty for exceeding weight
            if (wsum > limit) {
                rew -= 5 * (wsum - limit);
            }

            return rew;
        }
    }

    @Test
    public void test() {
        System.out.println("Genetic Algorithm");
        BitString[] seeds = new BitString[100];
        
        // The mutation parameters are set higher than usual to prevent premature convergence. 
        for (int i = 0; i < seeds.length; i++) {
            seeds[i] = new BitString(15, new Knapnack(), Crossover.UNIFORM, 1.0, 0.2);
        }
        
        GeneticAlgorithm<BitString> instance = new GeneticAlgorithm<>(seeds, Selection.Tournament(3, 0.95), 2);

        BitString result = instance.evolve(1000, 18);
        assertEquals(18, result.fitness(), 1E-7);

        int[] best = {1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        for (int i = 0; i < best.length; i++) {
            assertEquals(best[i], result.bits()[i]);
        }
    }

    @Test
    public void invalidElitismThrows() {
        // Given
        BitString[] seeds = new BitString[2];
        seeds[0] = new BitString(3, bs -> 0.0);
        seeds[1] = new BitString(3, bs -> 1.0);
        // When / Then
        assertThrows(IllegalArgumentException.class, () -> new GeneticAlgorithm<>(seeds, Selection.Rank(), -1));
        assertThrows(IllegalArgumentException.class, () -> new GeneticAlgorithm<>(seeds, Selection.Rank(), 2));
    }

    @Test
    public void invalidGenerationCountThrows() {
        // Given
        BitString[] seeds = new BitString[2];
        seeds[0] = new BitString(3, bs -> 0.0);
        seeds[1] = new BitString(3, bs -> 1.0);
        GeneticAlgorithm<BitString> ga = new GeneticAlgorithm<>(seeds, Selection.Rank(), 0);
        // When / Then
        assertThrows(IllegalArgumentException.class, () -> ga.evolve(0));
        assertThrows(IllegalArgumentException.class, () -> ga.evolve(0, 1.0));
    }

    @Test
    public void negativeLocalSearchStepsThrows() {
        // Given
        BitString[] seeds = new BitString[2];
        seeds[0] = new BitString(3, bs -> 0.0);
        seeds[1] = new BitString(3, bs -> 1.0);
        GeneticAlgorithm<BitString> ga = new GeneticAlgorithm<>(seeds, Selection.Rank(), 0);
        // When / Then
        assertThrows(IllegalArgumentException.class, () -> ga.setLocalSearchSteps(-1));
    }

    @Test
    public void setLocalSearchStepsIsFluentAndGetterMatches() {
        // Given
        BitString[] seeds = new BitString[2];
        seeds[0] = new BitString(3, bs -> 0.0);
        seeds[1] = new BitString(3, bs -> 1.0);
        GeneticAlgorithm<BitString> ga = new GeneticAlgorithm<>(seeds, Selection.Rank(), 0);
        // When
        GeneticAlgorithm<BitString> same = ga.setLocalSearchSteps(0);
        // Then
        assertSame(ga, same);
        assertEquals(0, ga.getLocalSearchSteps());
        ga.setLocalSearchSteps(7);
        assertEquals(7, ga.getLocalSearchSteps());
    }

    @Test
    public void populationReturnsSeedsArray() {
        // Given
        BitString[] seeds = new BitString[2];
        seeds[0] = new BitString(3, bs -> 0.0);
        seeds[1] = new BitString(3, bs -> 1.0);
        GeneticAlgorithm<BitString> ga = new GeneticAlgorithm<>(seeds, Selection.Rank(), 0);
        // When / Then
        assertSame(seeds, ga.population());
    }

    @Test
    public void populationSizeOneThrows() {
        // Given
        BitString[] seeds = new BitString[1];
        seeds[0] = new BitString(4, bs -> 1.0, Crossover.UNIFORM, 1.0, 0.0);
        // When / Then
        assertThrows(
                IllegalArgumentException.class,
                () -> new GeneticAlgorithm<>(seeds, Selection.Tournament(1, 0.5), 0));
    }

    @Test
    public void evolveStopsWhenFitnessReachesThreshold() {
        // Given
        BitString[] seeds = new BitString[4];
        for (int i = 0; i < seeds.length; i++) {
            seeds[i] = new BitString(2, bs -> 1.0, Crossover.UNIFORM, 1.0, 0.0);
        }
        GeneticAlgorithm<BitString> ga = new GeneticAlgorithm<>(seeds, Selection.Rank(), 0);
        // When
        BitString best = ga.evolve(1000, 1.0);
        // Then
        assertEquals(1.0, best.fitness(), 1e-9);
    }

    static final class CountingLamarckian implements LamarckianChromosome<CountingLamarckian> {
        private final AtomicInteger evolveCalls = new AtomicInteger();
        private double fitness;

        CountingLamarckian(double fitness) {
            this.fitness = fitness;
        }

        int evolveCalls() {
            return evolveCalls.get();
        }

        @Override
        public void evolve() {
            evolveCalls.incrementAndGet();
            fitness += 0.01;
        }

        @Override
        public double fitness() {
            return fitness;
        }

        @Override
        public int compareTo(Chromosome<CountingLamarckian> o) {
            return Double.compare(fitness(), o.fitness());
        }

        @Override
        public CountingLamarckian newInstance() {
            return new CountingLamarckian(fitness);
        }

        @Override
        public CountingLamarckian[] crossover(CountingLamarckian other) {
            double m = (fitness + other.fitness) / 2.0;
            return new CountingLamarckian[] {new CountingLamarckian(m), new CountingLamarckian(m)};
        }

        @Override
        public void mutate() {
            // no-op
        }
    }

    @Test
    public void lamarckianChromosomesReceiveLocalSearchDuringFitnessPasses() {
        // Given
        CountingLamarckian[] seeds = new CountingLamarckian[2];
        seeds[0] = new CountingLamarckian(0.0);
        seeds[1] = new CountingLamarckian(0.5);
        GeneticAlgorithm<CountingLamarckian> ga =
                new GeneticAlgorithm<>(seeds, Selection.Tournament(2, 0.9), 0).setLocalSearchSteps(3);
        // When
        ga.evolve(1, Double.POSITIVE_INFINITY);
        // Then (initial fitness pass applies t local-search steps per individual)
        assertTrue(seeds[0].evolveCalls() >= 3);
        assertTrue(seeds[1].evolveCalls() >= 3);
    }

    @Test
    public void zeroLocalSearchStepsSkipsLamarckianEvolve() {
        // Given
        CountingLamarckian[] seeds = new CountingLamarckian[2];
        seeds[0] = new CountingLamarckian(0.0);
        seeds[1] = new CountingLamarckian(0.5);
        GeneticAlgorithm<CountingLamarckian> ga =
                new GeneticAlgorithm<>(seeds, Selection.Tournament(2, 0.9), 0).setLocalSearchSteps(0);
        // When
        ga.evolve(1, Double.POSITIVE_INFINITY);
        // Then
        assertEquals(0, seeds[0].evolveCalls());
        assertEquals(0, seeds[1].evolveCalls());
    }
}
