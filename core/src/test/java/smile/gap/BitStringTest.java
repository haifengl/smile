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

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.math.MathEx;

import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class BitStringTest {

    public BitStringTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
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