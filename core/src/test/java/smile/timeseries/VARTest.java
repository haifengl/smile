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
package smile.timeseries;

import org.junit.jupiter.api.Test;
import smile.io.Read;
import smile.io.Write;

import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the VAR model.
 *
 * @author Haifeng Li
 */
public class VARTest {

    private static double[][] simulateVAR1(double[][] a, int n, double noise, long seed) {
        int k = a.length;
        Random rng = new Random(seed);
        double[][] x = new double[n][k];
        for (int t = 0; t < n; t++) {
            for (int j = 0; j < k; j++) {
                double value = noise * rng.nextGaussian();
                if (t > 0) {
                    for (int i = 0; i < k; i++) {
                        value += a[j][i] * x[t - 1][i];
                    }
                }
                x[t][j] = value;
            }
        }
        return x;
    }

    @Test
    public void testGivenSyntheticVAR1WhenFittingThenRecoversCoefficients() {
        // Given a stable VAR(1) with a known 2x2 matrix.
        double[][] a = {
                {0.5, 0.2},
                {-0.3, 0.6}
        };
        double[][] x = simulateVAR1(a, 2000, 1.0, 42);

        // When
        VAR model = VAR.fit(x, 1);

        // Then
        assertEquals(2, model.k());
        assertEquals(1, model.p());
        assertTrue(model.hasIntercept());
        double[][][] coef = model.coefficients();
        for (int j = 0; j < 2; j++) {
            for (int i = 0; i < 2; i++) {
                assertEquals(a[j][i], coef[0][j][i], 0.05,
                        String.format("A[%d][%d]", j, i));
            }
        }
        assertTrue(model.isStable());
        assertTrue(model.spectralRadius() < 1.0);
        // The residual covariance is close to the identity of the simulation.
        double[][] cov = model.covariance();
        assertEquals(1.0, cov[0][0], 0.15);
        assertEquals(1.0, cov[1][1], 0.15);
    }

    @Test
    public void testGivenVAR2WhenFittingThenOrdersAndIdentityHold() {
        // Given a stable VAR(2).
        int k = 2;
        int n = 600;
        Random rng = new Random(7);
        double[][] x = new double[n][k];
        for (int t = 0; t < n; t++) {
            x[t][0] = 0.4 * (t >= 1 ? x[t - 1][0] : 0.0) - 0.2 * (t >= 2 ? x[t - 2][1] : 0.0) + rng.nextGaussian();
            x[t][1] = 0.3 * (t >= 1 ? x[t - 1][1] : 0.0) + 0.1 * (t >= 2 ? x[t - 2][0] : 0.0) + rng.nextGaussian();
        }

        // When
        VAR model = VAR.fit(x, 2);

        // Then
        assertEquals(2, model.p());
        assertEquals(2, model.coefficients().length);
        double[][] fitted = model.fittedValues();
        double[][] residuals = model.residuals();
        assertEquals(n - 2, fitted.length);
        for (int t = 0; t < fitted.length; t++) {
            for (int j = 0; j < k; j++) {
                assertEquals(x[t + 2][j], fitted[t][j] + residuals[t][j], 1E-10);
            }
        }
        assertTrue(model.isStable());
        assertTrue(Double.isFinite(model.logLikelihood()));
    }

    @Test
    public void testGivenStableVARWhenForecastingThenConvergesToMean() {
        // Given a stable VAR(1) with an intercept.
        double[][] a = {
                {0.5, 0.0},
                {0.0, 0.4}
        };
        int n = 800;
        Random rng = new Random(11);
        double[][] x = new double[n][2];
        for (int t = 0; t < n; t++) {
            x[t][0] = 10.0 + (t >= 1 ? 0.5 * (x[t - 1][0] - 10.0) : 0.0) + rng.nextGaussian() * 0.1;
            x[t][1] = -5.0 + (t >= 1 ? 0.4 * (x[t - 1][1] + 5.0) : 0.0) + rng.nextGaussian() * 0.1;
        }
        VAR model = VAR.fit(x, 1);

        // When
        double[][] forecast = model.forecast(60);

        // Then
        assertEquals(60, forecast.length);
        assertEquals(2, forecast[0].length);
        // The long-horizon forecasts approach the unconditional means.
        assertEquals(10.0, forecast[59][0], 1.0);
        assertEquals(-5.0, forecast[59][1], 1.0);
        // Successive forecasts get closer to the limit.
        assertTrue(Math.abs(forecast[59][0] - 10.0) < Math.abs(forecast[0][0] - 10.0));
    }

    @Test
    public void testGivenUnstableVARWhenCheckingStabilityThenFalse() {
        // Given an explosion: every component doubles each step.
        double[][] x = new double[60][2];
        x[0] = new double[]{1.0, 1.0};
        for (int t = 1; t < x.length; t++) {
            x[t][0] = 2.0 * x[t - 1][0];
            x[t][1] = 2.0 * x[t - 1][1];
        }

        // When
        VAR model = VAR.fit(x, 1);

        // Then
        assertFalse(model.isStable());
        assertTrue(model.spectralRadius() >= 2.0 - 1E-6);
    }

    @Test
    public void testGivenExtraLagWhenFittingThenBicPenalizesIt() {
        // Given a true VAR(1).
        double[][] a = {
                {0.6, -0.2},
                {0.1, 0.5}
        };
        double[][] x = simulateVAR1(a, 1200, 1.0, 23);

        // When
        VAR var1 = VAR.fit(x, 1);
        VAR var2 = VAR.fit(x, 2);

        // Then
        // The second lag is irrelevant: it may reduce the likelihood slightly,
        // but the parameter penalty must dominate.
        assertTrue(var2.bic() > var1.bic(),
                "BIC(VAR(2)) = " + var2.bic() + " should exceed BIC(VAR(1)) = " + var1.bic());
    }

    @Test
    public void testGivenNoInterceptWhenFittingThenInterceptIsZero() {
        // Given
        double[][] a = {
                {0.3, 0.1},
                {-0.1, 0.4}
        };
        double[][] x = simulateVAR1(a, 500, 1.0, 5);

        // When
        VAR model = VAR.fit(x, 1, false);

        // Then
        assertFalse(model.hasIntercept());
        assertArrayEquals(new double[]{0.0, 0.0}, model.intercept(), 1E-12);
    }

    @Test
    public void testGivenInvalidInputWhenFittingThenThrows() {
        // Jagged rows.
        double[][] jagged = {{1.0, 2.0}, {1.0}, {2.0, 3.0}};
        assertThrows(IllegalArgumentException.class, () -> VAR.fit(jagged, 1));

        // One series only.
        assertThrows(IllegalArgumentException.class, () -> VAR.fit(new double[][]{{1.0}, {2.0}, {3.0}}, 1));

        // Too few observations.
        assertThrows(IllegalArgumentException.class,
                () -> VAR.fit(new double[][]{{1.0, 2.0}, {2.0, 3.0}, {3.0, 4.0}}, 2));

        // Invalid order.
        assertThrows(IllegalArgumentException.class,
                () -> VAR.fit(new double[][]{{1.0, 2.0}, {2.0, 3.0}, {3.0, 4.0}}, 0));

        // Empty data.
        assertThrows(IllegalArgumentException.class, () -> VAR.fit(new double[0][], 1));
    }

    @Test
    public void testGivenFittedModelWhenSerializedThenRoundTrips() throws Exception {
        // Given
        double[][] a = {
                {0.4, 0.1},
                {-0.2, 0.5}
        };
        double[][] x = simulateVAR1(a, 400, 1.0, 31);
        VAR model = VAR.fit(x, 1);

        // When
        Path path = Write.object(model);
        VAR restored = (VAR) Read.object(path);

        // Then
        assertEquals(model.k(), restored.k());
        assertEquals(model.p(), restored.p());
        assertTrue(restored.isStable());
        double[][] expected = model.forecast(3);
        double[][] actual = restored.forecast(3);
        for (int h = 0; h < 3; h++) {
            assertArrayEquals(expected[h], actual[h], 1E-10);
        }
    }

    @Test
    public void testGivenFittedModelWhenPrintingThenSummaryShowsCoefficients() {
        double[][] a = {{0.5, 0.0}, {0.0, 0.5}};
        double[][] x = simulateVAR1(a, 200, 1.0, 3);
        VAR model = VAR.fit(x, 1);
        String summary = model.toString();

        assertTrue(summary.contains("VAR(1)"));
        assertTrue(summary.contains("Stability"));
        assertTrue(summary.contains("Coefficients of lag 1"));
        assertTrue(summary.contains("Residual covariance"));
        assertTrue(summary.contains("AIC:"));
    }
}
