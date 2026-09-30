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

import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import smile.datasets.ImageSegmentation;
import smile.datasets.ProstateCancer;
import smile.math.MathEx;

import static org.junit.jupiter.api.Assertions.*;

public class PredictionTest {

    @BeforeEach
    public void setUp() {
        MathEx.setSeed(19650218);
    }

    @Test
    public void testPredictionJson() {
        var resp = new Prediction(2, new double[]{0.0521, 0.1867, 0.7612});
        assertEquals(2, resp.output());
        assertEquals("{\"prediction\":2,\"probabilities\":[0.052,0.187,0.761]}", resp.toJson());

        var expRegression = new Explanations(new double[]{0.1234, -0.5678});
        var respReg = new Prediction(42.5, null, expRegression);
        assertEquals(42.5, respReg.output());
        assertEquals("{\"prediction\":42.5,\"explanations\":{\"shap\":[0.123,-0.568]}}", respReg.toJson());

        var expClass = new Explanations(new double[][]{{0.1, 0.2}, {-0.3, 0.4}});
        var respClass = new Prediction(0, new double[]{0.8, 0.2}, expClass);
        assertEquals(0, respClass.output());
        assertEquals("{\"prediction\":0,\"probabilities\":[0.800,0.200],\"explanations\":{\"shap\":[[0.100,0.200],[-0.300,0.400]]}}", respClass.toJson());

        var expUnsupported = new Explanations("Not supported");
        var respUnsup = new Prediction(1, null, expUnsupported);
        assertEquals(1, respUnsup.output());
        assertEquals("{\"prediction\":1,\"explanations\":{\"shap\":\"Not supported\"}}", respUnsup.toJson());
    }

    @Test
    public void testClassificationModelExplain() throws Exception {
        var segment = new ImageSegmentation();
        var params = new Properties();
        params.setProperty("smile.random_forest.trees", "10");
        params.setProperty("smile.random_forest.max_nodes", "20");
        var model = Model.classification("random-forest", segment.formula(), segment.train(), segment.test(), params);

        assertTrue(model.supportsShap());

        var row = segment.test().get(0);
        var resp = model.infer(row, true, true);
        assertNotNull(resp);
        assertEquals(resp.output(), Integer.valueOf(resp.output().intValue()));
        assertNotNull(resp.probabilities());
        assertNotNull(resp.explanations());
        assertTrue(resp.explanations().shap() instanceof double[][]);

        double[][] shapMat = (double[][]) resp.explanations().shap();
        assertEquals(model.numClasses(), shapMat.length);
        assertEquals(model.schema().length(), shapMat[0].length);

        String json = resp.toJson();
        assertTrue(json.contains("\"prediction\":"));
        assertTrue(json.contains("\"probabilities\":"));
        assertTrue(json.contains("\"explanations\":{\"shap\":[["));

        // Batch infer
        var batchResponses = model.infer(segment.test(), true, true);
        assertEquals(segment.test().size(), batchResponses.length);
        assertNotNull(batchResponses[0].explanations());
    }

    @Test
    public void testRegressionModelExplain() throws Exception {
        var prostate = new ProstateCancer();
        var params = new Properties();
        params.setProperty("smile.random_forest.trees", "10");
        params.setProperty("smile.random_forest.max_nodes", "20");
        var model = Model.regression("random-forest", prostate.formula(), prostate.train(), prostate.test(), params);

        assertTrue(model.supportsShap());

        var row = prostate.test().get(0);
        var resp = model.infer(row, false, true);
        assertNotNull(resp);
        assertNotNull(resp.output());
        assertNull(resp.probabilities());
        assertNotNull(resp.explanations());
        assertTrue(resp.explanations().shap() instanceof double[]);

        double[] shapArr = (double[]) resp.explanations().shap();
        assertEquals(model.schema().length(), shapArr.length);

        String json = resp.toJson();
        assertTrue(json.contains("\"prediction\":"));
        assertFalse(json.contains("\"probabilities\":"));
        assertTrue(json.contains("\"explanations\":{\"shap\":["));
    }

    @Test
    public void testUnsupportedModelExplain() throws Exception {
        var prostate = new ProstateCancer();
        var params = new Properties();
        var model = Model.regression("ols", prostate.formula(), prostate.train(), prostate.test(), params);

        assertFalse(model.supportsShap());

        var row = prostate.test().get(0);
        var resp = model.infer(row, false, true);
        assertNotNull(resp);
        assertNotNull(resp.explanations());
        assertEquals("Not supported", resp.explanations().shap());
        assertTrue(resp.toJson().contains("\"shap\":\"Not supported\""));
    }
}
