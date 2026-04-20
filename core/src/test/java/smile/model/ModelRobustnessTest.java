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
import org.junit.jupiter.api.Test;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;
import smile.data.vector.DoubleVector;
import smile.data.vector.IntVector;
import smile.math.MathEx;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Targeted robustness tests for {@link Model} wrappers.
 */
public class ModelRobustnessTest {

    // ------------------------------------------------------------------
    // Helpers: tiny balanced datasets to keep tests fast
    // ------------------------------------------------------------------

    private static DataFrame tinyClassificationData() {
        return DataFrame.of(new double[][] {
                {0.0}, {1.0}, {2.0}, {3.0}, {4.0}, {5.0},
                {6.0}, {7.0}, {8.0}, {9.0}
        }, "x").add(new IntVector("y", new int[] {0, 0, 0, 0, 0, 1, 1, 1, 1, 1}));
    }

    private static DataFrame tinyRegressionData() {
        // y = 2x + 1 with 10 rows (enough for 2-fold CV and regression tree minimum node size)
        return DataFrame.of(new double[][] {
                {0.0}, {1.0}, {2.0}, {3.0}, {4.0},
                {5.0}, {6.0}, {7.0}, {8.0}, {9.0}
        }, "x").add(new DoubleVector("y",
                new double[] {1.0, 3.0, 5.0, 7.0, 9.0, 11.0, 13.0, 15.0, 17.0, 19.0}));
    }

    // ------------------------------------------------------------------
    // Schema, tags, and basic accessors
    // ------------------------------------------------------------------

    @Test
    public void testGivenClassificationWrapperWhenInspectingSchemaAndTagsThenResponseIsExcludedAndTagsAreCloned() {
        // Given
        DataFrame data = DataFrame.of(new double[][] {
                {0.0}, {1.0}, {2.0}, {3.0}, {4.0}, {5.0}
        }, "x").add(new IntVector("y", new int[] {0, 0, 0, 1, 1, 1}));
        Properties params = new Properties();
        params.setProperty("custom.tag", "original");

        // When
        ClassificationModel model = Model.classification("logistic", Formula.lhs("y"), data, null, params);
        params.setProperty("custom.tag", "mutated");
        model.setTag(Model.ID, "classification-model");

        // Then
        assertEquals("logistic", model.algorithm());
        assertArrayEquals(new String[] {"x"}, model.schema().names());
        assertEquals("original", model.getTag("custom.tag"),
                "Tags should be cloned; post-fit mutation to params must not affect model");
        assertEquals("classification-model", model.getTag(Model.ID));
        assertEquals("fallback", model.getTag("missing", "fallback"));
        assertEquals(2, model.numClasses());
    }

    @Test
    public void testGivenRegressionWrapperWhenInspectingSchemaAndTagsThenResponseIsExcludedAndTagsAreCloned() {
        // Given
        DataFrame data = tinyRegressionData();
        Properties params = new Properties();
        params.setProperty("custom.tag", "original");

        // When
        RegressionModel model = Model.regression("ols", Formula.lhs("y"), data, null, params);
        params.setProperty("custom.tag", "mutated");
        model.setTag(Model.VERSION, "1.0.0");

        // Then
        assertEquals("ols", model.algorithm());
        assertArrayEquals(new String[] {"x"}, model.schema().names());
        assertEquals("original", model.getTag("custom.tag"),
                "Tags should be cloned; post-fit mutation to params must not affect model");
        assertEquals("1.0.0", model.getTag(Model.VERSION));
        assertEquals("fallback", model.getTag("missing", "fallback"));
        assertEquals(19.0, model.predict(data.get(9)), 1E-10);
    }

    // ------------------------------------------------------------------
    // setTag / setProperty symmetry
    // ------------------------------------------------------------------

    @Test
    public void testGivenModelWhenUsingSetTagAndGetTagThenValuesAreSymmetric() {
        // Given
        DataFrame data = tinyRegressionData();
        RegressionModel model = Model.regression("ols", Formula.lhs("y"), data, null, new Properties());

        // When
        model.setTag("env", "production");

        // Then
        assertEquals("production", model.getTag("env"));
        assertEquals("production", model.getTag("env", "default"),
                "getTag(key, default) should return actual value when key is present");
        assertNull(model.getTag("missing"),
                "getTag with absent key should return null");
    }

    // ------------------------------------------------------------------
    // Metrics shape: no-test / with-test / no-CV / with-CV
    // ------------------------------------------------------------------

    @Test
    public void testGivenNoTestDataWhenTrainingClassifierThenTestMetricsIsNull() {
        // Given
        DataFrame data = tinyClassificationData();

        // When
        ClassificationModel model = Model.classification("logistic", Formula.lhs("y"), data, null, new Properties());

        // Then
        assertNotNull(model.train(), "Training metrics should always be present");
        assertNull(model.test(), "No test data => test metrics should be null");
        assertNull(model.validation(), "No CV => validation metrics should be null");
    }

    @Test
    public void testGivenTestDataWhenTrainingClassifierThenTestMetricsIsNonNull() {
        // Given
        DataFrame data = tinyClassificationData();
        // use the same data as test for simplicity
        DataFrame test = tinyClassificationData();

        // When
        ClassificationModel model = Model.classification("logistic", Formula.lhs("y"), data, test, new Properties());

        // Then
        assertNotNull(model.train(), "Training metrics should always be present");
        assertNotNull(model.test(), "Test data provided => test metrics should be non-null");
        assertNull(model.validation(), "No CV => validation metrics should be null");
    }

    @Test
    public void testGivenNoTestDataWhenTrainingRegressorThenTestMetricsIsNull() {
        // Given
        DataFrame data = tinyRegressionData();

        // When
        RegressionModel model = Model.regression("ols", Formula.lhs("y"), data, null, new Properties());

        // Then
        assertNotNull(model.train(), "Training metrics should always be present");
        assertNull(model.test(), "No test data => test metrics should be null");
        assertNull(model.validation(), "No CV => validation metrics should be null");
    }

    @Test
    public void testGivenTestDataWhenTrainingRegressorThenTestMetricsIsNonNull() {
        // Given
        DataFrame data = tinyRegressionData();

        // When
        RegressionModel model = Model.regression("ols", Formula.lhs("y"), data, data, new Properties());

        // Then
        assertNotNull(model.train(), "Training metrics should always be present");
        assertNotNull(model.test(), "Test data provided => test metrics should be non-null");
        assertNull(model.validation(), "No CV => validation metrics should be null");
    }

    // ------------------------------------------------------------------
    // CV path: kfold >= 2 with ensemble=false (retrain, not ensemble)
    // ------------------------------------------------------------------

    @Test
    public void testGivenKfoldWithoutEnsembleWhenTrainingClassifierThenValidationMetricsIsNonNull() {
        // Given
        MathEx.setSeed(19650218);
        DataFrame data = tinyClassificationData();

        // When: kfold=2, ensemble=false → retrain on full data, record CV metrics
        ClassificationModel model = Model.classification(
                "logistic", Formula.lhs("y"), data, null, new Properties(), 2, 1, false);

        // Then
        assertNotNull(model.validation(),
                "kfold=2 should produce non-null validation metrics even when ensemble=false");
        assertNotNull(model.train(), "Training metrics should be present");
        assertNull(model.test(), "No test data provided");
    }

    @Test
    public void testGivenKfoldWithoutEnsembleWhenTrainingRegressorThenValidationMetricsIsNonNull() {
        // Given
        MathEx.setSeed(19650218);
        DataFrame data = tinyRegressionData();

        // When
        RegressionModel model = Model.regression(
                "ols", Formula.lhs("y"), data, null, new Properties(), 2, 1, false);

        // Then
        assertNotNull(model.validation(),
                "kfold=2 should produce non-null validation metrics even when ensemble=false");
        assertNull(model.test(), "No test data provided");
    }

    // ------------------------------------------------------------------
    // Soft predict (posteriori probabilities) for ClassificationModel
    // ------------------------------------------------------------------

    @Test
    public void testGivenLogisticModelWhenPredictingWithPosterioriThenArrayIsFilled() {
        // Given
        DataFrame data = DataFrame.of(new double[][] {
                {0.0}, {1.0}, {2.0}, {3.0}, {4.0}, {5.0}
        }, "x").add(new IntVector("y", new int[] {0, 0, 0, 1, 1, 1}));
        ClassificationModel model = Model.classification("logistic", Formula.lhs("y"), data, null, new Properties());
        Tuple query = data.get(0);
        double[] posteriori = new double[model.numClasses()];

        // When
        int label = model.predict(query, posteriori);

        // Then
        assertTrue(label == 0 || label == 1,
                "Predicted label should be one of the training classes");
        double sum = 0;
        for (double p : posteriori) {
            assertTrue(p >= 0.0 && p <= 1.0,
                    "Each posterior probability should be in [0,1], got " + p);
            sum += p;
        }
        assertEquals(1.0, sum, 1E-9,
                "Posterior probabilities should sum to 1, got " + sum);
    }

    // ------------------------------------------------------------------
    // Additional algorithm branches
    // ------------------------------------------------------------------

    @Test
    public void testGivenCartAlgorithmWhenTrainingClassifierThenModelIsReturnedWithTrainMetrics() {
        // Given
        DataFrame data = tinyClassificationData();

        // When
        ClassificationModel model = Model.classification("cart", Formula.lhs("y"), data, null, new Properties());

        // Then
        assertEquals("cart", model.algorithm());
        assertNotNull(model.train(), "Training metrics should be present for 'cart'");
        assertNotNull(model.classifier(), "Underlying classifier should not be null");
    }

    @Test
    public void testGivenCartAlgorithmWhenTrainingRegressorThenModelIsReturnedWithTrainMetrics() {
        // Given
        DataFrame data = tinyRegressionData();

        // When
        RegressionModel model = Model.regression("cart", Formula.lhs("y"), data, null, new Properties());

        // Then
        assertEquals("cart", model.algorithm());
        assertNotNull(model.train(), "Training metrics should be present for 'cart'");
        assertNotNull(model.regression(), "Underlying regressor should not be null");
    }

    // ------------------------------------------------------------------
    // Unsupported algorithm
    // ------------------------------------------------------------------

    @Test
    public void testGivenUnsupportedClassificationAlgorithmWhenTrainingThenThrowsMeaningfulException() {
        // Given
        DataFrame data = tinyClassificationData();

        // When
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Model.classification("unsupported", Formula.lhs("y"), data, null, new Properties()));

        // Then
        assertTrue(exception.getMessage().contains("Unsupported algorithm"));
    }

    @Test
    public void testGivenUnsupportedRegressionAlgorithmWhenTrainingThenThrowsMeaningfulException() {
        // Given
        DataFrame data = tinyRegressionData();

        // When
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Model.regression("unsupported", Formula.lhs("y"), data, null, new Properties()));

        // Then
        assertTrue(exception.getMessage().contains("Unsupported algorithm"));
    }
}
