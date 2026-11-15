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
package smile.anomaly;

import org.junit.jupiter.api.Test;
import smile.io.Read;
import smile.io.Write;
import smile.timeseries.ARIMA;
import smile.timeseries.ETS;
import smile.timeseries.TimeSeriesModel;

import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TimeSeriesAnomaly}.
 *
 * @author Haifeng Li
 */
public class TimeSeriesAnomalyTest {

    /** The seasonal period of the synthetic series; the ETS fit is told about it. */
    private static final int PERIOD = 12;

    /** Generates a seasonally driven series plus small Gaussian noise. */
    private static double[] series(int n, double noise, long seed) {
        return series(n, noise, seed, 0);
    }

    /** Generates a series continuation that starts at the given phase offset. */
    private static double[] series(int n, double noise, long seed, int offset) {
        Random rng = new Random(seed);
        double[] x = new double[n];
        for (int t = 0; t < n; t++) {
            x[t] = 50.0 + 10.0 * Math.sin(2 * Math.PI * (offset + t) / PERIOD) + rng.nextGaussian() * noise;
        }
        return x;
    }

    private static TimeSeriesAnomaly detector(double[] history) {
        TimeSeriesModel model = ETS.auto(history, PERIOD);
        return TimeSeriesAnomaly.fit(history, model, new TimeSeriesAnomaly.Options());
    }

    @Test
    public void testGivenCleanSeriesWhenScoringThenFalsePositiveRateIsLow() {
        // Given a detector trained on one clean series, scored on a fresh
        // continuation drawn from the same process.
        double[] history = series(500, 0.5, 42);
        TimeSeriesAnomaly detector = detector(history);
        double[] continuation = series(500, 0.5, 99, history.length);

        // When
        boolean[] flags = detector.predict(continuation);

        // Then
        int anomalies = 0;
        for (boolean flag : flags) {
            if (flag) {
                anomalies++;
            }
        }
        double rate = (double) anomalies / flags.length;
        assertTrue(rate < 0.05, "False-positive rate = " + rate);
        assertTrue(detector.sigma() > 0.0);
        assertEquals(TimeSeriesAnomaly.Scale.MAD, detector.scale());
        assertEquals(3.0, detector.threshold(), 1E-12);
    }

    @Test
    public void testGivenInjectedSpikeWhenScoringThenSpikeIsFlagged() {
        // Given
        double[] history = series(400, 0.5, 7);
        TimeSeriesAnomaly detector = detector(history);
        double[] test = series(200, 0.5, 11, history.length);
        int index = 137;
        test[index] += 40.0;

        // When
        double[] scores = detector.score(test);
        boolean[] flags = detector.predict(test);

        // Then
        assertEquals(test.length, scores.length);
        assertTrue(flags[index], "spike at " + index + " not flagged, score = " + scores[index]);
        assertTrue(scores[index] > detector.threshold());
        // The bulk of the series remains unflagged.
        int flagged = 0;
        for (boolean flag : flags) {
            if (flag) {
                flagged++;
            }
        }
        assertTrue(flagged <= 3, "Too many points flagged: " + flagged);
    }

    @Test
    public void testGivenBatchWhenScoringThenFirstPointMatchesSingleScoring() {
        // Given
        double[] history = series(300, 0.5, 3);
        TimeSeriesAnomaly detector = detector(history);
        double[] test = series(50, 0.5, 5, history.length);

        // When
        double[] batch = detector.score(test);
        double first = detector.score(test[0]);

        // Then: the first point of the batch is the single-step score, and
        // scoring is side-effect free.
        assertEquals(first, batch[0], 1E-10);
        assertArrayEquals(batch, detector.score(test), 1E-12);
    }

    @Test
    public void testGivenPeriodicContinuationWhenScoringThenNoSpuriousFlags() {
        // Given a noise-free seasonal history.
        double[] history = series(240, 0.0, 41);
        TimeSeriesAnomaly detector = detector(history);

        // When: the continuation repeats the same seasonal cycle.
        double[] scores = detector.score(series(48, 0.0, 43, history.length));

        // Then the perfectly periodic continuation is not anomalous.
        for (double s : scores) {
            assertTrue(s < 10.0, "score = " + s);
        }
    }

    @Test
    public void testGivenScaleOptionsWhenConvertingToPropertiesThenRoundTrips() {
        // Given
        TimeSeriesAnomaly.Options options = new TimeSeriesAnomaly.Options(
                TimeSeriesAnomaly.Scale.EWMA, 4.5, 0.35);

        // When
        var props = options.toProperties();
        TimeSeriesAnomaly.Options restored = TimeSeriesAnomaly.Options.of(props);

        // Then
        assertEquals(options, restored);
        assertEquals("EWMA", props.getProperty("smile.timeseries_anomaly.scale"));
    }

    @Test
    public void testGivenAlternativeScalesWhenFittingThenSigmaDiffers() {
        // Given
        double[] history = series(300, 0.5, 17);
        TimeSeriesModel model = ETS.auto(history, 1);

        // When
        TimeSeriesAnomaly mad = TimeSeriesAnomaly.fit(history, model,
                new TimeSeriesAnomaly.Options(TimeSeriesAnomaly.Scale.MAD, 3.0, 0.2));
        TimeSeriesAnomaly std = TimeSeriesAnomaly.fit(history, model,
                new TimeSeriesAnomaly.Options(TimeSeriesAnomaly.Scale.STD, 3.0, 0.2));
        TimeSeriesAnomaly ewma = TimeSeriesAnomaly.fit(history, model,
                new TimeSeriesAnomaly.Options(TimeSeriesAnomaly.Scale.EWMA, 3.0, 0.2));

        // Then
        assertTrue(mad.sigma() > 0.0);
        assertTrue(std.sigma() > 0.0);
        assertTrue(ewma.sigma() > 0.0);
        assertNotEquals(mad.sigma(), std.sigma(), 1E-6);
        assertEquals(0.2, ewma.ewmaAlpha(), 1E-12);
    }

    @Test
    public void testGivenInvalidOptionsWhenConstructingThenThrows() {
        assertThrows(IllegalArgumentException.class, () -> new TimeSeriesAnomaly.Options(null, 3.0, 0.2));
        assertThrows(IllegalArgumentException.class,
                () -> new TimeSeriesAnomaly.Options(TimeSeriesAnomaly.Scale.MAD, 0.0, 0.2));
        assertThrows(IllegalArgumentException.class,
                () -> new TimeSeriesAnomaly.Options(TimeSeriesAnomaly.Scale.MAD, 3.0, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new TimeSeriesAnomaly.Options(TimeSeriesAnomaly.Scale.MAD, 3.0, 1.5));
    }

    @Test
    public void testGivenTooShortHistoryWhenFittingThenThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> TimeSeriesAnomaly.fit(new double[]{1.0, 2.0, 3.0}, new TimeSeriesAnomaly.Options()));
        assertThrows(IllegalArgumentException.class,
                () -> TimeSeriesAnomaly.fit((double[]) null, new TimeSeriesAnomaly.Options()));
        assertThrows(IllegalArgumentException.class,
                () -> TimeSeriesAnomaly.fit(new double[20], null));
    }

    @Test
    public void testGivenConstantHistoryWhenFittingThenDegenerateScaleIsRejected() {
        // Given
        double[] constant = new double[50];
        java.util.Arrays.fill(constant, 7.0);

        // When/Then
        TimeSeriesModel model = ETS.fit(constant, new ETS.Options());
        assertThrows(IllegalArgumentException.class,
                () -> TimeSeriesAnomaly.fit(constant, model, new TimeSeriesAnomaly.Options()));
    }

    @Test
    public void testGivenARIMAModelWhenFittingDetectorThenWorks() {
        // Given
        double[] history = series(400, 0.5, 23);
        TimeSeriesModel model = ARIMA.fit(history, 2, 0, 2);

        // When
        TimeSeriesAnomaly detector = TimeSeriesAnomaly.fit(history, model, new TimeSeriesAnomaly.Options());

        // Then
        assertSame(model, detector.model());
        double[] test = series(60, 0.5, 29, history.length);
        test[40] += 15.0;
        boolean[] flags = detector.predict(test);
        assertTrue(flags[40]);
    }

    @Test
    public void testGivenFittedDetectorWhenSerializedThenRoundTrips() throws Exception {
        // Given
        double[] history = series(300, 0.5, 31);
        TimeSeriesAnomaly detector = detector(history);

        // When
        Path path = Write.object(detector);
        TimeSeriesAnomaly restored = (TimeSeriesAnomaly) Read.object(path);

        // Then
        assertEquals(detector.scale(), restored.scale());
        assertEquals(detector.sigma(), restored.sigma(), 1E-12);
        assertEquals(detector.threshold(), restored.threshold(), 1E-12);
        assertEquals(detector.score(52.0), restored.score(52.0), 1E-12);
        assertEquals(detector.toString(), restored.toString());
    }

    @Test
    public void testGivenFittedDetectorWhenPrintingThenSummaryMentionsScale() {
        double[] history = series(200, 0.5, 37);
        String summary = detector(history).toString();
        assertTrue(summary.contains("TimeSeriesAnomaly"));
        assertTrue(summary.contains("MAD"));
        assertTrue(summary.contains("threshold"));
    }
}
