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
import smile.data.Tuple;
import smile.data.formula.Formula;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.datasets.ImageSegmentation;
import smile.datasets.Iris;
import smile.datasets.ProstateCancer;
import smile.io.Read;
import smile.io.Write;
import smile.math.MathEx;
import smile.regression.DataFrameRegression;

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
        assertEquals(resp.output(), ((Number) resp.output()).intValue());
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
    public void testNonTreeModelsAndSerializationExplain() throws Exception {
        // Given: non-tree regression (ols, ridge) and classification (logistic, lda) models, including ensemble
        var prostate = new ProstateCancer();
        var params = new Properties();
        var olsModel = Model.regression("ols", prostate.formula(), prostate.train(), prostate.test(), params);
        var ridgeEnsemble = Model.regression("ridge", prostate.formula(), prostate.train(), prostate.test(), params, 3, 1, true);

        var iris = new Iris();
        var logitModel = Model.classification("logistic", iris.formula(), iris.data(), null, params);
        var ldaEnsemble = Model.classification("lda", iris.formula(), iris.data(), null, params, 3, 1, true);

        // When: serialize and deserialize models via .sml
        Path olsPath = Write.object(olsModel);
        RegressionModel loadedOls = (RegressionModel) Read.object(olsPath);

        Path logitPath = Write.object(logitModel);
        ClassificationModel loadedLogit = (ClassificationModel) Read.object(logitPath);

        // Then: all support SHAP and return valid explanations
        assertTrue(loadedOls.supportsShap());
        assertTrue(ridgeEnsemble.supportsShap());
        assertTrue(loadedLogit.supportsShap());
        assertTrue(ldaEnsemble.supportsShap());

        var regRow = prostate.test().get(0);
        var olsResp = loadedOls.infer(regRow, false, true);
        assertNotNull(olsResp.explanations());
        assertTrue(olsResp.explanations().shap() instanceof double[]);
        assertEquals(loadedOls.schema().length(), ((double[]) olsResp.explanations().shap()).length);

        var ensRegResp = ridgeEnsemble.infer(regRow, false, true);
        assertTrue(ensRegResp.explanations().shap() instanceof double[]);
        assertEquals(ridgeEnsemble.schema().length(), ((double[]) ensRegResp.explanations().shap()).length);

        var clsRow = iris.data().get(0);
        var logitResp = loadedLogit.infer(clsRow, true, true);
        assertNotNull(logitResp.explanations());
        assertTrue(logitResp.explanations().shap() instanceof double[][]);
        double[][] logitShap = (double[][]) logitResp.explanations().shap();
        assertEquals(loadedLogit.numClasses(), logitShap.length);
        assertEquals(loadedLogit.schema().length(), logitShap[0].length);

        var ensClsResp = ldaEnsemble.infer(clsRow, true, true);
        assertTrue(ensClsResp.explanations().shap() instanceof double[][]);
    }

    @Test
    public void testUnsupportedModelExplain() {
        var schema = new StructType(new StructField("x1", DataTypes.DoubleType));
        var formula = Formula.lhs("y");
        DataFrameRegression stubRegression = new DataFrameRegression() {
            @Override
            public Formula formula() {
                return formula;
            }

            @Override
            public StructType schema() {
                return schema;
            }

            @Override
            public double predict(Tuple x) {
                return 1.0;
            }
        };
        var model = new RegressionModel("custom", schema, formula, stubRegression, null, null, null, new Properties());

        assertFalse(model.supportsShap());

        var row = Tuple.of(schema, new Object[]{2.0});
        var resp = model.infer(row, false, true);
        assertNotNull(resp);
        assertNotNull(resp.explanations());
        assertEquals("Not supported", resp.explanations().shap());
        assertTrue(resp.toJson().contains("\"shap\":\"Not supported\""));
    }
}
