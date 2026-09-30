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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import smile.datasets.BitcoinPrice;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ARIMA(p, d, q).
 *
 * @author Haifeng Li
 */
public class ARIMATest {
    private static double[] logPrice;
    private static double[] logPriceDiff;

    @BeforeAll
    public static void setUpClass() throws Exception {
        var bitcoin = new BitcoinPrice();
        logPrice = bitcoin.logPrice();
        logPriceDiff = TimeSeries.diff(logPrice, 1);
    }

    @Test
    public void testARIMA_d0_matchesARMA() throws Exception {
        ARMA arma = ARMA.fit(logPriceDiff, 2, 2);
        ARIMA arima = ARIMA.fit(logPriceDiff, 2, 0, 2);

        assertEquals(0, arima.d());
        assertEquals(2, arima.p());
        assertEquals(2, arima.q());
        assertEquals(arma.intercept(), arima.intercept(), 1E-10);
        assertArrayEquals(arma.ar(), arima.ar(), 1E-10);
        assertArrayEquals(arma.ma(), arima.ma(), 1E-10);
        assertEquals(arma.RSS(), arima.RSS(), 1E-10);
        assertEquals(arma.variance(), arima.variance(), 1E-10);
        assertEquals(arma.logLikelihood(), arima.logLikelihood(), 1E-10);
        assertEquals(arma.aic(), arima.aic(), 1E-10);
        assertEquals(arma.bic(), arima.bic(), 1E-10);

        assertEquals(arma.forecast(), arima.forecast(), 1E-10);
        assertArrayEquals(arma.forecast(5), arima.forecast(5), 1E-10);
    }

    @Test
    public void testARIMA_d0_convenienceOverload() throws Exception {
        ARIMA arimaExplicit = ARIMA.fit(logPriceDiff, 2, 0, 2);
        ARIMA arimaOverload = ARIMA.fit(logPriceDiff, 2, 2);

        assertEquals(arimaExplicit.forecast(), arimaOverload.forecast(), 1E-10);
        assertArrayEquals(arimaExplicit.forecast(4), arimaOverload.forecast(4), 1E-10);
    }

    @Test
    public void testARIMA_d1_BitcoinLogPrice() throws Exception {
        ARIMA model = ARIMA.fit(logPrice, 2, 1, 2);

        assertEquals(2, model.p());
        assertEquals(1, model.d());
        assertEquals(2, model.q());
        assertEquals(2, model.ar().length);
        assertEquals(2, model.ma().length);
        assertNotNull(model.residuals());
        assertNotNull(model.fittedValues());
        assertEquals(model.residuals().length, model.fittedValues().length);

        assertTrue(model.variance() > 0.0);
        assertTrue(model.RSS() > 0.0);
        assertTrue(Double.isFinite(model.logLikelihood()));
        assertTrue(Double.isFinite(model.aic()));
        assertTrue(Double.isFinite(model.bic()));

        // 1-step forecast should match first element of multi-step forecast
        double oneStep = model.forecast();
        double[] multiStep = model.forecast(5);
        assertEquals(oneStep, multiStep[0], 1E-10);

        // All forecasted values must be finite
        for (double v : multiStep) {
            assertTrue(Double.isFinite(v));
        }

        // Verify fitted value relation: x[t] - fitted[t] == residuals[t]
        int n = model.residuals().length;
        for (int i = 0; i < n; i++) {
            int offset = logPrice.length - n + i;
            assertEquals(logPrice[offset] - model.fittedValues()[i], model.residuals()[i], 1E-10);
        }
    }

    @Test
    public void testARIMA_d2_quadraticTrend() throws Exception {
        int n = 100;
        double[] x = new double[n];
        Random rng = new Random(42);
        for (int t = 0; t < n; t++) {
            // Quadratic trend plus small noise
            x[t] = 0.05 * t * t + 0.2 * t + 10.0 + rng.nextGaussian() * 0.1;
        }

        ARIMA model = ARIMA.fit(x, 1, 2, 1);
        assertEquals(2, model.d());

        double oneStep = model.forecast();
        double[] multiStep = model.forecast(5);
        assertEquals(oneStep, multiStep[0], 1E-10);

        // Since series is growing quadratically, forecast should continue upward
        assertTrue(multiStep[4] > multiStep[0]);
        assertTrue(multiStep[0] > x[n - 1]);
    }

    @Test
    public void testPureARI_p2_d1_q0() throws Exception {
        ARIMA model = ARIMA.fit(logPrice, 2, 1, 0);
        assertEquals(2, model.p());
        assertEquals(1, model.d());
        assertEquals(0, model.q());
        assertEquals(2, model.ar().length);
        assertEquals(0, model.ma().length);

        double[] fc = model.forecast(3);
        assertEquals(3, fc.length);
        assertEquals(model.forecast(), fc[0], 1E-10);
        assertTrue(Double.isFinite(fc[0]));
    }

    @Test
    public void testPureIMA_p0_d1_q2() throws Exception {
        ARIMA model = ARIMA.fit(logPrice, 0, 1, 2);
        assertEquals(0, model.p());
        assertEquals(1, model.d());
        assertEquals(2, model.q());
        assertEquals(0, model.ar().length);
        assertEquals(2, model.ma().length);

        double[] fc = model.forecast(3);
        assertEquals(3, fc.length);
        assertEquals(model.forecast(), fc[0], 1E-10);
        assertTrue(Double.isFinite(fc[0]));
    }

    @Test
    public void testToString() throws Exception {
        ARIMA model = ARIMA.fit(logPrice, 2, 1, 2);
        String s = model.toString();
        assertTrue(s.contains("ARIMA(2, 1, 2)"));
        assertTrue(s.contains("Residuals:"));
        assertTrue(s.contains("Coefficients:"));
        assertTrue(s.contains("AIC:"));
        assertTrue(s.contains("BIC:"));
    }

    @Test
    public void testInputValidation() {
        assertThrows(IllegalArgumentException.class, () -> ARIMA.fit(logPrice, -1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> ARIMA.fit(logPrice, 1, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> ARIMA.fit(logPrice, 1, 1, -1));
        assertThrows(IllegalArgumentException.class, () -> ARIMA.fit(logPrice, 0, 1, 0)); // both p and q are 0
        assertThrows(IllegalArgumentException.class, () -> {
            ARIMA model = ARIMA.fit(logPrice, 1, 1, 1);
            model.forecast(0);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            ARIMA model = ARIMA.fit(logPrice, 1, 1, 1);
            model.forecast(-5);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            double[] shortSeries = new double[]{1.0, 2.0, 3.0};
            ARIMA.fit(shortSeries, 2, 1, 2);
        });
    }
}
