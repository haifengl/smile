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

import smile.data.DataFrame;
import smile.datasets.ProstateCancer;
import smile.io.Read;
import smile.io.Write;
import smile.math.MathEx;
import smile.datasets.CPU;
import smile.datasets.Longley;
import smile.validation.CrossValidation;
import smile.validation.RegressionValidations;
import smile.validation.metric.RMSE;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class OLSTest {

    public OLSTest() {
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
        System.out.println("Longley");
        var longley = new Longley();
        LinearModel model = OLS.fit(longley.formula(), longley.data());
        System.out.println(model);

        assertEquals(12.8440, model.RSS(), 1E-4);
        assertEquals(1.1946, model.error(), 1E-4);
        assertEquals(9, model.df());
        assertEquals(0.9926, model.RSquared(), 1E-4);
        assertEquals(0.9877, model.adjustedRSquared(), 1E-4);
        assertEquals(202.5094, model.ftest(), 1E-4);
        assertEquals(4.42579E-9, model.pvalue(), 1E-14);

        double[][] ttest = {
                {2946.85636, 5647.97658,   0.522,   0.6144},
                {   0.26353,    0.10815,   2.437,   0.0376},
                {   0.03648,    0.03024,   1.206,   0.2585},
                {   0.01116,    0.01545,   0.722,   0.4885},
                {  -1.73703,    0.67382,  -2.578,   0.0298},
                {  -1.41880,    2.94460,  -0.482,   0.6414},
                {   0.23129,    1.30394,   0.177,   0.8631}
        };

        double[] residuals = {
                -0.6008156,  1.5502732,  0.1032287, -1.2306486, -0.3355139,  0.2693345,  0.8776759,
                0.1222429, -2.0086121, -0.4859826,  1.0663129,  1.2274906, -0.3835821,  0.2710215,
                0.1978569, -0.6402823
        };

        for (int i = 0; i < ttest.length; i++) {
            for (int j = 0; j < 4; j++) {
                assertEquals(ttest[i][j], model.ttest()[i][j], 1E-3);
            }
        }

        for (int i = 0; i < residuals.length; i++) {
            assertEquals(residuals[i], model.residuals().get(i), 1E-4);
        }

        java.nio.file.Path temp = Write.object(model);
        Read.object(temp);

        // Test with data without response variable.
        DataFrame test = longley.data().drop("deflator");
        model.predict(test);
    }

    @Test
    public void testCPU() throws Exception {
        System.out.println("CPU");

        MathEx.setSeed(19650218); // to get repeatable results.
        var cpu = new CPU();
        LinearModel model = OLS.fit(cpu.formula(), cpu.data());
        System.out.println(model);

        RegressionValidations<LinearModel> result = CrossValidation.regression(10, cpu.formula(), cpu.data(), OLS::fit);

        System.out.println(result);
        assertEquals(51.0009, result.avg().rmse(), 1E-4);
    }

    @Test
    public void testProstate() throws Exception {
        System.out.println("Prostate");
        var prostate = new ProstateCancer();
        LinearModel model = OLS.fit(prostate.formula(), prostate.train());
        System.out.println(model);

        double[] prediction = model.predict(prostate.test());
        double rmse = RMSE.of(prostate.testy(), prediction);
        System.out.println("RMSE on test data = " + rmse);
        assertEquals(0.721993, rmse, 1E-4);
    }
}
