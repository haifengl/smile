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
package smile.util.function;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class RootTest {

    public RootTest() {
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
    public void testBrent() {
        System.out.println("Brent");
        Function func = x -> x * x * x + x * x - 5 * x + 3;
        double result = func.root(-4, -2, 1E-7, 500);
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
        double result = func.root(-4, -2, 1E-7, 500);
        assertEquals(-3, result, 1E-7);
    }
}
