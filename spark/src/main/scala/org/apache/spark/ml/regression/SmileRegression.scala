/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

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

/**
  * Params for SmileRegression
  */
private[ml] trait SmileRegressionParams extends Params with PredictorParams {

  type Trainer = (Array[Array[Double]], Array[Double]) => smile.regression.Regression[Array[Double]]

  /**
    * Param for the smile regression trainer
    *
    * @group param
    */
  val trainer: Param[Trainer] = new Param[Trainer](this, "trainer", "Smile regression trainer")

  /** @group getParam */
  def getTrainer: Trainer = $(trainer)
}

private[ml] object SmileRegressionParams {
  def saveImpl(instance: SmileRegressionParams, path: String, sc: SparkContext, extraMetadata: Option[JObject] = None): Unit = {
    val params = instance.extractParamMap().toSeq
    val jsonParams = render(
      params
        .filter { case ParamPair(p, _) => p.name != "trainer" }
        .map { case ParamPair(p, v) => p.name -> parse(p.jsonEncode(v)) }
        .toList
    )

    DefaultParamsWriter.saveMetadata(instance, path, sc, extraMetadata, Some(jsonParams))
  }

  def loadImpl(path: String, sc: SparkContext, expectedClassName: String): DefaultParamsReader.Metadata = {
    DefaultParamsReader.loadMetadata(path, sc, expectedClassName)
  }
}

/**
  * A Spark Estimator based on Smile's regression algorithms.
  * It allows to add a Smile model into a Spark MLLib Pipeline.
  *
  * @note SmileRegression will collect the training Dataset
  *       to the Spark Driver but train the model on a Spark Executor.
  */
class SmileRegression(override val uid: String)
  extends Predictor[Vector, SmileRegression, SmileRegressionModel]
    with SmileRegressionParams
    with MLWritable {

  def this() = this(Identifiable.randomUID("SmileRegression"))

  /** @group setParam */
  def setTrainer(value: (Array[Array[Double]], Array[Double]) => smile.regression.Regression[Array[Double]]): this.type =
    set(trainer, value)

  override protected def train(dataset: Dataset[_]): SmileRegressionModel =
    instrumented { instr =>
      instr.logPipelineStage(this)
      instr.logDataset(dataset)
      instr.logParams(this, labelCol, featuresCol, predictionCol)

      val spark = dataset.sparkSession
      val sc = spark.sparkContext

      val df = dataset.select($(labelCol), $(featuresCol))

      val persist = dataset.storageLevel == StorageLevel.NONE && (df.storageLevel == StorageLevel.NONE)
      if (persist) df.persist(StorageLevel.MEMORY_AND_DISK)

      val x = df.select(getFeaturesCol).collect().map(row => row.getAs[Vector](0).toArray)
      val y = df.select(getLabelCol).collect().map(row => row.getDouble(0))

      val trainersRDD = sc.parallelize(Seq(getTrainer))
      val model = trainersRDD.map(_.apply(x, y)).collect()(0)

      if (persist) df.unpersist()

      new SmileRegressionModel(model)
    }

  override def copy(extra: ParamMap): SmileRegression = {
    val copy = new SmileRegression(uid)
    copyValues(copy, extra)
    copy.setTrainer(getTrainer)
  }

  override def write: MLWriter = new SmileRegression.SmileRegressionWriter(this)
}

object SmileRegression extends MLReadable[SmileRegression] {

  override def read: MLReader[SmileRegression] = new SmileRegressionReader

  override def load(path: String): SmileRegression = super.load(path)

  /** [[MLWriter]] instance for [[SmileRegression]] */
  private[SmileRegression] class SmileRegressionWriter(instance: SmileRegression) extends MLWriter {
    override protected def saveImpl(path: String): Unit = {
      SmileRegressionParams.saveImpl(instance, path, sc)
    }
  }

  /** [[MLReader]] instance for [[SmileRegression]] */
  private class SmileRegressionReader extends MLReader[SmileRegression] {
    /** Checked against metadata when loading model */
    private val className = classOf[SmileRegression].getName

    override def load(path: String): SmileRegression = {
      val metadata = SmileRegressionParams.loadImpl(path, sc, className)
      val bc = new SmileRegression(metadata.uid)
      metadata.getAndSetParams(bc)
      bc
    }
  }
}

/**
  * Model produced by [[SmileRegression]].
  * This stores the model resulting from training the smile regression model.
  *
  * @param model smile regression model
  */
class SmileRegressionModel(override val uid: String, val model: smile.regression.Regression[Array[Double]])
  extends RegressionModel[Vector, SmileRegressionModel]
    with SmileRegressionParams
    with MLWritable {

  def this(model: smile.regression.Regression[Array[Double]]) =
    this(Identifiable.randomUID("SmileRegressionModel"), model)

  override def predict(features: Vector): Double = {
    model.predict(features.toArray)
  }

  override def copy(extra: ParamMap): SmileRegressionModel = {
    val copy = new SmileRegressionModel(uid, model)
    copyValues(copy, extra).setParent(parent)
  }

  override def write: MLWriter = new SmileRegressionModel.SmileRegressionModelWriter(this)
}

object SmileRegressionModel extends MLReadable[SmileRegressionModel] {
  override def read: MLReader[SmileRegressionModel] = new SmileRegressionModelReader

  override def load(path: String): SmileRegressionModel = super.load(path)

  /** [[MLWriter]] instance for [[SmileRegressionModel]] */
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
        case ex: Exception => ex.printStackTrace()
      }

      SmileRegressionParams.saveImpl(instance, path, sc)
    }
  }

  /** [[MLReader]] instance for [[SmileRegressionModel]] */
  private class SmileRegressionModelReader extends MLReader[SmileRegressionModel] {
    /** Checked against metadata when loading model */
    private val className = classOf[SmileRegressionModel].getName

    override def load(path: String): SmileRegressionModel = {
      val metadata = SmileRegressionParams.loadImpl(path, sc, className)
      val fs = FileSystem.get(sc.hadoopConfiguration)
      val impl = try {
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

      val model = new SmileRegressionModel(
        metadata.uid,
        impl.asInstanceOf[smile.regression.Regression[Array[Double]]]
      )

      metadata.getAndSetParams(model)
      model
    }
  }
}