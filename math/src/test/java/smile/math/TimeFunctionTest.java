/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.math;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Time;

import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class TimeFunctionTest {

    public TimeFunctionTest() {
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
    public void testConstant() {
        System.out.println("Constant");
        TimeFunction t = TimeFunction.constant(0.1);
        assertEquals(0.1, t.apply(0), 1E-7);
        assertEquals(0.1, t.apply(1), 1E-7);
        assertEquals(0.1, t.apply(2), 1E-7);
        assertEquals(0.1, t.apply(10000), 1E-7);
    }

    @Test
    public void testPiecewise() {
        System.out.println("Piecewise");
        int[] boundaries = {5, 10};
        double[] values = {0.1, 0.01, 0.001};
        TimeFunction t = TimeFunction.piecewise(boundaries, values);
        assertEquals(0.1, t.apply(0), 1E-7);
        assertEquals(0.1, t.apply(1), 1E-7);
        assertEquals(0.1, t.apply(4), 1E-7);
        assertEquals(0.1, t.apply(5), 1E-7);
        assertEquals(0.01, t.apply(6), 1E-7);
        assertEquals(0.01, t.apply(7), 1E-7);
        assertEquals(0.01, t.apply(9), 1E-7);
        assertEquals(0.01, t.apply(10), 1E-7);
        assertEquals(0.001, t.apply(11), 1E-7);
        assertEquals(0.001, t.apply(12), 1E-7);
    }

    @Test
    public void testLinear() {
        System.out.println("Linear");
        TimeFunction t = TimeFunction.linear(0.1, 9, 0.01);
        assertEquals(0.1, t.apply(0), 1E-7);
        assertEquals(0.09, t.apply(1), 1E-7);
        assertEquals(0.08, t.apply(2), 1E-7);
        assertEquals(0.02, t.apply(8), 1E-7);
        assertEquals(0.01, t.apply(9), 1E-7);
        assertEquals(0.01, t.apply(10), 1E-7);
        assertEquals(0.01, t.apply(11), 1E-7);
        assertEquals(0.01, t.apply(12), 1E-7);
    }

    @Test
    public void testPolynomial() {
        System.out.println("Polynomial");
        TimeFunction t = TimeFunction.polynomial(1, 0.1, 9, 0.01, true);
        assertEquals(0.1, t.apply(0), 1E-7);
        assertEquals(0.09, t.apply(1), 1E-7);
        assertEquals(0.08, t.apply(2), 1E-7);
        assertEquals(0.02, t.apply(8), 1E-7);
        assertEquals(0.01, t.apply(9), 1E-7);
        assertEquals(0.05, t.apply(10), 1E-7);
        assertEquals(0.045, t.apply(11), 1E-7);
        assertEquals(0.04, t.apply(12), 1E-7);
        assertEquals(0.015, t.apply(17), 1E-7);
        assertEquals(0.01, t.apply(18), 1E-7);
        assertEquals(0.0366666, t.apply(19), 1E-7);
        assertEquals(0.0333333, t.apply(20), 1E-7);
        assertEquals(0.03, t.apply(21), 1E-7);
        assertEquals(0.0133333, t.apply(26), 1E-7);
        assertEquals(0.01, t.apply(27), 1E-7);
        assertEquals(0.03, t.apply(28), 1E-7);
    }

    @Test
    public void testInverseNeuralGas() {
        System.out.println("Inverse for Neural Gas");
        TimeFunction t = TimeFunction.inverse(0.1, 9);
        assertEquals(0.1, t.apply(0), 1E-7);
        assertEquals(0.09, t.apply(1), 1E-7);
        assertEquals(0.0818182, t.apply(2), 1E-7);
        assertEquals(0.075, t.apply(3), 1E-7);
        assertEquals(0.05, t.apply(9), 1E-7);
        assertEquals(0.0473684, t.apply(10), 1E-7);
        assertEquals(0.045, t.apply(11), 1E-7);
    }

    @Test
    public void testInverseNeuralNetwork() {
        System.out.println("Inverse for Neural Network");
        TimeFunction t = TimeFunction.inverse(0.1, 10, 0.1);
        assertEquals(0.1, t.apply(0), 1E-7);
        assertEquals(0.0990099, t.apply(1), 1E-7);
        assertEquals(0.0980392, t.apply(2), 1E-7);
        assertEquals(0.0970873, t.apply(3), 1E-7);
        assertEquals(0.0917431, t.apply(9), 1E-7);
        assertEquals(0.0909091, t.apply(10), 1E-7);
        assertEquals(0.0900901, t.apply(11), 1E-7);
        assertEquals(0.0892857, t.apply(12), 1E-7);
    }

    @Test
    public void testInverseStaircase() {
        System.out.println("Staircase Inverse for Neural Network");
        TimeFunction t = TimeFunction.inverse(0.1, 10, 0.1, true);
        assertEquals(0.1, t.apply(0), 1E-7);
        assertEquals(0.1, t.apply(1), 1E-7);
        assertEquals(0.1, t.apply(2), 1E-7);
        assertEquals(0.1, t.apply(3), 1E-7);
        assertEquals(0.1, t.apply(9), 1E-7);
        assertEquals(0.0909091, t.apply(10), 1E-7);
        assertEquals(0.0909091, t.apply(11), 1E-7);
        assertEquals(0.0909091, t.apply(19), 1E-7);
        assertEquals(0.0833333, t.apply(20), 1E-7);
        assertEquals(0.0833333, t.apply(21), 1E-7);
    }

    @Test
    public void testExpNeuralGas() {
        System.out.println("Exponential for Neural Gas");
        TimeFunction t = TimeFunction.exp(0.1, 10);
        assertEquals(0.1, t.apply(0), 1E-7);
        assertEquals(0.0904837, t.apply(1), 1E-7);
        assertEquals(0.0818731, t.apply(2), 1E-7);
        assertEquals(0.0740818, t.apply(3), 1E-7);
        assertEquals(0.0406569, t.apply(9), 1E-7);
        assertEquals(0.0367879, t.apply(10), 1E-7);
        assertEquals(0.0332871, t.apply(11), 1E-7);
    }

    @Test
    public void testExpEnd() {
        System.out.println("Exponential to an end learning rate");
        TimeFunction t = TimeFunction.exp(0.1, 10, 0.001);
        assertEquals(0.1, t.apply(0), 1E-7);
        assertEquals(0.0630957, t.apply(1), 1E-7);
        assertEquals(0.0398107, t.apply(2), 1E-7);
        assertEquals(0.0251188, t.apply(3), 1E-7);
        assertEquals(0.0015848, t.apply(9), 1E-7);
        assertEquals(0.001, t.apply(10), 1E-7);
        assertEquals(0.001, t.apply(11), 1E-7);
        assertEquals(0.001, t.apply(12), 1E-7);
    }

    @Test
    public void testExpStaircase() {
        System.out.println("Staircase Exponential for Neural Network");
        TimeFunction t = TimeFunction.exp(0.1, 10, 0.9, true);
        assertEquals(0.1, t.apply(0), 1E-7);
        assertEquals(0.1, t.apply(1), 1E-7);
        assertEquals(0.1, t.apply(2), 1E-7);
        assertEquals(0.1, t.apply(3), 1E-7);
        assertEquals(0.1, t.apply(9), 1E-7);
        assertEquals(0.09, t.apply(10), 1E-7);
        assertEquals(0.09, t.apply(11), 1E-7);
        assertEquals(0.09, t.apply(19), 1E-7);
        assertEquals(0.081, t.apply(20), 1E-7);
        assertEquals(0.081, t.apply(21), 1E-7);
    }

    @Test
    public void testExpNonStaircase() {
        System.out.println("Exponential for Neural Network");
        TimeFunction t = TimeFunction.exp(0.1, 10, 0.9, false);
        assertEquals(0.1, t.apply(0), 1E-7);
        assertEquals(0.0989519, t.apply(1), 1E-7);
        assertEquals(0.0979148, t.apply(2), 1E-7);
        assertEquals(0.0968886, t.apply(3), 1E-7);
        assertEquals(0.0909532, t.apply(9), 1E-7);
        assertEquals(0.09, t.apply(10), 1E-7);
        assertEquals(0.0890567, t.apply(11), 1E-7);
        assertEquals(0.0818579, t.apply(19), 1E-7);
        assertEquals(0.081, t.apply(20), 1E-7);
        assertEquals(0.0801511, t.apply(21), 1E-7);
    }

    @Test(expected = Test.None.class)
    public void testParse() {
        System.out.println("parse");
        System.out.println(TimeFunction.of("0.01"));
        System.out.println(TimeFunction.of("piecewise([5, 10], [0.1, 0.01, 0.001])"));
        System.out.println(TimeFunction.of("linear(0.1, 9, 0.01)"));
        System.out.println(TimeFunction.of("polynomial(0.5, 0.1, 9, 0.01)"));
        System.out.println(TimeFunction.of("polynomial(0.5, 0.1, 9, 0.01, true)"));
        System.out.println(TimeFunction.of("inverse(0.1, 10, 0.1)"));
        System.out.println(TimeFunction.of("inverse(0.1, 10, 0.1, true)"));
        System.out.println(TimeFunction.of("exp(0.1, 10, 0.9)"));
        System.out.println(TimeFunction.of("exp(0.1, 10, 0.9, false)"));
    }

    @Test(expected = Test.None.class)
    public void testParseToString() {
        System.out.println("parse");
        int[] boundaries = {5, 10};
        double[] values = {0.1, 0.01, 0.001};

        TimeFunction.of(TimeFunction.constant(0.01).toString());
        TimeFunction.of(TimeFunction.piecewise(boundaries, values).toString());
        TimeFunction.of(TimeFunction.linear(0.1, 9, 0.01).toString());
        TimeFunction.of(TimeFunction.polynomial(2, 0.1, 9, 0.01).toString());
        TimeFunction.of(TimeFunction.polynomial(2, 0.1, 9, 0.01, true).toString());
        TimeFunction.of(TimeFunction.inverse(0.1, 10).toString());
        TimeFunction.of(TimeFunction.inverse(0.1, 10, 0.1).toString());
        TimeFunction.of(TimeFunction.inverse(0.1, 10, 0.1, true).toString());
        TimeFunction.of(TimeFunction.exp(0.1, 10).toString());
        TimeFunction.of(TimeFunction.exp(0.1, 10, 0.9).toString());
        TimeFunction.of(TimeFunction.exp(0.1, 10, 0.9, false).toString());
    }
}