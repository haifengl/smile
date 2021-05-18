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

package org.apache.spark.ml.classification

import java.io.{ObjectInputStream, ObjectOutputStream}
import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.spark.SparkContext
import org.apache.spark.ml.classification.{ClassificationModel => SparkClassificationModel, Classifier => SparkClassifier}
import org.apache.spark.ml.linalg.{Vector, Vectors}
import org.apache.spark.ml.param._
import org.apache.spark.ml.util.Instrumentation._
import org.apache.spark.ml.util.{Identifiable, _}
import org.apache.spark.sql.Dataset
import org.apache.spark.storage.StorageLevel
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import org.json4s.{DefaultFormats, JObject}

/**
  * Params for SmileClassifier
  */
private[ml] trait SmileClassifierParams extends Params with ClassifierParams {

  type Trainer = (Array[Array[Double]], Array[Int]) => smile.classification.Classifier[Array[Double]]

  /**
    * Param for the smile classification trainer
    *
    * @group param
    */
  val trainer: Param[Trainer] = new Param[Trainer](this, "trainer", "Smile classifier trainer")

  /** @group getParam */
  def getTrainer: Trainer = $(trainer)

}

private[ml] object SmileClassifierParams {
  def saveImpl(instance: SmileClassifierParams, path: String, sc: SparkContext, extraMetadata: Option[JObject] = None): Unit = {
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
  * A Spark Estimator based on Smile's classification algorithms.
  * It allows to add a Smile model into a Spark MLLib Pipeline.
  *
  * @note SmileClassifier will collect the training Dataset
  *       to the Spark Driver but train the model on a Spark Executor.
  */
class SmileClassifier(override val uid: String)
  extends SparkClassifier[Vector, SmileClassifier, SmileClassificationModel]
    with SmileClassifierParams
    with MLWritable {

  def this() = this(Identifiable.randomUID("SmileClassifier"))

  /** @group setParam */
  def setTrainer(value: (Array[Array[Double]], Array[Int]) => smile.classification.Classifier[Array[Double]]): this.type =
    set(trainer, value.asInstanceOf[Trainer])

  override protected def train(dataset: Dataset[_]): SmileClassificationModel =
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
      val y = df.select(getLabelCol).collect().map(row => row.getDouble(0).toInt)

      val trainersRDD = sc.parallelize(Seq(getTrainer))
      val model = trainersRDD.map(_.apply(x, y)).collect()(0)
      val numClasses = getNumClasses(df)

      if (persist) df.unpersist()

      new SmileClassificationModel(numClasses, model)
    }

  override def copy(extra: ParamMap): SmileClassifier = {
    val copy = new SmileClassifier(uid)
    copyValues(copy, extra)
    copy.setTrainer(getTrainer)
  }

  override def write: MLWriter = new SmileClassifier.SmileClassifierWriter(this)
}

object SmileClassifier extends MLReadable[SmileClassifier] {

  override def read: MLReader[SmileClassifier] = new SmileClassifierReader

  override def load(path: String): SmileClassifier = super.load(path)

  /** [[MLWriter]] instance for [[SmileClassifier]] */
  private[SmileClassifier] class SmileClassifierWriter(instance: SmileClassifier) extends MLWriter {
    override protected def saveImpl(path: String): Unit = {
      SmileClassifierParams.saveImpl(instance, path, sc)
    }
  }

  /** [[MLReader]] instance for [[SmileClassifier]] */
  private class SmileClassifierReader extends MLReader[SmileClassifier] {
    /** Checked against metadata when loading model */
    private val className = classOf[SmileClassifier].getName

    override def load(path: String): SmileClassifier = {
      val metadata = SmileClassifierParams.loadImpl(path, sc, className)
      val bc = new SmileClassifier(metadata.uid)
      metadata.getAndSetParams(bc)
      bc
    }
  }
}

/**
  * Model produced by [[SmileClassifier]].
  * This stores the models resulting from training the smile classifier.
  *
  * @param model smile classifier
  */
class SmileClassificationModel(override val uid: String,
                               override val numClasses: Int,
                               val model: smile.classification.Classifier[Array[Double]])
  extends SparkClassificationModel[Vector, SmileClassificationModel]
    with SmileClassifierParams
    with MLWritable {

  def this(numClasses: Int, model: smile.classification.Classifier[Array[Double]]) =
    this(Identifiable.randomUID("SmileClassificationModel"), numClasses, model)

  override def predictRaw(features: Vector): Vector = {
    // The Spark API predictRaw function outputs a Vector with
    // "confidence scores" for each class.
    val posteriori = Array.ofDim[Double](numClasses)
    if (model.soft()) {
      model.predict(features.toArray, posteriori)
    } else {
        // The "hard" confidence for the predicted class.
      posteriori(model.predict(features.toArray)) = 1.0
    }

    Vectors.dense(posteriori)
  }

  override def copy(extra: ParamMap): SmileClassificationModel = {
    val copy = new SmileClassificationModel(uid, numClasses, model)
    copyValues(copy, extra).setParent(parent)
  }

  override def write: MLWriter = new SmileClassificationModel.SmileClassificationModelWriter(this)
}

object SmileClassificationModel extends MLReadable[SmileClassificationModel] {
  override def read: MLReader[SmileClassificationModel] = new SmileClassificationModelReader

  override def load(path: String): SmileClassificationModel = super.load(path)

  /** [[MLWriter]] instance for [[SmileClassificationModel]] */
  private[SmileClassificationModel] class SmileClassificationModelWriter(instance: SmileClassificationModel) extends MLWriter {
    override protected def saveImpl(path: String): Unit = {
      val extraJson = "numClasses" -> instance.numClasses
      val fs = FileSystem.get(sc.hadoopConfiguration)
      try {
        val fileOut = fs.create(new Path(path, "model"))
        val objectOut = new ObjectOutputStream(fileOut)
        objectOut.writeObject(instance.model)
        objectOut.close()
      } catch {
        case ex: Exception => ex.printStackTrace()
      }

      SmileClassifierParams.saveImpl(instance, path, sc, Some(extraJson))
    }
  }

  /** [[MLReader]] instance for [[SmileClassificationModel]] */
  private class SmileClassificationModelReader extends MLReader[SmileClassificationModel] {
    /** Checked against metadata when loading model */
    private val className = classOf[SmileClassificationModel].getName

    override def load(path: String): SmileClassificationModel = {
      val metadata = SmileClassifierParams.loadImpl(path, sc, className)
      val fs = FileSystem.get(sc.hadoopConfiguration)
      implicit val format: DefaultFormats = DefaultFormats
      val numClasses = (metadata.metadata \ "numClasses").extract[Int]
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

      val model = new SmileClassificationModel(
        metadata.uid,
        numClasses,
        impl.asInstanceOf[smile.classification.Classifier[Array[Double]]]
      )

      metadata.getAndSetParams(model)
      model
    }
  }
}