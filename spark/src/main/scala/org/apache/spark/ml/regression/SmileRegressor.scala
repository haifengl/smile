package org.apache.spark.ml.regression

import java.io.{ObjectInputStream, ObjectOutputStream}

import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.spark.SparkContext
import org.apache.spark.ml.linalg.Vector
import org.apache.spark.ml.param._
import org.apache.spark.ml.util.Instrumentation._
import org.apache.spark.ml.util.{Identifiable, _}
import org.apache.spark.ml.{Predictor, PredictorParams}
import org.apache.spark.sql.Dataset
import org.apache.spark.storage.StorageLevel
import org.json4s.JObject
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

private[ml] trait SmileRegressorParams extends Params with PredictorParams {

  type Trainer =
    (Array[Array[Double]], Array[Double]) => smile.regression.Regression[Array[Double]]

  /**
   * smile regressor used for training
   *
   * @group param
   */
  val trainer: Param[Trainer] =
    new Param[Trainer](this, "trainer", "smile regressor used for training")

  /** @group getParam */
  def getTrainer: Trainer = $(trainer)

}

private[ml] object SmileRegressorParams {

  def saveImpl(
                instance: SmileRegressorParams,
                path: String,
                sc: SparkContext,
                extraMetadata: Option[JObject] = None): Unit = {

    val params = instance.extractParamMap().toSeq
    val jsonParams = render(
      params
        .filter { case ParamPair(p, _) => p.name != "trainer" }
        .map { case ParamPair(p, v) => p.name -> parse(p.jsonEncode(v)) }
        .toList)

    DefaultParamsWriter.saveMetadata(instance, path, sc, extraMetadata, Some(jsonParams))
  }

  def loadImpl(
                path: String,
                sc: SparkContext,
                expectedClassName: String): DefaultParamsReader.Metadata = {

    DefaultParamsReader.loadMetadata(path, sc, expectedClassName)
  }

}

class SmileRegressor(override val uid: String)
  extends Predictor[Vector, SmileRegressor, SmileRegressionModel]
    with SmileRegressorParams
    with MLWritable {

  def setTrainer(
                  value: (Array[Array[Double]], Array[Double]) => smile.regression.Regression[Array[Double]])
  : this.type =
    set(trainer, value.asInstanceOf[Trainer])

  def this() = this(Identifiable.randomUID("SmileRegressor"))

  override def copy(extra: ParamMap): SmileRegressor = {
    val copied = new SmileRegressor(uid)
    copyValues(copied, extra)
    copied.setTrainer(copied.getTrainer)
  }

  override protected def train(dataset: Dataset[_]): SmileRegressionModel =
    instrumented { instr =>
      instr.logPipelineStage(this)
      instr.logDataset(dataset)
      instr.logParams(this, labelCol, featuresCol, predictionCol)

      val spark = dataset.sparkSession

      val df = dataset.select($(labelCol), $(featuresCol))

      val handlePersistence = dataset.storageLevel == StorageLevel.NONE && (df.storageLevel == StorageLevel.NONE)
      if (handlePersistence) {
        df.persist(StorageLevel.MEMORY_AND_DISK)
      }

      val x = df.select(getFeaturesCol).collect().map(row => row.getAs[Vector](0).toArray)
      val y = df.select(getLabelCol).collect().map(row => row.getDouble(0))

      val sc = spark.sparkContext

      val trainersRDD = sc.parallelize(Seq(getTrainer))

      val model = trainersRDD
        .map(trainer => {
          trainer.apply(x, y)
        })
        .collect()(0)

      if (handlePersistence) {
        df.unpersist()
      }

      new SmileRegressionModel(model)

    }

  override def write: MLWriter = new SmileRegressor.SmileClassifierWriter(this)

}

object SmileRegressor extends MLReadable[SmileRegressor] {

  override def read: MLReader[SmileRegressor] = new SmileClassifierReader

  override def load(path: String): SmileRegressor = super.load(path)

  private[SmileRegressor] class SmileClassifierWriter(instance: SmileRegressor) extends MLWriter {

    override protected def saveImpl(path: String): Unit = {
      SmileRegressorParams.saveImpl(instance, path, sc)
    }

  }

  private class SmileClassifierReader extends MLReader[SmileRegressor] {

    /** Checked against metadata when loading model */
    private val className = classOf[SmileRegressor].getName

    override def load(path: String): SmileRegressor = {
      val metadata = SmileRegressorParams.loadImpl(path, sc, className)
      val bc = new SmileRegressor(metadata.uid)
      metadata.getAndSetParams(bc)
      bc
    }
  }

}

class SmileRegressionModel(
                            override val uid: String,
                            val model: smile.regression.Regression[Array[Double]])
  extends RegressionModel[Vector, SmileRegressionModel]
    with SmileRegressorParams
    with MLWritable {

  def this(model: smile.regression.Regression[Array[Double]]) =
    this(Identifiable.randomUID("SmileRegressionModel"), model)

  override def copy(extra: ParamMap): SmileRegressionModel = {
    val copied = new SmileRegressionModel(uid, model)
    copyValues(copied, extra).setParent(parent)
  }

  override def write: MLWriter = new SmileRegressionModel.SmileRegressionModelWriter(this)

  override def predict(features: Vector): Double = {
    model.predict(features.toArray)
  }

}

object SmileRegressionModel extends MLReadable[SmileRegressionModel] {

  override def read: MLReader[SmileRegressionModel] = new SmileRegressionModelReader

  override def load(path: String): SmileRegressionModel = super.load(path)

  private[SmileRegressionModel] class SmileRegressionModelWriter(instance: SmileRegressionModel)
    extends MLWriter {

    override protected def saveImpl(path: String): Unit = {
      val fs = FileSystem.get(sc.hadoopConfiguration)
      try {
        val fileOut = fs.create(new Path(path, "model"))
        val objectOut = new ObjectOutputStream(fileOut)
        objectOut.writeObject(instance.model)
        objectOut.close()
      } catch {
        case ex: Exception =>
          ex.printStackTrace()
      }
      SmileRegressorParams.saveImpl(instance, path, sc)
    }
  }

  private class SmileRegressionModelReader extends MLReader[SmileRegressionModel] {

    /** Checked against metadata when loading model */
    private val className = classOf[SmileRegressionModel].getName

    override def load(path: String): SmileRegressionModel = {
      val metadata = SmileRegressorParams.loadImpl(path, sc, className)
      val fs = FileSystem.get(sc.hadoopConfiguration)
      val res = try {
        val fileIn = fs.open(new Path(path, "model"))
        val objectIn = new ObjectInputStream(fileIn)
        val obj = objectIn.readObject
        objectIn.close()
        obj
      } catch {
        case ex: Exception =>
          ex.printStackTrace()
          return null
      }
      val tmp = new SmileRegressionModel(
        metadata.uid,
        res.asInstanceOf[smile.regression.Regression[Array[Double]]])
      metadata.getAndSetParams(tmp)
      tmp
    }
  }
}