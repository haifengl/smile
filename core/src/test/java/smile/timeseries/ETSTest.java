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
import smile.datasets.BitcoinPrice;
import smile.io.Read;
import smile.io.Write;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the ETS model.
 *
 * @author Haifeng Li
 */
public class ETSTest {

    @Test
    public void testGivenNoTrendNoSeasonalityWhenForecastingThenFlatForecast() {
        // Given
        double[] x = {10.0, 10.0, 10.0, 10.0, 10.0, 10.0};
        ETS model = ETS.fit(x, new ETS.Options(1, ETS.Trend.NONE, ETS.Seasonal.NONE));

        // When
        double[] forecast = model.forecast(4);

        // Then
        assertEquals(4, forecast.length);
        for (double f : forecast) {
            assertEquals(10.0, f, 1E-6);
        }
        assertEquals(0.0, model.beta(), 1E-12);
        assertEquals(1.0, model.phi(), 1E-12);
        assertEquals(1, model.period());
    }

    @Test
    public void testGivenAnyModelThenFittedPlusResidualEqualsObservation() {
        // Given
        double[] x = new double[60];
        Random rng = new Random(42);
        for (int t = 0; t < x.length; t++) {
            x[t] = 20.0 + 0.3 * t + rng.nextGaussian();
        }
        ETS model = ETS.fit(x, new ETS.Options(1, ETS.Trend.ADDITIVE, ETS.Seasonal.NONE));

        // When
        double[] fitted = model.fittedValues();
        double[] residuals = model.residuals();

        // Then
        assertEquals(x.length, fitted.length);
        for (int t = 0; t < x.length; t++) {
            assertEquals(x[t], fitted[t] + residuals[t], 1E-10);
        }
        double sse = 0.0;
        for (double e : residuals) {
            sse += e * e;
        }
        assertEquals(sse / x.length, model.variance(), 1E-8);
    }

    @Test
    public void testGivenDampedTrendWhenForecastingFarAheadThenConverges() {
        // Given
        double[] x = new double[80];
        Random rng = new Random(7);
        for (int t = 0; t < x.length; t++) {
            x[t] = 5.0 + 0.5 * t + rng.nextGaussian() * 0.1;
        }
        ETS model = ETS.fit(x, new ETS.Options(1, ETS.Trend.ADDITIVE_DAMPED, ETS.Seasonal.NONE));

        // When
        double[] forecast = model.forecast(200);

        // Then
        assertTrue(model.phi() < 1.0);
        // The increments must shrink, so the total rise is bounded.
        double rise = forecast[199] - forecast[0];
        assertTrue(rise < 200 * 0.5, "Damped forecast rose by " + rise);
        assertTrue(forecast[199] > forecast[0]);
    }

    @Test
    public void testGivenUndampedTrendWhenForecastingThenGrowsLinearly() {
        // Given
        double[] x = new double[80];
        Random rng = new Random(11);
        for (int t = 0; t < x.length; t++) {
            x[t] = 5.0 + 0.5 * t + rng.nextGaussian() * 0.1;
        }
        ETS model = ETS.fit(x, new ETS.Options(1, ETS.Trend.ADDITIVE, ETS.Seasonal.NONE));

        // When
        double[] forecast = model.forecast(50);

        // Then
        double first = forecast[1] - forecast[0];
        double last = forecast[49] - forecast[48];
        assertEquals(1.0, model.phi(), 1E-12);
        assertEquals(first, last, 1E-9);
        assertTrue(first > 0.0);
    }

    @Test
    public void testGivenSyntheticSeasonalSeriesWhenFittingAdditiveSeasonalThenRecoversPattern() {
        // Given
        int period = 12;
        int n = 120;
        double[] pattern = new double[period];
        for (int j = 0; j < period; j++) {
            pattern[j] = 5.0 * Math.cos(2 * Math.PI * j / period);
        }
        double[] x = new double[n];
        Random rng = new Random(42);
        for (int t = 0; t < n; t++) {
            x[t] = 50.0 + pattern[t % period] + rng.nextGaussian() * 0.3;
        }

        // When
        ETS model = ETS.fit(x, new ETS.Options(period, ETS.Trend.NONE, ETS.Seasonal.ADDITIVE));
        double[] forecast = model.forecast(period);

        // Then
        assertEquals(period, model.period());
        assertEquals(ETS.Seasonal.ADDITIVE, model.seasonal());
        for (int h = 0; h < period; h++) {
            double expected = 50.0 + pattern[(n + h) % period];
            double tolerance = Math.max(1.0, 0.10 * Math.abs(pattern[(n + h) % period]));
            assertEquals(expected, forecast[h], tolerance);
        }
    }

    @Test
    public void testGivenPositiveDataWhenFittingMultiplicativeSeasonalityThenRecoversPattern() {
        // Given
        int period = 12;
        int n = 120;
        double[] x = new double[n];
        Random rng = new Random(13);
        for (int t = 0; t < n; t++) {
            double seasonalFactor = 1.0 + 0.3 * Math.cos(2 * Math.PI * t / period);
            x[t] = 100.0 * seasonalFactor * (1.0 + 0.01 * rng.nextGaussian());
        }

        // When
        ETS model = ETS.fit(x, new ETS.Options(period, ETS.Trend.NONE, ETS.Seasonal.MULTIPLICATIVE));
        double[] forecast = model.forecast(period);

        // Then
        for (int h = 0; h < period; h++) {
            double expected = 100.0 * (1.0 + 0.3 * Math.cos(2 * Math.PI * (n + h) / period));
            assertEquals(expected, forecast[h], 0.10 * expected);
        }
    }

    @Test
    public void testGivenNegativeDataWhenFittingMultiplicativeSeasonalityThenThrows() {
        // Given
        double[] x = new double[40];
        for (int t = 0; t < x.length; t++) {
            x[t] = 10.0 - 0.5 * t;
        }

        // When/Then
        assertThrows(IllegalArgumentException.class,
                () -> ETS.fit(x, new ETS.Options(12, ETS.Trend.NONE, ETS.Seasonal.MULTIPLICATIVE)));
    }

    @Test
    public void testGivenTooShortSeriesWhenFittingSeasonalThenThrows() {
        // Given
        double[] x = new double[20];

        // When/Then
        assertThrows(IllegalArgumentException.class,
                () -> ETS.fit(x, new ETS.Options(12, ETS.Trend.NONE, ETS.Seasonal.ADDITIVE)));
        assertThrows(IllegalArgumentException.class,
                () -> ETS.fit(new double[3], new ETS.Options()));
        assertThrows(IllegalArgumentException.class,
                () -> ETS.fit(new double[10], new ETS.Options(0, ETS.Trend.NONE, ETS.Seasonal.NONE)));
        assertThrows(IllegalArgumentException.class,
                () -> ETS.fit(new double[40], new ETS.Options(12, ETS.Trend.NONE, ETS.Seasonal.ADDITIVE)).forecast(0));
    }

    @Test
    public void testGivenAutoWhenFittingSeasonalSeriesThenSelectsSeasonalModel() {
        // Given
        int period = 12;
        int n = 96;
        double[] x = new double[n];
        Random rng = new Random(21);
        for (int t = 0; t < n; t++) {
            x[t] = 30.0 + 6.0 * Math.sin(2 * Math.PI * t / period) + rng.nextGaussian() * 0.2;
        }

        // When
        ETS model = ETS.auto(x, period);
        ETS additive = ETS.fit(x, new ETS.Options(period, ETS.Trend.NONE, ETS.Seasonal.ADDITIVE));

        // Then
        assertNotEquals(ETS.Seasonal.NONE, model.seasonal(), "auto() failed to detect seasonality");
        assertTrue(model.aicc() <= additive.aicc() + 1E-6,
                "AICc-selected model should not be worse than the additive candidate");
        assertTrue(Double.isFinite(model.aicc()));
        assertTrue(model.aicc() >= model.aic());
        assertTrue(Double.isFinite(model.bic()));
        assertTrue(Double.isFinite(model.logLikelihood()));
    }

    @Test
    public void testGivenOptionsWhenConvertingToPropertiesThenRoundTrips() {
        // Given
        ETS.Options options = new ETS.Options(12, ETS.Trend.ADDITIVE_DAMPED, ETS.Seasonal.MULTIPLICATIVE);

        // When
        var props = options.toProperties();
        ETS.Options restored = ETS.Options.of(props);

        // Then
        assertEquals(options, restored);
        assertEquals("12", props.getProperty("smile.ets.period"));
    }

    @Test
    public void testGivenFittedModelWhenSerializedThenRoundTrips() throws Exception {
        // Given
        var bitcoin = new BitcoinPrice();
        double[] logPrice = bitcoin.logPrice();
        double[] diff = TimeSeries.diff(logPrice, 1);
        ETS model = ETS.fit(diff, new ETS.Options(1, ETS.Trend.NONE, ETS.Seasonal.NONE));

        // When
        Path path = Write.object(model);
        ETS restored = (ETS) Read.object(path);

        // Then
        assertEquals(model.alpha(), restored.alpha(), 1E-12);
        assertEquals(model.variance(), restored.variance(), 1E-12);
        assertEquals(model.forecast(3)[2], restored.forecast(3)[2], 1E-10);
        assertEquals(model.toString(), restored.toString());
    }

    @Test
    public void testGivenFittedModelWhenPrintingThenSummaryMentionsParameters() {
        // Given
        double[] x = new double[50];
        for (int t = 0; t < x.length; t++) {
            x[t] = Math.sin(0.3 * t) + 0.02 * t;
        }

        // When
        ETS model = ETS.fit(x, new ETS.Options(1, ETS.Trend.ADDITIVE, ETS.Seasonal.NONE));
        String summary = model.toString();

        // Then
        assertTrue(summary.contains("ETS("));
        assertTrue(summary.contains("alpha"));
        assertTrue(summary.contains("beta"));
        assertTrue(summary.contains("AIC:"));
        assertTrue(summary.contains("BIC:"));
    }
}
