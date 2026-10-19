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
import smile.data.formula.Formula;
import smile.datasets.Iris;
import smile.datasets.ProstateCancer;
import smile.io.Read;
import smile.io.Write;
import smile.math.MathEx;
import smile.vq.Neighborhood;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link VectorQuantizerModel}, {@link Model#quantizer}, and online {@link Model#update} methods.
 */
public class VectorQuantizerModelTest {

    @BeforeEach
    public void setUp() {
        MathEx.setSeed(19650218);
    }

    @Test
    public void testSOMAndSerialization() throws Exception {
        // Given
        var iris = new Iris();
        var params = new Properties();
        params.setProperty("smile.som.nrow", "4");
        params.setProperty("smile.som.ncol", "4");
        params.setProperty("smile.som.epochs", "5");
        params.setProperty("smile.som.alpha", "linear(0.1, 750, 0.01)");
        params.setProperty("smile.som.neighborhood", "Gaussian(1.0, 200)");

        // When
        var model = Model.quantizer("som", iris.formula(), iris.data(), params);

        // Then
        assertEquals("som", model.algorithm());
        assertEquals(4, model.schema().length());
        assertTrue(model.isOnline());
        assertFalse(model.supportsShap());

        var row = iris.data().get(0);
        Prediction pred = model.infer(row);
        assertInstanceOf(int[].class, pred.output());
        int[] cell = (int[]) pred.output();
        assertEquals(2, cell.length);
        assertTrue(cell[0] >= 0 && cell[0] < 4);
        assertTrue(cell[1] >= 0 && cell[1] < 4);
        assertNotNull(pred.centroid());
        assertEquals(4, pred.centroid().length);
        assertNotNull(pred.error());
        assertTrue(pred.error() >= 0.0);

        String json = pred.toJson();
        assertTrue(json.contains("\"prediction\":[" + cell[0] + "," + cell[1] + "]"));
        assertTrue(json.contains("\"centroid\":["));
        assertTrue(json.contains("\"error\":"));

        String text = pred.toString();
        assertTrue(text.startsWith("[" + cell[0] + ", " + cell[1] + "] "));

        // Online update and serialization round-trip
        model.update(row);
        Path path = Write.object(model);
        VectorQuantizerModel loaded = (VectorQuantizerModel) Read.object(path);
        Prediction loadedPred = loaded.infer(row);
        assertArrayEquals((int[]) model.infer(row).output(), (int[]) loadedPred.output());
        assertArrayEquals(model.infer(row).centroid(), loadedPred.centroid(), 1E-12);
        assertEquals(model.infer(row).error(), loadedPred.error(), 1E-12);
    }

    @Test
    public void testNeuralGas() throws Exception {
        // Given
        var iris = new Iris();
        var params = new Properties();
        params.setProperty("smile.neural_gas.k", "6");
        params.setProperty("smile.neural_gas.epochs", "5");

        // When
        var model = Model.quantizer("neural-gas", iris.formula(), iris.data(), params);

        // Then
        assertEquals("neural-gas", model.algorithm());
        assertTrue(model.isOnline());

        var row = iris.data().get(0);
        Prediction pred = model.infer(row);
        assertInstanceOf(Integer.class, pred.output());
        int idx = (Integer) pred.output();
        assertTrue(idx >= 0 && idx < 6);
        assertNotNull(pred.centroid());
        assertEquals(4, pred.centroid().length);
        assertNotNull(pred.error());
        assertTrue(pred.error() >= 0.0);

        // Batch infer and online update
        Prediction[] batch = model.infer(iris.data());
        assertEquals(iris.data().size(), batch.length);
        model.update(iris.data());
    }

    @Test
    public void testGrowingNeuralGasWithTransform() throws Exception {
        // Given
        var iris = new Iris();
        var params = new Properties();
        params.setProperty("smile.gng.epochs", "5");
        params.setProperty("smile.gng.lambda", "50");
        params.setProperty("smile.feature.transform", "standardizer");

        // When
        var model = Model.quantizer("growing-neural-gas", iris.formula(), iris.data(), params);

        // Then
        assertEquals("growing-neural-gas", model.algorithm());
        assertNotNull(model.transform());
        assertTrue(model.isOnline());

        var row = iris.data().get(0);
        Prediction pred = model.infer(row, false, true);
        assertInstanceOf(Integer.class, pred.output());
        assertNotNull(pred.centroid());
        assertEquals(4, pred.centroid().length);
        assertNotNull(pred.error());
        assertNotNull(pred.explanations());
        assertEquals("Not supported", pred.explanations().shap());
    }

    @Test
    public void testNeuralMap() throws Exception {
        // Given
        var iris = new Iris();
        var params = new Properties();
        params.setProperty("smile.neural_map.radius", "1.5");
        params.setProperty("smile.neural_map.epochs", "5");

        // When
        var model = Model.quantizer("neural-map", iris.formula(), iris.data(), params);

        // Then
        assertEquals("neural-map", model.algorithm());
        var row = iris.data().get(0);
        Prediction pred = model.infer(row);
        assertInstanceOf(Integer.class, pred.output());
        assertTrue((Integer) pred.output() >= 0);
        assertNotNull(pred.centroid());
        assertNotNull(pred.error());
    }

    @Test
    public void testBIRCH() throws Exception {
        // Given
        var iris = new Iris();
        var featuresOnly = iris.data().drop("class");
        var params = new Properties();
        params.setProperty("smile.birch.branching", "5");
        params.setProperty("smile.birch.leaves", "5");
        params.setProperty("smile.birch.radius", "1.0");

        // When: formula is null -> defaults to all columns
        var model = Model.quantizer("birch", null, featuresOnly, params);

        // Then
        assertEquals("birch", model.algorithm());
        assertEquals(4, model.schema().length());
        var row = featuresOnly.get(0);
        Prediction pred = model.infer(row);
        assertInstanceOf(Integer.class, pred.output());
        assertTrue((Integer) pred.output() >= 0);
        assertNotNull(pred.centroid());
        assertEquals(4, pred.centroid().length);
        assertNotNull(pred.error());
    }

    @Test
    public void testNeighborhoodParser() {
        assertNotNull(Neighborhood.of("square(2)"));
        assertNotNull(Neighborhood.of("bubble(3)"));
        assertNotNull(Neighborhood.of("triangle(1.5, 500)"));
        assertNotNull(Neighborhood.of("Gaussian(1.0, 1000)"));
        assertNotNull(Neighborhood.of("MexicanHat(1.0, 1000)"));
        assertThrows(IllegalArgumentException.class, () -> Neighborhood.of("unknown(1)"));
    }

    @Test
    public void testSupervisedOnlineUpdate() throws Exception {
        // Given: online ClassificationModel (logistic) and online RegressionModel (recursive OLS)
        var iris = new Iris();
        var clfModel = Model.classification("logistic", iris.formula(), iris.data(), null, new Properties());
        assertTrue(clfModel.isOnline());
        clfModel.update(iris.data().get(0));
        clfModel.update(iris.data().get(0), 0);

        var prostate = new ProstateCancer();
        var regParams = new Properties();
        regParams.setProperty("smile.ols.recursive", "true");
        var regModel = Model.regression("ols", prostate.formula(), prostate.train(), null, regParams);
        assertTrue(regModel.isOnline());
        regModel.update(prostate.test().get(0));
        regModel.update(prostate.test().get(0), 1.5);

        // Non-online model throws UnsupportedOperationException
        var rfParams = new Properties();
        rfParams.setProperty("smile.random_forest.trees", "10");
        var rfModel = Model.classification("random-forest", iris.formula(), iris.data(), null, rfParams);
        assertFalse(rfModel.isOnline());
        assertThrows(UnsupportedOperationException.class, () -> rfModel.update(iris.data().get(0)));
    }
}
