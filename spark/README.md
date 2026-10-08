# SMILE Spark

`smile-spark` is the integration layer between [SMILE](https://www.aihalo.dev//)
machine learning and [Apache Spark](https://spark.apache.org/) (Spark 4.x, Java 25).
It provides four tightly related capabilities:

1. **DataFrame conversion** — bidirectional conversion between Spark SQL
   `Dataset<Row>` and SMILE `DataFrame`, so that data loaded or transformed in Spark
   can be fed directly into SMILE algorithms, and SMILE results can be pushed back
   to Spark for distributed processing.

2. **Spark ML Pipeline integration** — `SmileClassifier` and
   `SmileRegressor` are fully-conformant Spark ML `Estimator` / `Model` pairs in
   the `smile.spark` package. Any SMILE classifier or regression model can be dropped
   into a Spark ML Pipeline with a single lambda, benefiting from Spark's feature
   engineering stages, cross-validator, and parameter grid.

3. **Distributed hyperparameter optimization (HPO)** — `SparkHPO.classification`
   and `SparkHPO.regression` parallelize hyperparameter search across Spark executors.
   The training data is broadcast once; each configuration is evaluated independently
   on an executor using either k-fold cross-validation or a held-out test set.

4. **Type mapping** — `DataTypeUtils` provides the complete bidirectional
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
   - [SmileRegressor](#smileregressor)
   - [Building a Pipeline](#building-a-pipeline)
   - [Saving and Loading Models](#saving-and-loading-models)
5. [Distributed Hyperparameter Optimization](#distributed-hyperparameter-optimization)
   - [Cross-validation HPO](#cross-validation-hpo)
   - [Train/Test HPO](#traintest-hpo)
   - [Working with Hyperparameters](#working-with-hyperparameters)

---

## Installation

Add the module to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":spark"))
}
```

Or, from SBT in a standalone project:

```scala
libraryDependencies += "com.github.haifengl" % "smile-spark" % "<version>"
```

The module depends on `smile-core` (pure Java) and the Spark libraries
(`spark-core`, `spark-sql`, `spark-mllib`, `hadoop-common`) which are marked `provided`
(typically supplied by your Spark runtime environment).

The core import is:

```java
import smile.spark.*;
```

---

## SparkSession Setup

Every feature in `smile-spark` works with an active `SparkSession`:

```java
import org.apache.spark.sql.SparkSession;

SparkSession spark = SparkSession.builder()
    .master("local[*]")
    .appName("smile-spark-demo")
    .getOrCreate();
```

---

## DataFrame Conversion

### Spark → SMILE

`SparkDataFrames.toSmile(sparkDf)` collects the Spark DataFrame to the driver
and wraps it as a local SMILE `DataFrame`. The schema is translated automatically.

```java
import smile.data.DataFrame;
import smile.spark.SparkDataFrames;

DataFrame smileDf = SparkDataFrames.toSmile(sparkDf);
```

For large datasets, you can stream rows as SMILE `Tuple`s without materializing
the entire dataset in a single list:

```java
import smile.data.Tuple;
import smile.spark.SparkDataFrames;
import java.util.stream.Stream;

Stream<Tuple> stream = SparkDataFrames.stream(sparkDf);
```

Nested `Row` values (struct columns) are wrapped as `SparkRowTuple`, which
implements SMILE's `Tuple` interface and delegates field access to the underlying
Spark `Row`.

### SMILE → Spark

`SmileDataFrames.toSpark(spark, smileDf)` converts a local SMILE `DataFrame` into a
distributed Spark `Dataset<Row>`.

```java
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import smile.spark.SmileDataFrames;

Dataset<Row> sparkDf = SmileDataFrames.toSpark(spark, smileDf);
```

### Type Mapping Reference

`DataTypeUtils` handles the complete mapping between Spark SQL and SMILE types:

| Spark SQL Type | SMILE Type (nullable=false) | SMILE Type (nullable=true) |
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
| `TimestampType` / `TimestampNTZType` | `DateTimeType` | `DateTimeType` |
| `ArrayType(e)` | `array(toSmile(e))` | `array(toSmile(e))` |
| `StructType` | nested `StructType` | nested `StructType` |
| `MapType(k,v)` | `array(StructType{key,value})` | — |
| `VectorUDT` (ML / MLLib) | `DoubleArrayType` | `DoubleArrayType` |
| `UserDefinedType` | `ObjectType(userClass)` | — |

---

## Spark ML Pipeline Integration

### SmileClassifier

`SmileClassifier` is a Spark ML `Classifier<Vector, SmileClassifier, SmileClassificationModel>`
that wraps any SMILE `Classifier<double[]>`. You supply the trainer as a lambda:

```java
import org.apache.spark.ml.classification.SmileClassifier;
import org.apache.spark.ml.classification.SmileClassificationModel;
import smile.classification.RandomForest;
import smile.data.formula.Formula;

SmileClassifier classifier = new SmileClassifier()
    .setTrainer((x, y) -> RandomForest.fit(Formula.lhs("label"), smile.data.DataFrame.of(x, "features"), y))
    .setFeaturesCol("features")
    .setLabelCol("label")
    .setPredictionCol("prediction");

SmileClassificationModel model = classifier.fit(trainingData);
Dataset<Row> predictions = model.transform(testData);
```

`SmileClassificationModel.predictRaw` outputs class posterior probabilities (or confidence scores),
enabling evaluation with standard Spark evaluators:

```java
import org.apache.spark.ml.evaluation.BinaryClassificationEvaluator;

BinaryClassificationEvaluator eval = new BinaryClassificationEvaluator()
    .setLabelCol("label")
    .setRawPredictionCol("rawPrediction");

double auc = eval.evaluate(predictions);
```

### SmileRegressor

`SmileRegressor` is a Spark ML `Regressor<Vector, SmileRegressor, SmileRegressionModel>`
that wraps any SMILE `Regression<double[]>`.

```java
import smile.spark.SmileRegressor;
import smile.spark.SmileRegressionModel;
import smile.regression.GradientTreeBoost;
import smile.data.formula.Formula;

SmileRegressor regressor = new SmileRegressor()
    .setTrainer((x, y) -> GradientTreeBoost.fit(Formula.lhs("y"), smile.data.DataFrame.of(x, "features"), y))
    .setFeaturesCol("features")
    .setLabelCol("label")
    .setPredictionCol("prediction");

SmileRegressionModel model = regressor.fit(trainingData);
Dataset<Row> predictions = model.transform(testData);
```

Evaluate with Spark's `RegressionEvaluator`:

```java
import org.apache.spark.ml.evaluation.RegressionEvaluator;

RegressionEvaluator eval = new RegressionEvaluator()
    .setLabelCol("label")
    .setPredictionCol("prediction")
    .setMetricName("rmse");

double rmse = eval.evaluate(predictions);
```

### Building a Pipeline

Both `SmileClassifier` and `SmileRegressor` conform to standard Spark ML contracts and compose
into pipelines with other transformers:

```java
import org.apache.spark.ml.Pipeline;
import org.apache.spark.ml.PipelineModel;
import org.apache.spark.ml.feature.StandardScaler;
import org.apache.spark.ml.feature.VectorAssembler;
import smile.spark.SmileClassifier;

VectorAssembler assembler = new VectorAssembler()
    .setInputCols(new String[]{"f1", "f2", "f3"})
    .setOutputCol("rawFeatures");

StandardScaler scaler = new StandardScaler()
    .setInputCol("rawFeatures")
    .setOutputCol("features");

SmileClassifier classifier = new SmileClassifier()
    .setTrainer((x, y) -> smile.classification.KNN.fit(x, y, 5));

Pipeline pipeline = new Pipeline()
    .setStages(new PipelineStage[]{assembler, scaler, classifier});

PipelineModel pipelineModel = pipeline.fit(trainDf);
Dataset<Row> results = pipelineModel.transform(testDf);
```

### Saving and Loading Models

Models implement Spark's `MLWritable` and support standard `MLReader` loading:

```java
// Save
model.write().overwrite().save("/path/to/model");

// Load
SmileClassificationModel loaded = SmileClassificationModel.load("/path/to/model");
Dataset<Row> predictions = loaded.transform(testData);
```

---

## Distributed Hyperparameter Optimization

`SparkHPO` parallelizes hyperparameter evaluation across the Spark cluster:

```java
import smile.classification.RandomForest;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.hpo.Hyperparameters;
import smile.spark.SparkHPO;
import smile.validation.ClassificationValidations;

Hyperparameters hp = new Hyperparameters()
    .add("smile.random.forest.trees", 50, 100, 50)
    .add("smile.random.forest.mtry", new int[]{2, 3, 4});

List<Properties> configurations = hp.random().limit(10).toList();

List<ClassificationValidations<RandomForest>> results = SparkHPO.classification(
    spark,
    5, // 5-fold cross validation
    formula,
    trainingData,
    configurations,
    (f, data, props) -> RandomForest.fit(f, data, RandomForest.Options.of(props))
);

for (var val : results) {
    System.out.println("Accuracy: " + val.avg().accuracy());
}
```
