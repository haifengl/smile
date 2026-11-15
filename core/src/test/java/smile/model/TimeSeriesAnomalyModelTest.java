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
package smile.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import smile.anomaly.TimeSeriesAnomaly;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.io.Read;
import smile.io.Write;
import smile.math.MathEx;

import java.nio.file.Path;
import java.util.Properties;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for the time-series anomaly detector through
 * {@link Model#anomaly}.
 *
 * @author Haifeng Li
 */
public class TimeSeriesAnomalyModelTest {

    private static final int PERIOD = 12;

    @BeforeEach
    public void setUp() {
        MathEx.setSeed(19650218);
    }

    private static double[] series(int n, double noise, long seed, int offset) {
        Random rng = new Random(seed);
        double[] x = new double[n];
        for (int t = 0; t < n; t++) {
            x[t] = 50.0 + 10.0 * Math.sin(2 * Math.PI * (offset + t) / PERIOD) + rng.nextGaussian() * noise;
        }
        return x;
    }

    private static DataFrame frame(double[] x) {
        double[][] data = new double[x.length][1];
        for (int i = 0; i < x.length; i++) {
            data[i][0] = x[i];
        }
        return DataFrame.of(data, "value");
    }

    @Test
    public void testGivenTimeSeriesWhenTrainingModelThenDetectsInjectedSpike() throws Exception {
        // Given
        double[] history = series(400, 0.5, 42, 0);
        double[] test = series(120, 0.5, 7, history.length);
        int spike = 73;
        test[spike] += 40.0;

        Properties params = new Properties();
        params.setProperty("smile.timeseries.model", "ets");
        params.setProperty("smile.timeseries.period", Integer.toString(PERIOD));
        params.setProperty("smile.timeseries_anomaly.threshold", "3.0");

        // When
        AnomalyModel model = Model.anomaly("ts-residual", Formula.rhs("."), frame(history), params);

        // Then
        assertEquals("ts-residual", model.algorithm());
        assertEquals(1, model.schema().length());
        assertEquals(3.0, model.threshold(), 1E-12);
        assertFalse(model.isOnline());
        assertFalse(model.supportsShap());
        assertInstanceOf(TimeSeriesAnomaly.class, model.detector());
        assertEquals("true", model.tags().getProperty("smile.timeseries.order_dependent"));

        double[] scores = model.score(frame(test));
        boolean[] flags = model.predict(frame(test));
        assertEquals(test.length, scores.length);
        assertEquals(test.length, flags.length);
        assertTrue(flags[spike], "spike not flagged; score = " + scores[spike]);
        assertTrue(scores[spike] > 3.0);

        int flagged = 0;
        for (boolean flag : flags) {
            if (flag) {
                flagged++;
            }
        }
        assertTrue(flagged <= 3, "Too many points flagged: " + flagged);

        // Single-row inference on the spike.
        var row = frame(new double[]{test[spike]}).get(0);
        Prediction prediction = model.infer(row);
        assertEquals(Boolean.TRUE, prediction.output());
        assertNotNull(prediction.score());
        assertEquals(prediction.score() > 3.0, prediction.output());
        assertTrue(prediction.toJson().contains("\"prediction\":true"));

        // Explain is not supported, as for the other detectors.
        Prediction explained = model.infer(row, false, true);
        assertEquals("Not supported", explained.explanations().shap());

        // Multi-step zero forecast for a clean continuation scores low.
        double[] clean = series(60, 0.5, 13, history.length);
        double[] cleanScores = model.score(frame(clean));
        for (double s : cleanScores) {
            assertTrue(s < 10.0);
        }

        // Serialization round-trip.
        Path path = Write.object(model);
        AnomalyModel restored = (AnomalyModel) Read.object(path);
        assertEquals(model.tags().getProperty("smile.timeseries.order_dependent"),
                restored.tags().getProperty("smile.timeseries.order_dependent"));
        assertArrayEquals(scores, restored.score(frame(test)), 1E-10);
    }

    @Test
    public void testGivenARIMAUnderlyingModelWhenTrainingThenWorks() throws Exception {
        // Given
        double[] history = series(400, 0.5, 17, 0);
        Properties params = new Properties();
        params.setProperty("smile.timeseries.model", "arima");
        params.setProperty("smile.timeseries.order", "2,1,2");
        params.setProperty("smile.timeseries_anomaly.scale", "STD");

        // When
        AnomalyModel model = Model.anomaly("ts-residual", Formula.rhs("."), frame(history), params);

        // Then
        TimeSeriesAnomaly detector = (TimeSeriesAnomaly) model.detector();
        assertEquals(TimeSeriesAnomaly.Scale.STD, detector.scale());
        assertInstanceOf(smile.timeseries.ARIMA.class, detector.model());

        double[] test = series(80, 0.5, 19, history.length);
        test[50] -= 30.0;
        boolean[] flags = model.predict(frame(test));
        assertTrue(flags[50]);
    }

    @Test
    public void testGivenMultiColumnDataWhenTrainingThenThrows() throws Exception {
        // Given a two-column frame.
        double[] a = series(100, 0.5, 3, 0);
        double[] b = series(100, 0.5, 5, 0);
        double[][] twoColumns = new double[a.length][2];
        for (int i = 0; i < a.length; i++) {
            twoColumns[i][0] = a[i];
            twoColumns[i][1] = b[i];
        }
        DataFrame data = DataFrame.of(twoColumns, "a", "b");

        // When/Then
        Properties params = new Properties();
        params.setProperty("smile.timeseries.model", "ets");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> Model.anomaly("ts-residual", Formula.rhs("."), data, params));
        assertTrue(ex.getMessage().contains("one numeric column"));
    }

    @Test
    public void testGivenTooShortHistoryWhenTrainingThenThrows() {
        // Given
        double[] shortSeries = series(5, 0.5, 3, 0);

        // When/Then
        Properties params = new Properties();
        assertThrows(IllegalArgumentException.class,
                () -> Model.anomaly("ts-residual", Formula.rhs("."), frame(shortSeries), params));
    }

    @Test
    public void testGivenUnsupportedTimeseriesModelWhenTrainingThenThrows() {
        // Given
        double[] history = series(100, 0.5, 3, 0);

        // When/Then
        Properties params = new Properties();
        params.setProperty("smile.timeseries.model", "garch");
        assertThrows(IllegalArgumentException.class,
                () -> Model.anomaly("ts-residual", Formula.rhs("."), frame(history), params));
    }

    @Test
    public void testGivenExtraColumnsWhenScoringThenFormulaSelectsTheTrainedColumn() throws Exception {
        // Given a model trained on the single "value" column.
        double[] history = series(200, 0.5, 9, 0);
        Properties params = new Properties();
        params.setProperty("smile.timeseries.model", "ets");
        params.setProperty("smile.timeseries.period", Integer.toString(PERIOD));
        AnomalyModel model = Model.anomaly("ts-residual", Formula.rhs("."), frame(history), params);

        // When a frame carries an extra column, the bound formula picks "value".
        double[] clean = series(20, 0.5, 11, history.length);
        double[][] twoColumns = new double[clean.length][2];
        for (int i = 0; i < clean.length; i++) {
            twoColumns[i][0] = clean[i];
            twoColumns[i][1] = 1E6;
        }
        DataFrame extra = DataFrame.of(twoColumns, "value", "extra");
        double[] scores = model.score(extra);

        // Then the constant extra column does not make every row anomalous.
        assertEquals(clean.length, scores.length);
        for (double s : scores) {
            assertTrue(s < 10.0, "score = " + s);
        }
    }
}
