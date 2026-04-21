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
package smile.feature.importance;

import org.junit.jupiter.api.Test;
import smile.classification.DecisionTree;
import smile.classification.RandomForest;
import smile.data.Tuple;
import smile.datasets.CPU;
import smile.datasets.Iris;
import smile.datasets.WeatherNominal;
import smile.model.cart.SplitRule;
import smile.regression.RegressionTree;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests exercising SHAP on real trained models.
 */
public class ShapIntegrationTest {

    @Test
    public void testGivenClassificationRandomForestWhenComputingDataFrameShapThenLengthIsNumFeaturesTimesNumClasses() throws Exception {
        // Given
        var iris = new Iris();
        var options = new RandomForest.Options(10, 2, SplitRule.GINI, 20, 100, 5, 1.0, null, null, null);
        RandomForest model = RandomForest.fit(iris.formula(), iris.data(), options);
        int p = model.schema().length();
        int k = 3;  // Iris has 3 classes

        // When
        double[] shap = model.shap(iris.data());

        // Then — SHAP layout: p * k values (k per feature)
        assertEquals(p * k, shap.length);
        for (double v : shap) {
            assertTrue(v >= 0.0, "aggregated SHAP value must be non-negative: " + v);
        }
    }

    @Test
    public void testGivenClassificationRandomForestWhenComputingTupleShapThenLengthIsNumFeaturesTimesNumClasses() throws Exception {
        // Given
        var iris = new Iris();
        var options = new RandomForest.Options(10, 2, SplitRule.GINI, 20, 100, 5, 1.0, null, null, null);
        RandomForest model = RandomForest.fit(iris.formula(), iris.data(), options);
        int p = model.schema().length();
        int k = 3;
        Tuple firstRow = iris.data().get(0);

        // When — single-instance SHAP (signed, not aggregated)
        double[] shap = model.shap(firstRow);

        // Then
        assertEquals(p * k, shap.length);
        for (double v : shap) assertTrue(Double.isFinite(v));
    }

    @Test
    public void testGivenRegressionRandomForestWhenComputingDataFrameShapThenLengthIsNumFeatures() throws Exception {
        // Given
        var cpu = new CPU();
        var options = new smile.regression.RandomForest.Options(10, 2, 20, 100, 5, 1.0, null, null);
        smile.regression.RandomForest model = smile.regression.RandomForest.fit(cpu.formula(), cpu.data(), options);
        int p = model.schema().length();

        // When
        double[] shap = model.shap(cpu.data());

        // Then
        assertEquals(p, shap.length);
        for (double v : shap) {
            assertTrue(v >= 0.0, "aggregated SHAP value must be non-negative: " + v);
        }
    }

    @Test
    public void testGivenRegressionRandomForestWhenComputingTupleShapThenLengthIsNumFeatures() throws Exception {
        // Given
        var cpu = new CPU();
        var options = new smile.regression.RandomForest.Options(10, 2, 20, 100, 5, 1.0, null, null);
        smile.regression.RandomForest model = smile.regression.RandomForest.fit(cpu.formula(), cpu.data(), options);
        int p = model.schema().length();
        Tuple firstRow = cpu.data().get(0);

        // When
        double[] shap = model.shap(firstRow);

        // Then
        assertEquals(p, shap.length);
        for (double v : shap) assertTrue(Double.isFinite(v));
    }

    @Test
    public void testGivenClassificationDecisionTreeWhenComputingTupleShapThenOutputLengthIsCorrect() throws Exception {
        // Given
        var iris = new Iris();
        var options = new DecisionTree.Options(SplitRule.GINI, 20, 100, 5);
        DecisionTree model = DecisionTree.fit(iris.formula(), iris.data(), options);
        int p = model.schema().length();
        int k = 3;
        Tuple row = iris.data().get(0);

        // When
        double[] shap = model.shap(row);

        // Then — length sanity and finiteness
        assertEquals(p * k, shap.length);
        for (double v : shap) assertTrue(Double.isFinite(v));
    }

    @Test
    public void testGivenRegressionDecisionTreeWhenComputingTupleShapThenAllValuesAreFinite() throws Exception {
        // Given
        var cpu = new CPU();
        var options = new RegressionTree.Options(20, 100, 5);
        RegressionTree model = RegressionTree.fit(cpu.formula(), cpu.data(), options);
        int p = model.schema().length();
        Tuple row = cpu.data().get(0);

        // When
        double[] shap = model.shap(row);

        // Then
        assertEquals(p, shap.length);
        for (double v : shap) assertTrue(Double.isFinite(v));
    }

    @Test
    public void testGivenBinaryClassificationWhenComputingDataFrameShapThenLengthIsNumFeaturesTimesTwo() throws Exception {
        // Given — WeatherNominal is a binary classification dataset (play: yes/no)
        var weather = new WeatherNominal();
        var options = new RandomForest.Options(10, 2, SplitRule.GINI, 20, 100, 5, 1.0, null, null, null);
        RandomForest model = RandomForest.fit(weather.formula(), weather.data(), options);
        int p = model.schema().length();
        int k = 2;  // binary

        // When
        double[] shap = model.shap(weather.data());

        // Then
        assertEquals(p * k, shap.length);
        for (double v : shap) {
            assertTrue(v >= 0.0, "aggregated SHAP value must be non-negative: " + v);
        }
    }
}
