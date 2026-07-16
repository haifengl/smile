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
package smile.regression;

import smile.io.Read;
import smile.io.Write;
import smile.datasets.CPU;
import smile.datasets.Longley;
import smile.math.MathEx;
import smile.validation.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class RidgeRegressionTest {
    public RidgeRegressionTest() {
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
    }

    @BeforeEach
    public void setUp() {
        MathEx.setSeed(19650218); // to get repeatable results.
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void testLongley() throws Exception {
        System.out.println("longley");
        var longley = new Longley();
        LinearModel model = RidgeRegression.fit(longley.formula(), longley.data(), 0.1);
        System.out.println(model);

        assertEquals(-1340.2198, model.intercept(), 1E-4);
        assertEquals( 0.0529, model.coefficients().get(0), 1E-4);
        assertEquals( 0.0120, model.coefficients().get(1), 1E-4);
        assertEquals( 0.0128, model.coefficients().get(2), 1E-4);
        assertEquals(-0.1637, model.coefficients().get(3), 1E-4);
        assertEquals( 0.7133, model.coefficients().get(4), 1E-4);
        assertEquals( 0.6019, model.coefficients().get(5), 1E-4);

        RegressionMetrics metrics = LOOCV.regression(longley.formula(), longley.data(),
                (f, x) -> RidgeRegression.fit(f, x, 0.1));

        System.out.println(metrics);
        assertEquals(1.732, metrics.rmse(), 1E-3);

        java.nio.file.Path temp = Write.object(model);
        Read.object(temp);
    }

    @Test
    public void testCPU() throws Exception {
        System.out.println("CPU");

        var cpu = new CPU();
        LinearModel model = RidgeRegression.fit(cpu.formula(), cpu.data(), 0.1);
        System.out.println(model);

        RegressionValidations<LinearModel> result = CrossValidation.regression(10, cpu.formula(), cpu.data(),
                (f, x) -> RidgeRegression.fit(f, x, 0.1));

        System.out.println(result);
        assertEquals(55.8048, result.avg().rmse(), 1E-4);
    }
}
