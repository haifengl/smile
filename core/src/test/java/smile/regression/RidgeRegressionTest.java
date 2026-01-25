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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
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

        assertEquals(-1.354007e+03, model.intercept(), 1E-3);
        assertEquals(5.457700e-02, model.coefficients().get(0), 1E-7);
        assertEquals(1.198440e-02, model.coefficients().get(1), 1E-7);
        assertEquals(1.261978e-02, model.coefficients().get(2), 1E-7);
        assertEquals(-1.856041e-01, model.coefficients().get(3), 1E-7);
        assertEquals(7.218054e-01, model.coefficients().get(4), 1E-7);
        assertEquals(5.884884e-01, model.coefficients().get(5), 1E-7);

        RegressionMetrics metrics = LOOCV.regression(longley.formula(), longley.data(),
                (f, x) -> RidgeRegression.fit(f, x, 0.1));

        System.out.println(metrics);
        assertEquals(1.7288188, metrics.rmse(), 1E-7);

        java.nio.file.Path temp = Write.object(model);
        Read.object(temp);
    }

    @Test
    public void testCPU() throws Exception {
        System.out.println("CPU");

        MathEx.setSeed(19650218); // to get repeatable results.
        var cpu = new CPU();
        LinearModel model = RidgeRegression.fit(cpu.formula(), cpu.data(), 0.1);
        System.out.println(model);

        RegressionValidations<LinearModel> result = CrossValidation.regression(10, cpu.formula(), cpu.data(),
                (f, x) -> RidgeRegression.fit(f, x, 0.1));

        System.out.println(result);
        assertEquals(50.9911, result.avg().rmse(), 1E-4);
    }
}
