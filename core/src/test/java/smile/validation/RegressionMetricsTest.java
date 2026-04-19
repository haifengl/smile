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
package smile.validation;

import org.junit.jupiter.api.Test;
import smile.regression.Regression;
import static org.junit.jupiter.api.Assertions.*;

public class RegressionMetricsTest {
    @Test
    public void givenValidPredictions_whenComputingRegressionMetrics_thenReturnExpectedValues() {
        double[] truth = {1.0, 2.0, 3.0};
        double[] prediction = {1.0, 2.0, 4.0};

        RegressionMetrics metrics = RegressionMetrics.of(1.0, 2.0, truth, prediction);
        assertEquals(3, metrics.size());
        assertEquals(1.0, metrics.rss(), 1E-12);
        assertEquals(1.0 / 3.0, metrics.mse(), 1E-12);
        assertEquals(Math.sqrt(1.0 / 3.0), metrics.rmse(), 1E-12);
        assertEquals(1.0 / 3.0, metrics.mad(), 1E-12);
        assertEquals(0.5, metrics.r2(), 1E-12);
    }

    @Test
    public void givenMismatchedVectors_whenComputingRegressionMetrics_thenThrowIllegalArgumentException() {
        double[] truth = {1.0, 2.0};
        double[] prediction = {1.0};

        assertThrows(IllegalArgumentException.class,
                () -> RegressionMetrics.of(0.0, 0.0, truth, prediction));
    }

    @Test
    public void givenRegressionModel_whenComputingRegressionMetrics_thenUseModelPredictions() {
        Regression<Double> model = x -> x;
        Double[] testx = {1.0, 2.0, 3.0};
        double[] testy = {1.0, 2.0, 3.0};

        RegressionMetrics metrics = RegressionMetrics.of(5.0, model, testx, testy);
        assertEquals(3, metrics.size());
        assertEquals(0.0, metrics.rss(), 1E-12);
        assertEquals(0.0, metrics.mse(), 1E-12);
        assertEquals(0.0, metrics.rmse(), 1E-12);
        assertEquals(0.0, metrics.mad(), 1E-12);
        assertEquals(1.0, metrics.r2(), 1E-12);
    }

    @Test
    public void givenRegressionMetrics_whenConvertedToString_thenIncludeMetricNames() {
        RegressionMetrics metrics = new RegressionMetrics(1.0, 2.0, 3, 4.0, 5.0, 6.0, 7.0, 0.8);
        String text = metrics.toString();
        assertTrue(text.contains("RSS"));
        assertTrue(text.contains("MSE"));
        assertTrue(text.contains("RMSE"));
        assertTrue(text.contains("MAD"));
        assertTrue(text.contains("R2"));
    }
}

