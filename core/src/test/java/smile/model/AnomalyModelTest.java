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

import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import smile.anomaly.IsolationForest;
import smile.anomaly.LOF;
import smile.anomaly.SVM;
import smile.data.Tuple;
import smile.datasets.Iris;
import smile.io.Read;
import smile.io.Write;
import smile.math.MathEx;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AnomalyModel} and {@link Model#anomaly}.
 */
public class AnomalyModelTest {

    @BeforeEach
    public void setUp() {
        MathEx.setSeed(19650218);
    }

    @Test
    public void testGivenIsolationForestWhenTrainedAndSerializedThenInfersAnomalyAndScore() throws Exception {
        // Given
        var iris = new Iris();
        var params = new Properties();
        params.setProperty("smile.isolation_forest.trees", "50");
        params.setProperty("smile.isolation_forest.max_depth", "8");
        params.setProperty("smile.isolation_forest.sampling_rate", "0.7");
        params.setProperty("smile.isolation_forest.extension_level", "1");
        params.setProperty("smile.anomaly.threshold", "0.55");

        // When
        var model = Model.anomaly("isolation-forest", iris.formula(), iris.data(), params);

        // Then
        assertEquals("isolation-forest", model.algorithm());
        assertEquals(4, model.schema().length());
        assertEquals(0.55, model.threshold(), 1E-12);
        assertFalse(model.isOnline());
        assertFalse(model.supportsShap());
        assertInstanceOf(IsolationForest.class, model.detector());

        var row = iris.data().get(0);
        Prediction pred = model.infer(row);
        assertInstanceOf(Boolean.class, pred.output());
        assertNotNull(pred.score());
        assertTrue(pred.score() > 0.0 && pred.score() <= 1.0);
        assertEquals(pred.score() > 0.55, pred.output());
        assertEquals(pred.score(), model.score(row), 1E-12);
        assertEquals(pred.output(), model.predict(row));

        // Explain returns Not supported
        Prediction explained = model.infer(row, false, true);
        assertNotNull(explained.explanations());
        assertEquals("Not supported", explained.explanations().shap());

        // Extreme outlier should have high anomaly score and be flagged true
        Tuple outlier = Tuple.of(model.schema(), new Object[]{100.0, 100.0, 100.0, 100.0});
        Prediction outlierPred = model.infer(outlier);
        assertEquals(Boolean.TRUE, outlierPred.output());
        assertTrue(outlierPred.score() > 0.55);

        String json = outlierPred.toJson();
        assertTrue(json.contains("\"prediction\":true"));
        assertTrue(json.contains("\"score\":"));
        assertTrue(outlierPred.toString().startsWith("true "));

        // Batch infer, score, and predict
        Prediction[] batch = model.infer(iris.data());
        assertEquals(iris.data().size(), batch.length);
        assertEquals(iris.data().size(), model.score(iris.data()).length);
        assertEquals(iris.data().size(), model.predict(iris.data()).length);

        // Serialization round-trip
        Path path = Write.object(model);
        AnomalyModel loaded = (AnomalyModel) Read.object(path);
        Prediction loadedPred = loaded.infer(row);
        assertEquals(pred.output(), loadedPred.output());
        assertEquals(pred.score(), loadedPred.score(), 1E-12);
    }

    @Test
    public void testGivenLOFWithTransformWhenTrainedAndSerializedThenInfersAnomalyAndScore() throws Exception {
        // Given
        var iris = new Iris();
        var params = new Properties();
        params.setProperty("smile.lof.k", "15");
        params.setProperty("smile.lof.threshold", "1.5");
        params.setProperty("smile.feature.transform", "standardizer");

        // When
        var model = Model.anomaly("lof", iris.formula(), iris.data(), params);

        // Then
        assertEquals("lof", model.algorithm());
        assertEquals(1.5, model.threshold(), 1E-12);
        assertNotNull(model.transform());
        assertInstanceOf(LOF.class, model.detector());

        var row = iris.data().get(0);
        Prediction pred = model.infer(row);
        assertInstanceOf(Boolean.class, pred.output());
        assertNotNull(pred.score());
        assertTrue(pred.score() > 0.0);
        assertEquals(pred.score() > 1.5, pred.output());

        // Extreme outlier should have LOF score >> 1.5
        Tuple outlier = Tuple.of(model.schema(), new Object[]{50.0, 50.0, 50.0, 50.0});
        Prediction outlierPred = model.infer(outlier);
        assertEquals(Boolean.TRUE, outlierPred.output());
        assertTrue(outlierPred.score() > 1.5);

        // Serialization round-trip
        Path path = Write.object(model);
        AnomalyModel loaded = (AnomalyModel) Read.object(path);
        Prediction loadedPred = loaded.infer(row);
        assertEquals(pred.output(), loadedPred.output());
        assertEquals(pred.score(), loadedPred.score(), 1E-12);
    }

    @Test
    public void testGivenOCSVMWhenTrainedAndSerializedThenInfersAnomalyAndScore() throws Exception {
        // Given
        var iris = new Iris();
        var featuresOnly = iris.data().drop("class");
        var params = new Properties();
        params.setProperty("smile.svm.kernel", "Gaussian(1.0)");
        params.setProperty("smile.svm.nu", "0.1");
        params.setProperty("smile.svm.tolerance", "1E-3");

        // When: formula is null -> defaults to all columns
        var model = Model.anomaly("ocsvm", null, featuresOnly, params);

        // Then
        assertEquals("ocsvm", model.algorithm());
        assertEquals(4, model.schema().length());
        assertEquals(0.0, model.threshold(), 1E-12);
        assertInstanceOf(SVM.class, model.detector());

        var row = featuresOnly.get(0);
        Prediction pred = model.infer(row);
        assertInstanceOf(Boolean.class, pred.output());
        assertNotNull(pred.score());
        assertEquals(pred.score() < 0.0, pred.output());
        assertEquals(pred.score(), model.score(row), 1E-12);
        assertEquals(pred.output(), model.predict(row));

        // Distant point outside the Gaussian support should have negative decision value -> anomaly = true
        Tuple outlier = Tuple.of(model.schema(), new Object[]{100.0, 100.0, 100.0, 100.0});
        Prediction outlierPred = model.infer(outlier);
        assertEquals(Boolean.TRUE, outlierPred.output());
        assertTrue(outlierPred.score() < 0.0);

        // Serialization round-trip
        Path path = Write.object(model);
        AnomalyModel loaded = (AnomalyModel) Read.object(path);
        Prediction loadedPred = loaded.infer(row);
        assertEquals(pred.output(), loadedPred.output());
        assertEquals(pred.score(), loadedPred.score(), 1E-12);
    }

    @Test
    public void testGivenUnsupportedAnomalyAlgorithmWhenTrainedThenThrowsIllegalArgumentException() throws Exception {
        var iris = new Iris();
        assertThrows(IllegalArgumentException.class, () ->
                Model.anomaly("unknown-anomaly", iris.formula(), iris.data(), new Properties()));
    }
}
