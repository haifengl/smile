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

/**
 * Integration between SMILE machine learning and Apache Spark.
 *
 * <p>This package bridges the local, in-memory SMILE data model with the
 * distributed Spark SQL and Spark ML abstractions. It is organized around four
 * capabilities:
 *
 * <ul>
 *   <li><b>DataFrame conversion</b> — move data between a Spark SQL
 *       {@code Dataset<Row>} and a SMILE {@link smile.data.DataFrame} in either
 *       direction, so data prepared in Spark can be fed to SMILE algorithms and
 *       SMILE results can be pushed back to Spark.</li>
 *   <li><b>Spark ML pipeline integration</b> — drop any SMILE classifier or
 *       regression model into a Spark ML {@code Pipeline} through the
 *       {@code Estimator}/{@code Model} pairs in this package.</li>
 *   <li><b>Distributed hyperparameter optimization</b> — evaluate a list of
 *       hyperparameter configurations in parallel across Spark executors.</li>
 *   <li><b>Type mapping</b> — translate Spark SQL types to and from SMILE data
 *       types, including nullability, arrays, structs, and Spark ML vectors.</li>
 * </ul>
 *
 * <h2>Key classes</h2>
 * <dl>
 *   <dt>{@link smile.spark.SparkDataFrames}</dt>
 *   <dd>Converts a Spark {@code Dataset<Row>} into a SMILE
 *       {@link smile.data.DataFrame}, either by collecting it to the driver or
 *       by streaming it lazily as {@link smile.data.Tuple}s.</dd>
 *
 *   <dt>{@link smile.spark.SmileDataFrames}</dt>
 *   <dd>Converts a SMILE {@link smile.data.DataFrame} into a distributed Spark
 *       {@code Dataset<Row>}.</dd>
 *
 *   <dt>{@link smile.spark.SparkRowTuple}</dt>
 *   <dd>Adapts a Spark {@code Row} to the SMILE {@link smile.data.Tuple}
 *       interface, delegating field access to the underlying row.</dd>
 *
 *   <dt>{@link smile.spark.DataTypeUtils}</dt>
 *   <dd>Bidirectional mapping between Spark SQL data types and SMILE data
 *       types, used to translate schemas during conversion.</dd>
 *
 *   <dt>{@link smile.spark.SmileClassifier} /
 *       {@link smile.spark.SmileClassificationModel}</dt>
 *   <dd>A Spark ML {@code Estimator}/{@code Model} pair that trains and applies
 *       any SMILE {@code Classifier} from a lambda {@code Trainer}.</dd>
 *
 *   <dt>{@link smile.spark.SmileRegressor} /
 *       {@link smile.spark.SmileRegressionModel}</dt>
 *   <dd>The regression counterpart, wrapping any SMILE {@code Regression}
 *       model as a Spark ML {@code Regressor} and its fitted model.</dd>
 *
 *   <dt>{@link smile.spark.SparkHPO}</dt>
 *   <dd>Distributed cross-validation and train/test hyperparameter search for
 *       classification and regression, parallelized over Spark executors.</dd>
 * </dl>
 *
 * <h2>Quick start</h2>
 * <pre>{@code
 * // Spark DataFrame -> SMILE DataFrame
 * DataFrame smileDf = SparkDataFrames.toSmile(sparkDf);
 *
 * // Wrap any SMILE classifier as a Spark ML estimator and fit it
 * SmileClassifier classifier = new SmileClassifier()
 *     .setTrainer((x, y) -> smile.classification.KNN.fit(x, y, 5))
 *     .setFeaturesCol("features")
 *     .setLabelCol("label");
 *
 * SmileClassificationModel model = classifier.fit(trainingData);
 * Dataset<Row> predictions = model.transform(testData);
 * }</pre>
 *
 * <h2>Dependencies</h2>
 * <p>The module depends on {@code smile-core} and on the Spark libraries
 * ({@code spark-core}, {@code spark-sql}, {@code spark-mllib}, and
 * {@code hadoop-common}), which are {@code provided} and expected to be
 * supplied by the Spark runtime environment. All operations require an active
 * {@code SparkSession}.
 *
 * @author Haifeng Li
 */
package smile.spark;
