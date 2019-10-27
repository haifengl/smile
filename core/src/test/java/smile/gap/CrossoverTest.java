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
public class CrossoverTest {

    public CrossoverTest() {
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
    public void testOnePoint() {
        System.out.println("one point");

        MathEx.setSeed(19650218); // to get repeatable results.

        byte[] father = {1,1,1,0,1,0,0,1,0,0,0};
        byte[] mother = {0,0,0,0,1,0,1,0,1,0,1};
        int length = father.length;

        BitString bs1 = new BitString(father, null);
        BitString bs2 = new BitString(mother, null);
        BitString[] result = Crossover.SINGLE_POINT.apply(bs1, bs2);

        assertEquals(length, result[0].length());
        assertEquals(length, result[1].length());

        byte[] child1 = {1,0,0,0,1,0,1,0,1,0,1};
        byte[] child2 = {0,1,1,0,1,0,0,1,0,0,0};
        for (int i = 0; i < length; i++) {
            assertEquals(child1[i], result[0].bits()[i]);
            assertEquals(child2[i], result[1].bits()[i]);
        }
    }

    @Test
    public void testTwoPoint() {
        System.out.println("two points");

        MathEx.setSeed(19650218); // to get repeatable results.

        byte[] father = {1,1,1,0,1,0,0,1,0,0,0};
        byte[] mother = {0,0,0,0,1,0,1,0,1,0,1};
        int length = father.length;

        BitString bs1 = new BitString(father, null);
        BitString bs2 = new BitString(mother, null);
        BitString[] result = Crossover.TWO_POINT.apply(bs1, bs2);

        assertEquals(length, result[0].length);
        assertEquals(length, result[1].length);

        byte[] child1 = {1,0,0,0,1,0,1,0,1,0,0};
        byte[] child2 = {0,1,1,0,1,0,0,1,0,0,1};
        for (int i = 0; i < length; i++) {
            assertEquals(child1[i], result[0].bits()[i]);
            assertEquals(child2[i], result[1].bits()[i]);
        }
    }

    @Test
    public void testUniform() {
        System.out.println("uniform");

        MathEx.setSeed(19650218); // to get repeatable results.

        byte[] father = {1,1,1,0,1,0,0,1,0,0,0};
        byte[] mother = {0,0,0,0,1,0,1,0,1,0,1};
        int length = father.length;

        BitString bs1 = new BitString(father, null);
        BitString bs2 = new BitString(mother, null);
        BitString[] result = Crossover.UNIFORM.apply(bs1, bs2);

        assertEquals(father.length, result[0].length);
        assertEquals(mother.length, result[1].length);

        byte[] child1 = {0,1,1,0,1,0,0,0,0,0,1};
        byte[] child2 = {1,0,0,0,1,0,1,1,1,0,0};
        for (int i = 0; i < length; i++) {
            assertEquals(child1[i], result[0].bits()[i]);
            assertEquals(child2[i], result[1].bits()[i]);
        }
    }
}