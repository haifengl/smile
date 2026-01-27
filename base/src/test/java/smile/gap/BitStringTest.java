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
}