# SMILE Spark

`smile-spark` is the integration layer between [SMILE](https://haifengl.github.io/)
machine learning and [Apache Spark](https://spark.apache.org/).  It provides
four tightly related capabilities:

1. **DataFrame conversion** — bidirectional, zero-copy-schema conversion
   between Spark SQL `DataFrame` and SMILE `DataFrame`, so that data loaded or
   transformed in Spark can be fed directly into SMILE algorithms, and SMILE
   results can be pushed back to Spark for further distributed processing.

2. **Spark ML Pipeline integration** — `SmileClassifier` and
   `SmileRegression` are fully-conformant Spark ML `Estimator` / `Model` pairs.
   Any SMILE classifier or regression model can be dropped into a Spark ML
   Pipeline with a single lambda, benefiting from Spark's feature-engineering
   stages, cross-validator, and parameter grid without leaving the SMILE
   algorithm ecosystem.

3. **Distributed hyperparameter optimization (HPO)** — `hpo.classification`
   and `hpo.regression` parallelize hyperparameter search across Spark executors.
   The training data is broadcast once; each configuration is evaluated
   independently on an executor using either k-fold cross-validation or a
   held-out test set.

4. **Type mapping** — `DataTypeOps` provides the complete bidirectional
   conversion table between Spark SQL `DataType` values and SMILE `DataType`
   values, including nullable variants, arrays, structs, Spark ML vectors, and
   user-defined types.

---

## Table of Contents

1. [Installation](#installation)
2. [SparkSession Setup](#sparksession-setup)
3. [DataFrame Conversion](#dataframe-conversion)
   - [Spark → SMILE](#spark--smile)
   - [SMILE → Spark](#smile--spark)
   - [Type Mapping Reference](#type-mapping-reference)
4. [Spark ML Pipeline Integration](#spark-ml-pipeline-integration)
   - [SmileClassifier](#smileclassifier)
   - [SmileRegression](#smileregression)
   - [Saving and Loading Models](#saving-and-loading-models)
5. [Distributed Hyperparameter Optimization](#distributed-hyperparameter-optimization)
   - [Cross-validation HPO](#cross-validation-hpo)
   - [Train/Test HPO](#traintest-hpo)
   - [Regression HPO](#regression-hpo)
   - [Working with `Hyperparameters`](#working-with-hyperparameters)
6. [Complete Examples](#complete-examples)
7. [Design Notes and Caveats](#design-notes-and-caveats)

---

## Installation

Add the module to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":spark"))
}
```

Or, from SBT in a standalone project (check the current version):

```scala
libraryDependencies += "com.github.haifengl" %% "smile-spark" % "<version>"
```

The module depends on `:scala` (the SMILE Scala shim) and the Spark libraries
(`spark-core`, `spark-sql`, `spark-mllib`) which must already be on your
classpath (typically provided by the Spark runtime).

The core import for all three feature areas is:

```scala
import smile.spark.*
```

---

## SparkSession Setup

Every feature in `smile-spark` requires an implicit or explicit `SparkSession`.
In a local test or notebook:

```scala
import org.apache.spark.sql.SparkSession

implicit val spark: SparkSession =
  SparkSession.builder()
    .master("local[*]")
    .appName("smile-spark-demo")
    .getOrCreate()
```

On a cluster, use the session provided by your environment (Databricks,
EMR, etc.) — it is usually available as `spark` in notebooks or passed through
the application entry point.

---

## DataFrame Conversion

### Spark → SMILE

`SparkDataFrame(sparkDf)` collects the entire Spark `DataFrame` to the driver
and wraps it as a local SMILE `DataFrame`.  The schema is translated
automatically.

```scala
import smile.spark.*

// Object syntax
val smileDf: smile.data.DataFrame = SparkDataFrame(sparkDf)

// Implicit extension method (same result)
val smileDf: smile.data.DataFrame = sparkDf.toSmile
```

The conversion calls `sparkDf.collect()`, so the full dataset must fit in
driver memory.  Use Spark for pre-aggregation or sampling when working with
large datasets before converting.

Nested `Row` values (struct columns) are wrapped as `SparkRowTuple`, which
implements the SMILE `Tuple` interface and delegates all field access to the
underlying Spark `Row`.

### SMILE → Spark

`SmileDataFrame(smileDf)` converts a local SMILE `DataFrame` into a
distributed Spark `DataFrame`.  Requires an implicit `SparkSession`.

```scala
import smile.spark.*
import org.apache.spark.sql.SparkSession

implicit val spark: SparkSession = …

// Object syntax
val sparkDf: org.apache.spark.sql.DataFrame = SmileDataFrame(smileDf)

// Implicit extension method
val sparkDf: org.apache.spark.sql.DataFrame = smileDf.toSpark
```

Each SMILE `Tuple` is wrapped in a `SparkRow` (a thin `org.apache.spark.sql.Row`
adapter), and `spark.createDataFrame` builds the distributed dataset from the
local list.

**Round-trip note:** the round-trip `smile → spark → smile` preserves all
data values and types, but SMILE-specific *measures* (e.g. `NominalScale`,
`OrdinalScale`) on fields are lost because Spark's schema has no equivalent
concept.  Strip them before converting if they are not needed downstream:

```scala
// Drop measures to get a plain type-only schema
val stripped = new StructType(
  smileDf.schema().fields().stream()
    .map(f => new StructField(f.name, f.dtype))
    .toList
)
```

### Type Mapping Reference

`DataTypeOps` handles the complete mapping.  The tables below show the
canonical conversions in both directions.

#### Spark → SMILE

| Spark SQL type | SMILE type (nullable=false) | SMILE type (nullable=true) |
|---|---|---|
| `BooleanType` | `BooleanType` | `NullableBooleanType` |
| `ByteType` | `ByteType` | `NullableByteType` |
| `ShortType` | `ShortType` | `NullableShortType` |
| `IntegerType` | `IntType` | `NullableIntType` |
| `LongType` | `LongType` | `NullableLongType` |
| `FloatType` | `FloatType` | `NullableFloatType` |
| `DoubleType` | `DoubleType` | `NullableDoubleType` |
| `DecimalType` | `DecimalType` | `DecimalType` |
| `StringType` | `StringType` | `StringType` |
| `BinaryType` | `ByteArrayType` | `ByteArrayType` |
| `DateType` | `DateType` | `DateType` |
| `TimestampType` | `DateTimeType` | `DateTimeType` |
| `ArrayType(e)` | `array(toSmile(e))` | `array(toSmile(e))` |
| `StructType` | nested `StructType` | nested `StructType` |
| `MapType(k,v)` | `array(StructType{key,value})` | — |
| `VectorUDT` (ML / MLLib) | `DoubleArrayType` | `DoubleArrayType` |
| `UserDefinedType` | `ObjectType(userClass)` | — |
| `NullType` | `StringType` | `StringType` |

#### SMILE → Spark

| SMILE type | Spark SQL type |
|---|---|
| `Boolean` | `BooleanType` |
| `Byte` | `ByteType` |
| `Char` | `StringType` |
| `Short` | `ShortType` |
| `Int` | `IntegerType` |
| `Long` | `LongType` |
| `Float` | `FloatType` |
| `Double` | `DoubleType` |
| `Decimal` | `DecimalType` |
| `String` | `StringType` |
| `Date` | `DateType` |
| `Time` | `StringType` (no Spark equivalent) |
| `DateTime` | `TimestampType` |
| `Object` | `StructType` (via `ExpressionEncoder.javaBean`) |
| `Array(e)` | `ArrayType(toSpark(e), false)` |
| `Struct` | nested `StructType` |

SMILE field measures are preserved as string metadata on the Spark field under
the key `"measure"` so they can be retrieved if necessary:

```scala
val field = sparkDf.schema("columnName")
val measure = field.metadata.getString("measure")  // e.g. "nominal"
```

---

## Spark ML Pipeline Integration

### SmileClassifier

`SmileClassifier` is a Spark ML `Estimator[SmileClassificationModel]` that
wraps any SMILE `Classifier[Array[Double]]`.  You supply the trainer as a
lambda:

```scala
import org.apache.spark.ml.classification.{SmileClassifier, SmileClassificationModel}
import smile.classification.RandomForest
import smile.data.formula.Formula

// Define a trainer: (features: Array[Array[Double]], labels: Array[Int]) => Classifier
val trainer = (x: Array[Array[Double]], y: Array[Int]) => {
  RandomForest.fit(Formula.lhs("label"), smile.data.DataFrame.of(x, "features"), y)
  // or any other SMILE classifier
}

val estimator = new SmileClassifier()
  .setTrainer(trainer)
  .setFeaturesCol("features")   // default: "features"
  .setLabelCol("label")         // default: "label"
  .setPredictionCol("prediction")

// Fit on a Spark Dataset whose features column contains ML Vectors
val model: SmileClassificationModel = estimator.fit(trainingData)

// Apply to new data
val predictions = model.transform(testData)
```

The `featuresCol` must contain `org.apache.spark.ml.linalg.Vector` values
(the standard Spark ML feature column type).  Use Spark's `VectorAssembler` to
assemble numeric columns into a vector before fitting.

**How training works:** `SmileClassifier.train` collects the entire feature
matrix and label array to the driver, then sends the trainer function to a
single Spark executor via a one-element RDD.  The trained model is returned to
the driver and wrapped in `SmileClassificationModel`.

**Prediction:** `SmileClassificationModel.predictRaw` returns a `Vector` of
confidence scores for each class.  For soft classifiers (those implementing
`isSoft() == true`), this is the actual posterior probability vector.  For hard
classifiers, the predicted class receives a score of `1.0` and all others `0.0`.
The Spark `ClassificationModel` machinery converts this to both a
`rawPrediction` column and a `prediction` column.

**Evaluating with Spark evaluators:**

```scala
import org.apache.spark.ml.evaluation.{BinaryClassificationEvaluator, MulticlassClassificationEvaluator}

val eval = new BinaryClassificationEvaluator()
  .setLabelCol("label")
  .setRawPredictionCol("rawPrediction")

val auc = eval.evaluate(model.transform(testData))
println(f"AUC = $auc%.4f")
```

### SmileRegression

`SmileRegression` is a Spark ML `Predictor[Vector, SmileRegression, SmileRegressionModel]`
that wraps any SMILE `Regression[Array[Double]]`.

```scala
import org.apache.spark.ml.regression.{SmileRegression, SmileRegressionModel}
import smile.regression.GradientTreeBoost
import smile.data.formula.Formula
import smile.model.cart.Loss

val trainer = (x: Array[Array[Double]], y: Array[Double]) =>
  GradientTreeBoost.fit(
    Formula.lhs("y"),
    smile.data.DataFrame.of(x, "features"), y
  )

val estimator = new SmileRegression()
  .setTrainer(trainer)
  .setFeaturesCol("features")
  .setLabelCol("label")
  .setPredictionCol("prediction")

val model: SmileRegressionModel = estimator.fit(trainingData)
val predictions = model.transform(testData)
```

`SmileRegressionModel.predict` calls the SMILE model directly:

```scala
val prediction: Double = model.predict(featureVector)
```

**Evaluating with Spark evaluators:**

```scala
import org.apache.spark.ml.evaluation.RegressionEvaluator

val eval = new RegressionEvaluator()
  .setLabelCol("label")
  .setPredictionCol("prediction")
  .setMetricName("rmse")  // "rmse", "mse", "r2", "mae", "var"

val rmse = eval.evaluate(model.transform(testData))
println(f"RMSE = $rmse%.4f")
```

### Building a Pipeline

Both `SmileClassifier` and `SmileRegression` conform to the standard Spark ML
`Estimator` interface, so they compose naturally with other stages:

```scala
import org.apache.spark.ml.Pipeline
import org.apache.spark.ml.feature.{VectorAssembler, StandardScaler}

val assembler = new VectorAssembler()
  .setInputCols(Array("f1", "f2", "f3", "f4"))
  .setOutputCol("rawFeatures")

val scaler = new StandardScaler()
  .setInputCol("rawFeatures")
  .setOutputCol("features")

val classifier = new SmileClassifier()
  .setTrainer((x, y) => smile.classification.SVM.fit(x, y, kernel, C = 1.0))

val pipeline = new Pipeline().setStages(Array(assembler, scaler, classifier))

val pipelineModel = pipeline.fit(trainDf)
val result = pipelineModel.transform(testDf)
```

### Saving and Loading Models

Both `SmileClassificationModel` and `SmileRegressionModel` implement Spark's
`MLWritable` and `MLReadable` interfaces, so they can be saved to and loaded
from any Hadoop-compatible filesystem (local, HDFS, S3, GCS, etc.).  The
underlying SMILE model is serialised using Java object serialization.

```scala
// Save
model.write.overwrite().save("/path/to/model")

// Load — use the Model companion object, not the Estimator
val loaded = SmileClassificationModel.load("/path/to/model")

// Use the loaded model exactly like the original
val predictions = loaded.transform(testData)
```

The same works for `SmileRegressionModel`:

```scala
model.write.overwrite().save("/path/to/regression-model")
val loaded = SmileRegressionModel.load("/path/to/regression-model")
```

**Serialization requirement:** the SMILE model and any captured state in the
trainer closure must be `java.io.Serializable`.  All SMILE built-in models
satisfy this requirement.

**Note on saving the Estimator:** Saving a `SmileClassifier` or
`SmileRegression` (before fitting) does *not* save the trainer lambda, because
Scala lambdas are not reliably serializable to JSON.  Only the fitted
`Model` objects support full round-trip persistence.

---

## Distributed Hyperparameter Optimization

`hpo.classification` and `hpo.regression` evaluate a collection of
hyperparameter configurations in parallel across the Spark cluster.  Each
configuration is assigned to one executor; the training data is broadcast once
to avoid re-sending it for every configuration.

The `hpo` object lives in the `smile.spark` package:

```scala
import smile.spark.*
// hpo is a top-level object in the smile.spark package
```

### Cross-validation HPO

#### Classification — raw arrays

```scala
import java.util.Properties
import smile.spark.hpo

// configurations: each Properties holds one hyperparameter assignment
val results: Array[ClassificationValidations[M]] =
  hpo.classification(
    k     = 5,               // k-fold cross-validation
    x     = featureMatrix,   // Array[Array[Double]]
    y     = labels,          // Array[Int]
    configurations = configs // Seq[Properties]
  ) { (x, y, props) =>
    // Train a model with these hyperparameters
    smile.classification.RandomForest.fit(formula, df, RandomForest.Options.of(props))
  }
```

The return type `Array[ClassificationValidations[M]]` contains one element per
configuration.  Each `ClassificationValidations[M]` aggregates k-fold metrics
(accuracy, precision, recall, F1, AUC, …).

#### Classification — Formula + DataFrame

```scala
val results: Array[ClassificationValidations[RandomForest]] =
  hpo.classification(
    k              = 5,
    formula        = Formula.lhs("class"),
    data           = mushrooms,
    configurations = configs
  ) { (formula, data, props) =>
    RandomForest.fit(formula, data, RandomForest.Options.of(props))
  }

// Print results per configuration
configs.indices.foreach { i =>
  println(s"Config $i: ${configs(i)}")
  println(s"  Avg accuracy: ${results(i).avg.accuracy}")
}
```

### Train/Test HPO

When a separate held-out test set is available, use the four-argument overload
that returns `Array[ClassificationValidation[M]]` (singular, not plural):

```scala
// Raw arrays with explicit test split
val results: Array[ClassificationValidation[M]] =
  hpo.classification(
    x              = trainX,
    y              = trainY,
    testx          = testX,
    testy          = testY,
    configurations = configs
  ) { (x, y, props) => myTrainer(x, y, props) }

// Formula + DataFrame with explicit test split
val results: Array[ClassificationValidation[M]] =
  hpo.classification(
    formula        = formula,
    train          = trainDf,
    test           = testDf,
    configurations = configs
  ) { (formula, data, props) => myTrainer(formula, data, props) }
```

### Regression HPO

The regression counterparts are identical in structure but use
`Array[Double]` for labels and return `RegressionValidations[M]` or
`RegressionValidation[M]`:

```scala
// k-fold cross-validation, raw arrays
val results: Array[RegressionValidations[M]] =
  hpo.regression(
    k              = 5,
    x              = featureMatrix,
    y              = targets,
    configurations = configs
  ) { (x, y, props) => myRegressionTrainer(x, y, props) }

// k-fold cross-validation, Formula + DataFrame
val results: Array[RegressionValidations[M]] =
  hpo.regression(
    k              = 5,
    formula        = formula,
    data           = trainingDf,
    configurations = configs
  ) { (formula, data, props) => myTrainer(formula, data, props) }

// Train/test split, raw arrays
val results: Array[RegressionValidation[M]] =
  hpo.regression(
    x              = trainX,
    y              = trainY,
    testx          = testX,
    testy          = testY,
    configurations = configs
  ) { (x, y, props) => myTrainer(x, y, props) }

// Train/test split, Formula + DataFrame
val results: Array[RegressionValidation[M]] =
  hpo.regression(
    formula        = formula,
    train          = trainDf,
    test           = testDf,
    configurations = configs
  ) { (formula, data, props) => myTrainer(formula, data, props) }
```

### Working with `Hyperparameters`

SMILE's `smile.hpo.Hyperparameters` builder generates `java.util.Properties`
objects that encode specific hyperparameter assignments.  It supports three
kinds of value specifications:

```scala
import smile.hpo.Hyperparameters
import java.util.stream.Collectors
import scala.jdk.CollectionConverters.*

val hp = new Hyperparameters()
  .add("smile.random.forest.trees",     100)               // fixed value
  .add("smile.random.forest.mtry",      Array(2, 3, 4))    // discrete choices
  .add("smile.random.forest.max.nodes", 100, 500, 50)      // range [100, 500] step 50

// Random search — draw 10 configurations uniformly at random
val configs: Seq[Properties] =
  hp.random()
    .limit(10)
    .collect(Collectors.toList())
    .asScala.toSeq

// Grid search — enumerate all combinations
val allConfigs: Seq[Properties] =
  hp.grid()
    .collect(Collectors.toList())
    .asScala.toSeq
```

Pass the resulting `Seq[Properties]` directly to `hpo.classification` or
`hpo.regression`.

---

## Complete Examples

### Example 1 — Load from Spark, train with SMILE, push back

```scala
import org.apache.spark.sql.SparkSession
import smile.spark.*
import smile.data.formula.Formula
import smile.classification.RandomForest

implicit val spark: SparkSession =
  SparkSession.builder().master("local[*]").getOrCreate()

// Load data in Spark (any Spark source)
val sparkDf = spark.read
  .option("header", "true")
  .option("inferSchema", "true")
  .csv("hdfs:///data/iris.csv")

// Convert to SMILE for local training
val smileDf = sparkDf.toSmile

// Train a SMILE model
val formula = Formula.lhs("class")
val model = RandomForest.fit(formula, smileDf)

println(s"OOB error: ${model.metrics.error}")

// Score the entire Spark DataFrame back in Spark using the pipeline
// (or distribute predictions manually)
val predictions = smileDf.toSpark   // push results back if needed
```

### Example 2 — SmileClassifier in a Spark ML Pipeline

```scala
import org.apache.spark.sql.SparkSession
import org.apache.spark.ml.Pipeline
import org.apache.spark.ml.feature.VectorAssembler
import org.apache.spark.ml.evaluation.BinaryClassificationEvaluator
import org.apache.spark.ml.classification.SmileClassifier
import smile.model.rbf.RBF
import smile.classification.RBFNetwork

implicit val spark: SparkSession =
  SparkSession.builder().master("local[*]").getOrCreate()

val data = spark.read
  .format("libsvm")
  .load("data/mushrooms.svm")
  .withColumn("label", org.apache.spark.sql.functions.col("label") - 1)
data.cache()

// Define the SMILE trainer as a lambda
val trainer = (x: Array[Array[Double]], y: Array[Int]) => {
  val neurons = RBF.fit(x, 30)   // 30 Gaussian RBF centres via k-means
  RBFNetwork.fit(x, y, neurons)
}

val classifier = new SmileClassifier()
  .setTrainer(trainer)
  .setFeaturesCol("features")
  .setLabelCol("label")

// Fit and evaluate
val model = classifier.fit(data)
val predictions = model.transform(data)

val eval = new BinaryClassificationEvaluator()
  .setLabelCol("label")
  .setRawPredictionCol("rawPrediction")

println(f"AUC = ${eval.evaluate(predictions)}%.4f")

// Save and reload the trained model
model.write.overwrite().save("/tmp/rbf-classifier")
val reloaded = org.apache.spark.ml.classification.SmileClassificationModel
  .load("/tmp/rbf-classifier")
println(f"Reloaded AUC = ${eval.evaluate(reloaded.transform(data))}%.4f")
```

### Example 3 — SmileRegression in a Spark ML Pipeline

```scala
import org.apache.spark.sql.SparkSession
import org.apache.spark.ml.regression.SmileRegression
import org.apache.spark.ml.evaluation.RegressionEvaluator
import smile.model.rbf.RBF
import smile.regression.{RBFNetwork => RBFRegression}

implicit val spark: SparkSession =
  SparkSession.builder().master("local[*]").getOrCreate()

val data = spark.read
  .format("libsvm")
  .load("data/housing.svm")
data.cache()

val trainer = (x: Array[Array[Double]], y: Array[Double]) => {
  val neurons = RBF.fit(x, 20)
  RBFRegression.fit(x, y, neurons)
}

val model = new SmileRegression()
  .setTrainer(trainer)
  .fit(data)

val eval = new RegressionEvaluator()
  .setLabelCol("label")
  .setPredictionCol("prediction")
  .setMetricName("rmse")

println(f"RMSE = ${eval.evaluate(model.transform(data))}%.4f")

model.write.overwrite().save("/tmp/rbf-regression")
val reloaded = org.apache.spark.ml.regression.SmileRegressionModel
  .load("/tmp/rbf-regression")
println(f"Reloaded RMSE = ${eval.evaluate(reloaded.transform(data))}%.4f")
```

### Example 4 — Distributed HPO with random search and cross-validation

```scala
import org.apache.spark.sql.SparkSession
import smile.spark.*
import smile.classification.RandomForest
import smile.data.formula.Formula
import smile.hpo.Hyperparameters
import smile.io.Read
import java.util.stream.Collectors
import scala.jdk.CollectionConverters.*

implicit val spark: SparkSession =
  SparkSession.builder().master("local[*]").getOrCreate()

// Load training data locally (will be broadcast to executors)
val mushrooms = Read.arff("data/mushrooms.arff").dropna()
val formula   = Formula.lhs("class")

// Define the hyperparameter search space
val hp = new Hyperparameters()
  .add("smile.random.forest.trees",     100)               // fixed
  .add("smile.random.forest.mtry",      Array(2, 3, 4))    // 3 choices
  .add("smile.random.forest.max.nodes", 100, 500, 50)      // 9 values

val configs = hp.random()
  .limit(20)
  .collect(Collectors.toList())
  .asScala.toSeq

// Run distributed 5-fold CV over 20 configurations
val scores = hpo.classification(
  k              = 5,
  formula        = formula,
  data           = mushrooms,
  configurations = configs
) { (f, data, props) =>
  RandomForest.fit(f, data, RandomForest.Options.of(props))
}

// Find the best configuration
val best = configs.zip(scores).maxBy(_._2.avg.accuracy)
println(s"Best config: ${best._1}")
println(f"Best avg accuracy: ${best._2.avg.accuracy * 100}%.2f%%")

// Print all results
configs.zip(scores).foreach { case (cfg, result) =>
  println(f"  ntrees=${cfg.getProperty("smile.random.forest.trees")} " +
          f"mtry=${cfg.getProperty("smile.random.forest.mtry")} " +
          f"maxNodes=${cfg.getProperty("smile.random.forest.max.nodes")} " +
          f"→ acc=${result.avg.accuracy * 100}%.2f%%")
}
```

### Example 5 — HPO with explicit train/test split

```scala
import org.apache.spark.sql.SparkSession
import smile.spark.*
import smile.classification.GradientTreeBoost
import smile.data.formula.Formula
import smile.hpo.Hyperparameters
import java.util.stream.Collectors
import scala.jdk.CollectionConverters.*

implicit val spark: SparkSession = …

val (trainDf, testDf) = {
  val all = Read.arff("data/bank-marketing.arff").dropna()
  val n   = all.nrow
  val idx = all.nrow * 8 / 10
  (all.of(0 until idx), all.of(idx until n))
}

val hp = new Hyperparameters()
  .add("smile.gbm.trees",     Array(100, 200, 500))
  .add("smile.gbm.shrinkage", Array(0.01, 0.05, 0.1))
  .add("smile.gbm.max.nodes", Array(4, 6, 8))

val configs = hp.grid()
  .collect(Collectors.toList())
  .asScala.toSeq   // 3 × 3 × 3 = 27 configurations

val formula = Formula.lhs("y")

val results = hpo.classification(
  formula        = formula,
  train          = trainDf,
  test           = testDf,
  configurations = configs
) { (f, data, props) =>
  GradientTreeBoost.fit(f, data, GradientTreeBoost.Options.of(props))
}

val best = configs.zip(results).maxBy(_._2.accuracy)
println(s"Best config: ${best._1}")
println(f"Best test accuracy: ${best._2.accuracy * 100}%.2f%%")
```

### Example 6 — DataFrame schema round-trip

```scala
import org.apache.spark.sql.{SparkSession, Encoders}
import smile.spark.*

implicit val spark: SparkSession = …

case class Record(id: Int, value: Double, label: String)

implicit val enc = Encoders.product[Record]
val sparkDf = spark.createDataset(Seq(
  Record(1, 3.14, "A"),
  Record(2, 2.72, "B")
)).toDF()

// Convert to SMILE
val smileDf = sparkDf.toSmile
println(smileDf.schema())
// StructType{id: int, value: double, label: string}

// Round-trip back to Spark
val back = smileDf.toSpark
back.printSchema()
// root
//  |-- id: integer (nullable = false)
//  |-- value: double (nullable = false)
//  |-- label: string (nullable = true)

back.show()
```

---

## Design Notes and Caveats

### Training happens on a single executor

Both `SmileClassifier` and `SmileRegression` collect the entire training
dataset to the driver and then send the trainer function to one executor.
This means:

- The full training data must fit in driver memory.
- Only one executor is used per `fit()` call.
- There is no distributed SMILE training — Spark is used only for data
  loading, feature engineering, and distributed prediction at score time.

For large datasets, consider downsampling before converting, or use SMILE's
own `hpo` utilities for distributed evaluation across many configurations
(which is the intended scaling story).

### The HPO broadcast pattern

`hpo.classification` and `hpo.regression` follow the
**broadcast-then-parallelize** pattern:

1. The training data (or formula + DataFrame) is **broadcast** to all
   executors using `SparkContext.broadcast`.
2. The list of `Properties` configurations is **parallelised** into an RDD.
3. Each executor picks up one configuration from the RDD and runs the entire
   k-fold CV or train/test evaluation locally using the broadcast data.
4. Results are collected back to the driver and the broadcast is destroyed.

This means the training data is transmitted once but lives in executor memory
for the duration of the search.  If memory is tight, reduce the number of
concurrent tasks or increase executor heap.

### SMILE measures are not preserved round-trip

When converting Smile → Spark → Smile, column measures (`NominalScale`,
`OrdinalScale`, `ContinuousScale`, etc.) are stored as Spark metadata under
the key `"measure"` but are not automatically reapplied on the return trip.
If your SMILE algorithms depend on measures (e.g. for split-rule selection in
CART), set them explicitly after the round-trip.

### Spark ML Vector columns

`SmileClassifier` and `SmileRegression` expect the features column to contain
`org.apache.spark.ml.linalg.Vector` values — the standard Spark ML dense or
sparse vector type.  Both dense and sparse Spark vectors are supported; sparse
vectors are converted to dense `Array[Double]` before being passed to the SMILE
trainer.

Use `VectorAssembler` to build a vector column from individual numeric columns:

```scala
import org.apache.spark.ml.feature.VectorAssembler

val assembler = new VectorAssembler()
  .setInputCols(Array("col1", "col2", "col3"))
  .setOutputCol("features")

val assembled = assembler.transform(rawDf)
```

### Java serialization for model persistence

Saved models use Java object serialization (`ObjectOutputStream`) written to
the Hadoop filesystem path you specify.  This means:

- The SMILE model class must be present on the classpath when loading.
- Model files are version-sensitive — loading a model saved with a different
  SMILE version may fail if the class structure changed.
- For long-term storage, consider wrapping the save/load cycle with version
  metadata and migration tests.
