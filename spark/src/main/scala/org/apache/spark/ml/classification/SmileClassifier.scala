package org.apache.spark.ml.classification

import java.io.{ObjectInputStream, ObjectOutputStream}

import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.spark.SparkContext
import org.apache.spark.ml.classification.{
  ClassificationModel => SparkClassificationModel,
  Classifier => SparkClassifier
}
import org.apache.spark.ml.linalg.{Vector, Vectors}
import org.apache.spark.ml.param._
import org.apache.spark.ml.util.Instrumentation._
import org.apache.spark.ml.util.{Identifiable, _}
import org.apache.spark.sql.Dataset
import org.apache.spark.storage.StorageLevel
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import org.json4s.{DefaultFormats, JObject}

private[ml] trait SmileClassifierParams extends Params with ClassifierParams {

  type Trainer =
    (Array[Array[Double]], Array[Int]) => smile.classification.Classifier[Array[Double]]

  /**
   * smile classifier used for training
   *
   * @group param
   */
  val trainer: Param[Trainer] =
    new Param[Trainer](this, "trainer", "smile classifier used for training")

  /** @group getParam */
  def getTrainer: Trainer = $(trainer)

}

private[ml] object SmileClassifierParams {

  def saveImpl(
                instance: SmileClassifierParams,
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

class SmileClassifier(override val uid: String)
  extends SparkClassifier[Vector, SmileClassifier, SmileClassificationModel]
    with SmileClassifierParams
    with MLWritable {

  def setTrainer(
                  value: (Array[Array[Double]], Array[Int]) => smile.classification.Classifier[Array[Double]])
  : this.type =
    set(trainer, value.asInstanceOf[Trainer])

  def this() = this(Identifiable.randomUID("SmileClassifier"))

  override def copy(extra: ParamMap): SmileClassifier = {
    val copied = new SmileClassifier(uid)
    copyValues(copied, extra)
    copied.setTrainer(copied.getTrainer)
  }

  override protected def train(dataset: Dataset[_]): SmileClassificationModel =
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
      val y = df.select(getLabelCol).collect().map(row => row.getDouble(0).toInt)

      val sc = spark.sparkContext

      val trainersRDD = sc.parallelize(Seq(getTrainer))

      val model = trainersRDD
        .map(trainer => {
          trainer.apply(x, y)
        })
        .collect()(0)

      val numClasses = getNumClasses(df)

      if (handlePersistence) {
        df.unpersist()
      }

      new SmileClassificationModel(numClasses, model)

    }

  override def write: MLWriter = new SmileClassifier.SmileClassifierWriter(this)

}

object SmileClassifier extends MLReadable[SmileClassifier] {

  override def read: MLReader[SmileClassifier] = new SmileClassifierReader

  override def load(path: String): SmileClassifier = super.load(path)

  private[SmileClassifier] class SmileClassifierWriter(instance: SmileClassifier)
    extends MLWriter {

    override protected def saveImpl(path: String): Unit = {
      SmileClassifierParams.saveImpl(instance, path, sc)
    }

  }

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

class SmileClassificationModel(
                                override val uid: String,
                                override val numClasses: Int,
                                val model: smile.classification.Classifier[Array[Double]])
  extends SparkClassificationModel[Vector, SmileClassificationModel]
    with SmileClassifierParams
    with MLWritable {

  def this(numClasses: Int, model: smile.classification.Classifier[Array[Double]]) =
    this(Identifiable.randomUID("SmileClassificationModel"), numClasses, model)

  override def copy(extra: ParamMap): SmileClassificationModel = {
    val copied = new SmileClassificationModel(uid, numClasses, model)
    copyValues(copied, extra).setParent(parent)
  }

  override def write: MLWriter = new SmileClassificationModel.SmileClassificationModelWriter(this)

  override protected def predictRaw(features: Vector): Vector = {
    val tmp = Array.fill(numClasses)(0.0)
    tmp(model.predict(features.toArray)) = 1.0
    Vectors.dense(tmp)
  }

}

object SmileClassificationModel extends MLReadable[SmileClassificationModel] {

  override def read: MLReader[SmileClassificationModel] = new SmileClassificationModelReader

  override def load(path: String): SmileClassificationModel = super.load(path)

  private[SmileClassificationModel] class SmileClassificationModelWriter(
                                                                          instance: SmileClassificationModel)
    extends MLWriter {

    override protected def saveImpl(path: String): Unit = {
      val extraJson = "numClasses" -> instance.numClasses
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
      SmileClassifierParams.saveImpl(instance, path, sc, Some(extraJson))
    }
  }

  private class SmileClassificationModelReader extends MLReader[SmileClassificationModel] {

    /** Checked against metadata when loading model */
    private val className = classOf[SmileClassificationModel].getName

    override def load(path: String): SmileClassificationModel = {
      val metadata = SmileClassifierParams.loadImpl(path, sc, className)
      val fs = FileSystem.get(sc.hadoopConfiguration)
      implicit val format: DefaultFormats = DefaultFormats
      val numClasses = (metadata.metadata \ "numClasses").extract[Int]
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
      val tmp = new SmileClassificationModel(
        metadata.uid,
        numClasses,
        res.asInstanceOf[smile.classification.Classifier[Array[Double]]])
      metadata.getAndSetParams(tmp)
      tmp
    }
  }
}