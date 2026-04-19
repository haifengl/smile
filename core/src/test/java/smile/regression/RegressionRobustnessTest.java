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

import org.junit.jupiter.api.Test;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.data.vector.DoubleVector;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Targeted regression-package robustness tests.
 */
public class RegressionRobustnessTest {
    private static final double TOLERANCE = 1E-12;

    private record WeightedLine(double intercept, double slope) {
    }

    @Test
    public void testGivenForgettingFactorWhenUpdatingThenMatchesWeightedLeastSquares() {
        // Given
        double[][] x = {
                {0.0},
                {1.0},
                {2.0}
        };
        double[] y = {0.0, 1.0, 2.0};
        DataFrame batch = DataFrame.of(x).add(new DoubleVector("y", y));
        LinearModel model = OLS.fit(Formula.lhs("y"), batch);
        double lambda = 0.5;

        // When
        model.update(new double[]{1.0, 3.0}, 6.0, lambda);

        // Then
        WeightedLine expected = weightedLeastSquaresLine(
                new double[]{0.0, 1.0, 2.0, 3.0},
                new double[]{0.0, 1.0, 2.0, 6.0},
                new double[]{lambda, lambda, lambda, 1.0}
        );
        assertEquals(expected.intercept(), model.intercept(), 1E-10);
        assertEquals(expected.slope(), model.coefficients().get(0), 1E-10);
    }

    @Test
    public void testGivenConstantPredictorWhenFittingElasticNetThenThrowsMeaningfulException() {
        // Given
        double[][] x = {
                {1.0, 0.0},
                {1.0, 1.0},
                {1.0, 2.0},
                {1.0, 3.0}
        };
        double[] y = {1.0, 2.0, 3.0, 4.0};
        DataFrame data = DataFrame.of(x).add(new DoubleVector("y", y));

        // When
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> ElasticNet.fit(Formula.lhs("y"), data, 0.5, 0.5));

        // Then
        assertTrue(exception.getMessage().contains("constant"));
    }

    @Test
    public void testGivenNegativeRidgePenaltyTargetWhenCreatingOptionsThenItIsAcceptedAndCanFit() {
        // Given
        RidgeRegression.Options options = new RidgeRegression.Options(
                new double[]{1.5, 0.5},
                new double[]{-1.0, 0.25}
        );
        double[][] x = {
                {0.0, 0.0},
                {1.0, 0.0},
                {0.0, 1.0},
                {1.0, 1.0}
        };
        double[] y = {-1.0, 1.0, 0.0, 2.0};
        DataFrame data = DataFrame.of(x).add(new DoubleVector("y", y));

        // When
        RidgeRegression.Options restored = RidgeRegression.Options.of(options.toProperties());
        LinearModel model = assertDoesNotThrow(() -> RidgeRegression.fit(Formula.lhs("y"), data, new double[]{1.0, 1.0, 1.0, 1.0}, restored));

        // Then
        assertArrayEquals(options.lambda(), restored.lambda(), TOLERANCE);
        assertArrayEquals(options.beta0(), restored.beta0(), TOLERANCE);
        assertTrue(Double.isFinite(model.intercept()));
        assertTrue(Double.isFinite(model.coefficients().get(0)));
        assertTrue(Double.isFinite(model.coefficients().get(1)));
    }

    @Test
    public void testGivenRegressionOptionsWhenSerializedThenRoundTripPreservesValues() {
        // Given
        OLS.Options ols = new OLS.Options(OLS.Method.SVD, false, true);
        ElasticNet.Options elasticNet = new ElasticNet.Options(0.75, 0.25, 1E-5, 200, 0.02, 0.4, 1E-4, 50, 250);

        // When
        OLS.Options restoredOls = OLS.Options.of(ols.toProperties());
        ElasticNet.Options restoredElasticNet = ElasticNet.Options.of(elasticNet.toProperties());

        // Then
        assertEquals(ols.method(), restoredOls.method());
        assertEquals(ols.stderr(), restoredOls.stderr());
        assertEquals(ols.recursive(), restoredOls.recursive());

        assertEquals(elasticNet.lambda1(), restoredElasticNet.lambda1(), TOLERANCE);
        assertEquals(elasticNet.lambda2(), restoredElasticNet.lambda2(), TOLERANCE);
        assertEquals(elasticNet.tol(), restoredElasticNet.tol(), TOLERANCE);
        assertEquals(elasticNet.maxIter(), restoredElasticNet.maxIter());
        assertEquals(elasticNet.alpha(), restoredElasticNet.alpha(), TOLERANCE);
        assertEquals(elasticNet.beta(), restoredElasticNet.beta(), TOLERANCE);
        assertEquals(elasticNet.eta(), restoredElasticNet.eta(), TOLERANCE);
        assertEquals(elasticNet.lsMaxIter(), restoredElasticNet.lsMaxIter());
        assertEquals(elasticNet.pcgMaxIter(), restoredElasticNet.pcgMaxIter());
    }

    private static WeightedLine weightedLeastSquaresLine(double[] x, double[] y, double[] weights) {
        double sw = 0.0;
        double sx = 0.0;
        double sy = 0.0;
        double sxx = 0.0;
        double sxy = 0.0;

        for (int i = 0; i < x.length; i++) {
            sw += weights[i];
            sx += weights[i] * x[i];
            sy += weights[i] * y[i];
            sxx += weights[i] * x[i] * x[i];
            sxy += weights[i] * x[i] * y[i];
        }

        double denominator = sw * sxx - sx * sx;
        double slope = (sw * sxy - sx * sy) / denominator;
        double intercept = (sy - slope * sx) / sw;
        return new WeightedLine(intercept, slope);
    }
}

