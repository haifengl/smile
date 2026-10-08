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
import org.apache.spark.ml.evaluation.BinaryClassificationEvaluator;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import smile.classification.RBFNetwork;
import smile.io.Paths;
import smile.model.rbf.RBF;

import static org.apache.spark.sql.functions.col;
import static org.junit.jupiter.api.Assertions.*;

class SmileClassifierTest {

    private static SparkSession spark;

    @BeforeAll
    static void setUp() {
        spark = SparkSession.builder()
                .master("local[*]")
                .appName("SmileClassifierTest")
                .getOrCreate();
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
                .load(path)
                .withColumn("label", col("label").minus(1)); // transform label from 1/2 to 0/1
        data.cache();

        SmileClassifier classifier = new SmileClassifier()
                .setTrainer((x, y) -> {
                    var neurons = RBF.fit(x, 30);
                    return RBFNetwork.fit(x, y, neurons);
                });

        BinaryClassificationEvaluator eval = new BinaryClassificationEvaluator()
                .setLabelCol("label")
                .setRawPredictionCol("rawPrediction");

        SmileClassificationModel model = classifier.fit(data);
        assertNotNull(model);
        assertEquals(2, model.numClasses());

        Dataset<Row> predictions = model.transform(data);
        double metric = eval.evaluate(predictions);
        assertTrue(metric > 0.8, "Expected AUC > 0.8, got " + metric);

        Path tempDir = Files.createTempDirectory("smile-classifier-test-");
        String modelPath = tempDir.resolve("model").toAbsolutePath().toString();
        try {
            model.write().overwrite().save(modelPath);

            SmileClassificationModel loaded = SmileClassificationModel.load(modelPath);
            assertEquals(model.numClasses(), loaded.numClasses());

            double loadedMetric = eval.evaluate(loaded.transform(data));
            assertEquals(metric, loadedMetric, 1e-6);
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
        SmileClassifier classifier = new SmileClassifier()
                .setFeaturesCol("customFeatures")
                .setLabelCol("customLabel")
                .setNumClasses(3);

        Path tempDir = Files.createTempDirectory("smile-estimator-test-");
        String path = tempDir.resolve("estimator").toAbsolutePath().toString();
        try {
            classifier.write().overwrite().save(path);
            SmileClassifier loaded = SmileClassifier.load(path);
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
