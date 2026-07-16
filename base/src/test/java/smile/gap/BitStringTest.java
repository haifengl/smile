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
import smile.math.MathEx;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class BitStringTest {

    public BitStringTest() {
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

    @Test
    public void testNewInstance() {
        System.out.println("newInstance");

        byte[] father = {1,1,1,0,1,0,0,1,0,0,0};
        int length = father.length;
        BitString instance = new BitString(father, null, Crossover.SINGLE_POINT, 1.0, 0.0);
        BitString result = instance.newInstance();

        assertEquals(length, result.length());

        boolean same = true;
        for (int i = 0; i < length; i++) {
            if (father[i] != result.bits()[i]) {
                same = false;
            }
        }
        assertFalse(same);
    }

    @Test
    public void testMutate() {
        System.out.println("mutate");

        MathEx.setSeed(19650218); // to get repeatable results.

        byte[] father = {1,1,1,0,1,0,0,1,0,0,0};
        BitString instance = new BitString(father.clone(), null, Crossover.SINGLE_POINT, 1.0, 0.1);
        instance.mutate();

        byte[] mutant = {1,1,1,1,1,0,0,1,0,1,0};
        for (int i = 0; i < father.length; i++) {
            assertEquals(mutant[i], instance.bits()[i]);
        }
    }

    @Test
    public void invalidLengthThrows() {
        // Given
        Fitness<BitString> f = bs -> 0.0;
        // When / Then
        assertThrows(IllegalArgumentException.class, () -> new BitString(0, f));
    }

    @Test
    public void invalidCrossoverRateThrows() {
        // Given
        Fitness<BitString> f = bs -> 0.0;
        // When / Then
        assertThrows(
                IllegalArgumentException.class,
                () -> new BitString(4, f, Crossover.SINGLE_POINT, 1.01, 0.01));
    }

    @Test
    public void invalidMutationRateThrows() {
        // Given
        Fitness<BitString> f = bs -> 0.0;
        // When / Then
        assertThrows(
                IllegalArgumentException.class,
                () -> new BitString(4, f, Crossover.SINGLE_POINT, 0.9, -0.01));
    }

    @Test
    public void fitnessIsCached() {
        // Given
        AtomicInteger scoreCalls = new AtomicInteger();
        Fitness<BitString> f =
                bs -> {
                    scoreCalls.incrementAndGet();
                    return bs.bits()[0];
                };
        BitString bs = new BitString(new byte[] {1}, f, Crossover.UNIFORM, 1.0, 0.0);
        // When
        double a = bs.fitness();
        double b = bs.fitness();
        // Then
        assertEquals(1.0, a);
        assertEquals(1.0, b);
        assertEquals(1, scoreCalls.get());
    }

    @Test
    public void compareToOrdersByFitness() {
        // Given
        Fitness<BitString> f = BitStringTest::binaryFitness;
        BitString low = new BitString(new byte[] {0, 0, 1}, f, Crossover.UNIFORM, 1.0, 0.0);
        BitString high = new BitString(new byte[] {1, 0, 0}, f, Crossover.UNIFORM, 1.0, 0.0);
        low.fitness();
        high.fitness();
        // When / Then
        assertTrue(low.compareTo(high) < 0);
        assertTrue(high.compareTo(low) > 0);
        assertEquals(0, low.compareTo(low));
    }

    @Test
    public void toStringConcatenatesBits() {
        // Given
        BitString bs = new BitString(new byte[] {1, 0, 1}, null, Crossover.UNIFORM, 1.0, 0.0);
        // When / Then
        assertEquals("101", bs.toString());
    }

    @Test
    public void newInstanceWithBitsCopiesPatternAndKeepsOperators() {
        // Given
        byte[] pattern = {1, 0, 1, 1};
        Fitness<BitString> f = bs -> 1.0;
        BitString parent = new BitString(new byte[] {0, 0, 0, 0}, f, Crossover.TWO_POINT, 0.8, 0.02);
        BitString child = parent.newInstance(pattern);
        BitString mate = new BitString(new byte[] {0, 0, 0, 0}, f, Crossover.TWO_POINT, 0.8, 0.02);
        // When
        MathEx.setSeed(1);
        BitString[] kids = child.crossover(mate);
        // Then
        assertArrayEquals(pattern, child.bits());
        assertEquals(4, child.length());
        assertEquals(2, kids.length);
        assertEquals(4, kids[0].length());
        assertEquals(4, kids[1].length());
    }

    private static double binaryFitness(BitString bs) {
        double v = 0.0;
        for (byte b : bs.bits()) {
            v = v * 2 + b;
        }
        return v;
    }
}