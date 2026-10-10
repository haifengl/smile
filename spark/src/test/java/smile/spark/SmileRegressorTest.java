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
package smile.spark;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.apache.spark.ml.evaluation.RegressionEvaluator;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import smile.io.Paths;
import smile.model.rbf.RBF;
import smile.regression.RBFNetwork;

import static org.junit.jupiter.api.Assertions.*;

class SmileRegressorTest {

    private static SparkSession spark;

    @BeforeAll
    static void setUp() {
        spark = SparkTest.createSession("SmileRegressorTest");
    }

    @AfterAll
    static void tearDown() {
        if (spark != null) {
            spark.stop();
        }
    }

    @Test
    void testTrainEvaluateSaveLoad() throws Exception {
        String path = "file:///" + Paths.getTestData("libsvm/mushrooms.svm").toAbsolutePath().toString().replace('\\', '/');
        Dataset<Row> data = spark.read()
                .format("libsvm")
                .load(path);
        data.cache();

        SmileRegressor regressor = new SmileRegressor()
                .setTrainer((x, y) -> {
                    var neurons = RBF.fit(x, 30);
                    return RBFNetwork.fit(x, y, neurons);
                });

        RegressionEvaluator eval = new RegressionEvaluator()
                .setLabelCol("label")
                .setPredictionCol("prediction")
                .setMetricName("rmse");

        SmileRegressionModel model = regressor.fit(data);
        assertNotNull(model);

        Dataset<Row> predictions = model.transform(data);
        double rmse = eval.evaluate(predictions);
        assertTrue(rmse >= 0.0, "Expected valid RMSE, got " + rmse);

        Path tempDir = Files.createTempDirectory("smile-regressor-test-");
        String modelPath = tempDir.resolve("model").toAbsolutePath().toString();
        try {
            model.write().overwrite().save(modelPath);

            SmileRegressionModel loaded = SmileRegressionModel.load(modelPath);
            assertNotNull(loaded);

            double loadedRmse = eval.evaluate(loaded.transform(data));
            assertEquals(rmse, loadedRmse, 1e-6);
        } finally {
            try (var stream = Files.walk(tempDir)) {
                stream.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        }
    }

    @Test
    void testEstimatorSaveLoad() throws Exception {
        SmileRegressor regressor = new SmileRegressor()
                .setFeaturesCol("customFeatures")
                .setLabelCol("customLabel");

        Path tempDir = Files.createTempDirectory("smile-regressor-est-");
        String path = tempDir.resolve("estimator").toAbsolutePath().toString();
        try {
            regressor.write().overwrite().save(path);
            SmileRegressor loaded = SmileRegressor.load(path);
            assertEquals("customFeatures", loaded.getFeaturesCol());
            assertEquals("customLabel", loaded.getLabelCol());
        } finally {
            try (var stream = Files.walk(tempDir)) {
                stream.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        }
    }
}
