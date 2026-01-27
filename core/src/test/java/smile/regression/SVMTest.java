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

import smile.datasets.*;
import smile.io.Read;
import smile.io.Write;
import smile.math.kernel.GaussianKernel;
import smile.math.MathEx;
import smile.validation.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class SVMTest {

    public SVMTest() {
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
        var options = new SVM.Options(2.0, 10.0, 1E-3);
        RegressionMetrics metrics = LOOCV.regression(longley.x(), longley.y(), (x, y) -> SVM.fit(x, y, options));

        System.out.println("LOOCV RMSE = " + metrics.rmse());
        assertEquals(1.6140, metrics.rmse(), 1E-4);

        Regression<double[]> model = SVM.fit(longley.x(), longley.y(), options);
        java.nio.file.Path temp = Write.object(model);
        Read.object(temp);
    }

    @Test
    public void testCPU() throws Exception {
        System.out.println("CPU");
        var cpu = new CPU();
        double[][] x = cpu.x();
        double[] y = cpu.y();
        MathEx.standardize(x);

        MathEx.setSeed(19650218); // to get repeatable results.
        RegressionValidations<Regression<double[]>> result = CrossValidation.regression(10, x, y,
                (xi, yi) -> SVM.fit(xi, yi, new SVM.Options(40.0, 10.0, 1E-3)));

        System.out.println(result);
        assertEquals(47.1872, result.avg().rmse(), 1E-4);
    }

    @Test
    public void tesProstate() throws Exception {
        System.out.println("Prostate");
        var prostate = new ProstateCancer();
        GaussianKernel kernel = new GaussianKernel(6.0);

        RegressionValidation<Regression<double[]>> result = RegressionValidation.of(prostate.x(), prostate.y(),
                prostate.testx(), prostate.testy(), (x, y) -> SVM.fit(x, y, kernel, new SVM.Options(0.5, 5, 1E-3)));

        System.out.println(result);
        assertEquals(0.9112183360712871, result.metrics().rmse(), 1E-4);
    }

    @Test
    public void tesAbalone() throws Exception {
        System.out.println("Abalone");
        var abalone = new Abalone();
        GaussianKernel kernel = new GaussianKernel(5.0);
        var result = RegressionValidation.of(abalone.x(), abalone.y(), abalone.testx(), abalone.testy(),
                (x, y) -> SVM.fit(x, y, kernel, new SVM.Options(1.5, 100, 1E-3)));

        System.out.println(result);
        assertEquals(2.1092, result.metrics().rmse(), 1E-4);
    }

    @Test
    public void tesDiabetes() throws Exception {
        System.out.println("Diabetes");

        MathEx.setSeed(19650218); // to get repeatable results.
        var diabetes = new Diabetes();
        GaussianKernel kernel = new GaussianKernel(5.0);
        RegressionValidations<Regression<double[]>> result = CrossValidation.regression(10, diabetes.x(), diabetes.y(),
                (x, y) -> SVM.fit(x, y, kernel, new SVM.Options(50, 1000, 1E-3)));

        System.out.println(result);
        assertEquals(61.5148, result.avg().rmse(), 1E-4);
    }
}
