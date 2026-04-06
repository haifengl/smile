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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import smile.math.MathEx;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Selection} strategies.
 * @author Haifeng Li
 */
public class SelectionTest {

    /** Maps a bit string to a distinct non-negative fitness (ascending order with natural bit order). */
    private static double binaryFitness(BitString bs) {
        double v = 0.0;
        for (byte b : bs.bits()) {
            v = v * 2 + b;
        }
        return v;
    }

    private static BitString[] sortedPopulationOfThree() {
        Fitness<BitString> f = SelectionTest::binaryFitness;
        BitString[] pop = new BitString[3];
        pop[0] = new BitString(new byte[] {0, 0, 1}, f, Crossover.UNIFORM, 1.0, 0.0);
        pop[1] = new BitString(new byte[] {0, 1, 0}, f, Crossover.UNIFORM, 1.0, 0.0);
        pop[2] = new BitString(new byte[] {1, 0, 0}, f, Crossover.UNIFORM, 1.0, 0.0);
        Arrays.sort(pop);
        return pop;
    }

    @Test
    public void rankSelectionReturnsPopulationMember() {
        // Given
        MathEx.setSeed(19650218);
        BitString[] pop = sortedPopulationOfThree();
        Selection selection = Selection.Rank();

        // When
        BitString chosen = selection.apply(pop);

        // Then
        assertNotNull(chosen);
        assertTrue(chosen == pop[0] || chosen == pop[1] || chosen == pop[2]);
    }

    @Test
    public void rouletteWheelSelectionReturnsPopulationMember() {
        // Given
        MathEx.setSeed(19650218);
        BitString[] pop = sortedPopulationOfThree();
        Selection selection = Selection.RouletteWheel();

        // When
        BitString chosen = selection.apply(pop);

        // Then
        assertNotNull(chosen);
        assertTrue(chosen == pop[0] || chosen == pop[1] || chosen == pop[2]);
    }

    @Test
    public void scaledRouletteWheelSelectionReturnsPopulationMember() {
        // Given
        MathEx.setSeed(19650218);
        BitString[] pop = sortedPopulationOfThree();
        Selection selection = Selection.ScaledRouletteWheel();

        // When
        BitString chosen = selection.apply(pop);

        // Then
        assertNotNull(chosen);
        assertTrue(chosen == pop[0] || chosen == pop[1] || chosen == pop[2]);
    }

    @Test
    public void tournamentSelectionReturnsPopulationMember() {
        // Given
        MathEx.setSeed(19650218);
        BitString[] pop = sortedPopulationOfThree();
        Selection selection = Selection.Tournament(3, 0.95);

        // When
        BitString chosen = selection.apply(pop);

        // Then
        assertNotNull(chosen);
        assertTrue(chosen == pop[0] || chosen == pop[1] || chosen == pop[2]);
    }

    @Test
    public void tournamentSelectionCoversPopulationWithManyDraws() {
        // Given
        MathEx.setSeed(42);
        BitString[] pop = sortedPopulationOfThree();
        Selection selection = Selection.Tournament(2, 0.5);
        Set<BitString> seen = new HashSet<>();

        // When
        for (int i = 0; i < 200; i++) {
            seen.add(selection.apply(pop));
        }

        // Then
        assertEquals(3, seen.size());
    }

    @Test
    public void rouletteWheelHandlesNegativeFitness() {
        // Given
        MathEx.setSeed(1);
        Fitness<BitString> f =
                bs -> binaryFitness(bs) - 2.0; // some negative values
        BitString[] pop = new BitString[3];
        pop[0] = new BitString(new byte[] {0, 0, 0}, f, Crossover.UNIFORM, 1.0, 0.0);
        pop[1] = new BitString(new byte[] {0, 0, 1}, f, Crossover.UNIFORM, 1.0, 0.0);
        pop[2] = new BitString(new byte[] {1, 1, 1}, f, Crossover.UNIFORM, 1.0, 0.0);
        Arrays.sort(pop);
        Selection selection = Selection.RouletteWheel();

        // When
        BitString chosen = selection.apply(pop);

        // Then
        assertNotNull(chosen);
    }
}
