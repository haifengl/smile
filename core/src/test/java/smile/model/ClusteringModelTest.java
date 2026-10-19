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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.datasets.Iris;
import smile.io.Read;
import smile.io.Write;
import smile.math.MathEx;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ClusteringModel} and {@link Model#clustering}.
 */
public class ClusteringModelTest {

    @BeforeEach
    public void setUp() {
        MathEx.setSeed(19650218);
    }

    @Test
    public void testGivenIrisDataWhenTrainingKMeansWithFormulaThenPredictsAndSerializes() throws Exception {
        // Given
        var iris = new Iris();
        var params = new Properties();
        params.setProperty("smile.clustering.k", "3");
        params.setProperty("smile.clustering.iterations", "50");
        params.setProperty("smile.clustering.runs", "2");

        // When
        ClusteringModel model = Model.clustering("kmeans", iris.formula(), iris.data(), params);

        // Then
        assertEquals("kmeans", model.algorithm());
        assertEquals(3, model.k());
        assertEquals(4, model.schema().length());
        assertTrue(model.distortion() > 0.0);
        assertFalse(model.supportsShap());

        int cluster = model.predict(iris.data().get(0));
        assertTrue(cluster >= 0 && cluster < 3);

        Prediction pred = model.infer(iris.data().get(0));
        assertEquals(cluster, pred.output().intValue());
        assertNull(pred.probabilities());
        assertNotNull(pred.centroid());
        assertEquals(4, pred.centroid().length);
        assertArrayEquals(model.center(cluster), pred.centroid(), 1E-12);
        assertTrue(pred.toJson().contains("\"centroid\":["));
        assertTrue(pred.toString().startsWith(cluster + " "));

        // Verify explain=true returns "Not supported"
        Prediction explained = model.infer(iris.data().get(0), false, true);
        assertNotNull(explained.explanations());
        assertEquals("Not supported", explained.explanations().shap());

        // Verify serialization round-trip
        Path path = Write.object(model);
        try {
            ClusteringModel loaded = (ClusteringModel) Read.object(path);
            assertEquals(model.k(), loaded.k());
            assertEquals(cluster, loaded.predict(iris.data().get(0)));
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    public void testGivenNumericDataFrameWhenTrainingWithNullFormulaThenUsesAllColumns() throws Exception {
        // Given
        var iris = new Iris();
        DataFrame featuresOnly = iris.formula().x(iris.data());
        var params = new Properties();
        params.setProperty("smile.clustering.k", "3");

        // When
        ClusteringModel model = Model.clustering("kmeans", null, featuresOnly, params);

        // Then
        assertEquals(4, model.schema().length());
        Prediction[] predictions = model.infer(featuresOnly);
        assertEquals(featuresOnly.size(), predictions.length);
        for (Prediction p : predictions) {
            int c = p.output().intValue();
            assertTrue(c >= 0 && c < 3);
            assertArrayEquals(model.center(c), p.centroid(), 1E-12);
        }
    }

    @Test
    public void testGivenIrisDataWhenTrainingXMeansGMeansAndDAThenFitsModels() throws Exception {
        // Given
        var iris = new Iris();
        var params = new Properties();
        params.setProperty("smile.clustering.k", "6");
        params.setProperty("smile.clustering.iterations", "50");

        // When / Then: X-Means
        ClusteringModel xmeans = Model.clustering("xmeans", iris.formula(), iris.data(), params);
        assertEquals("xmeans", xmeans.algorithm());
        assertTrue(xmeans.k() >= 1 && xmeans.k() <= 6);
        assertEquals(4, xmeans.infer(iris.data().get(0)).centroid().length);

        // When / Then: G-Means
        ClusteringModel gmeans = Model.clustering("gmeans", iris.formula(), iris.data(), params);
        assertEquals("gmeans", gmeans.algorithm());
        assertTrue(gmeans.k() >= 1 && gmeans.k() <= 6);
        assertEquals(4, gmeans.infer(iris.data().get(0)).centroid().length);

        // When / Then: Deterministic Annealing
        var daParams = new Properties();
        daParams.setProperty("smile.deterministic_annealing.k", "3");
        daParams.setProperty("smile.deterministic_annealing.alpha", "0.8");
        daParams.setProperty("smile.deterministic_annealing.iterations", "30");
        ClusteringModel da = Model.clustering("deterministic-annealing", iris.formula(), iris.data(), daParams);
        assertEquals("deterministic-annealing", da.algorithm());
        assertTrue(da.k() >= 1 && da.k() <= 3);
        assertEquals(4, da.infer(iris.data().get(0)).centroid().length);
    }

    @Test
    public void testGivenFeatureTransformWhenTrainingClusteringModelThenAppliesTransformAtInference() throws Exception {
        // Given
        var iris = new Iris();
        var params = new Properties();
        params.setProperty("smile.clustering.k", "3");
        params.setProperty("smile.feature.transform", "standardizer");

        // When
        ClusteringModel model = Model.clustering("kmeans", Formula.lhs("class"), iris.data(), params);

        // Then
        assertNotNull(model.transform());
        int[] clusters = model.predict(iris.data());
        assertEquals(iris.data().size(), clusters.length);
    }
}
