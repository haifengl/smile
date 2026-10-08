/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
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
package smile.regression; // Elastic Net test suite
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.data.vector.DoubleVector;
import smile.datasets.Abalone;
import smile.datasets.CPU;
import smile.datasets.Diabetes;
import smile.datasets.Longley;
import smile.datasets.ProstateCancer;
import smile.io.Read;
import smile.io.Write;
import smile.math.MathEx;
import smile.validation.CrossValidation;
import smile.validation.LOOCV;
import smile.validation.RegressionMetrics;
import smile.validation.RegressionValidation;
import static org.junit.jupiter.api.Assertions.assertEquals;
//
/** Unit tests for {@link ElasticNet}.
 * @author Haifeng Li
 **/
public class ElasticNetTest { // test suite
    //
    @BeforeEach
    void setUp() {
        MathEx.setSeed(19650218);
    } // setUp

    @Test void testToy() {
        // Given
        double[][] x = {
                {1.0, 0.0, 0.0, 0.5},
                {0.0, 1.0, 0.2, 0.3},
                {1.0, 0.5, 0.2, 0.3},
                {0.0, 0.1, 0.0, 0.2},
                {0.0, 0.1, 1.0, 0.2}
        };
        double[] y = {6.0, 5.2, 6.2, 5.0, 6.0};
        DataFrame df = DataFrame.of(x).add(new DoubleVector("y", y));

        // When
        RegressionValidation<LinearModel> result = RegressionValidation.of(
                Formula.lhs("y"), df, df,
                (formula, data) -> ElasticNet.fit(formula, data, 0.1, 0.001)
        );

        // Then
        assertEquals(5.0294, result.model().intercept(), 1E-4);
        double[] expectedWeights = {0.9618, -2.9425E-5, 0.9497, 7.4383E-5};
        for (int i = 0; i < expectedWeights.length; i++) {
            assertEquals(expectedWeights[i], result.model().coefficients().get(i), 1E-4);
        }
    } // testToy

    @Test void testLongley() throws Exception {
        // Given
        var longley = new Longley();

        // When
        LinearModel model = ElasticNet.fit(longley.formula(), longley.data(), 0.1, 0.1);
        RegressionMetrics metrics = LOOCV.regression(longley.formula(), longley.data(),
                (f, d) -> ElasticNet.fit(f, d, 0.1, 0.1));

        // Then
        assertEquals(1.7401, metrics.rmse(), 1E-4);

        Path tempPath = Write.object(model);
        Read.object(tempPath);
    } // testLongley
    //
    @Test void testCPU() throws Exception {
        // Given
        var cpu = new CPU();

        // When
        LinearModel model = ElasticNet.fit(cpu.formula(), cpu.data(), 0.8, 0.2);
        var cv = CrossValidation.regression(10, cpu.formula(), cpu.data(),
                (f, d) -> ElasticNet.fit(f, d, 0.8, 0.2));

        // Then
        assertEquals(55.7878, cv.avg().rmse(), 1E-4);
    } // testCPU
    //
    @Test void testProstate() throws Exception {
        // Given
        var prostate = new ProstateCancer();

        // When
        var result = RegressionValidation.of(prostate.formula(), prostate.train(), prostate.test(),
                (formula, data) -> ElasticNet.fit(formula, data, 0.8, 0.2));

        // Then
        assertEquals(0.7103, result.metrics().rmse(), 1E-4);
    } // testProstate
    //
    @Test void testAbalone() throws Exception {
        // Given
        var abalone = new Abalone();

        // When
        var result = RegressionValidation.of(abalone.formula(), abalone.train(), abalone.test(),
                (formula, data) -> ElasticNet.fit(formula, data, 0.8, 0.2));

        // Then
        assertEquals(2.1263, result.metrics().rmse(), 1E-4);
    } // testAbalone

    @Test void testDiabetes() throws Exception {
        // Given
        var diabetes = new Diabetes();

        // When
        LinearModel model = ElasticNet.fit(diabetes.formula(), diabetes.data(), 0.8, 0.2);
        var cv = CrossValidation.regression(10, diabetes.formula(), diabetes.data(),
                (f, d) -> ElasticNet.fit(f, d, 0.8, 0.2));

        // Then
        assertEquals(58.4945, cv.avg().rmse(), 0.01);
    } // testDiabetes
} // class ElasticNetTest
