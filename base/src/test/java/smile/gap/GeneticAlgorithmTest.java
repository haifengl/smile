/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.gap;

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
}
