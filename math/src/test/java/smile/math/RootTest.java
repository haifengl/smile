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

import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class RootTest {

    public RootTest() {
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
    public void testBrent() {
        System.out.println("Brent");
        double result = Root.find(x -> x * x * x + x * x - 5 * x + 3, -4, -2, 1E-7, 500);
        assertEquals(-3, result, 1E-7);
    }

    @Test
    public void testNewton() {
        System.out.println("Newton");
        Function func = new DifferentiableFunction() {

            @Override
            public double f(double x) {
                return x * x * x + x * x - 5 * x + 3;
            }

            @Override
            public double g(double x) {
                return 3 * x * x + 2 * x - 5;
            }
        };
        double result = Root.find(func, -4, -2, 1E-7, 500);
        assertEquals(-3, result, 1E-7);
    }
}
