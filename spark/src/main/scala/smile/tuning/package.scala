package smile

import java.util.function.BiFunction

import org.apache.spark.sql.SparkSession
import smile.classification.Classifier
import smile.validation.{Accuracy, ClassificationMeasure, CrossValidation}

import scala.reflect.ClassTag

case class SerializableClassificationMeasure(@transient measure: ClassificationMeasure)

package object tuning {

  def sparkgscv[T <: Object: ClassTag](
                                        spark: SparkSession)(k: Int, x: Array[T], y: Array[Int], measures: ClassificationMeasure*)(
                                        trainers: ((Array[T], Array[Int]) => Classifier[T])*): Array[Array[Double]] = {

    val sc = spark.sparkContext

    val xBroadcasted = sc.broadcast[Array[T]](x)
    val yBroadcasted = sc.broadcast[Array[Int]](y)

    val trainersRDD = sc.parallelize(trainers)

    val measuresBroadcasted = measures.map(SerializableClassificationMeasure).map(sc.broadcast)

    val res = trainersRDD
      .map(trainer => {
        //TODO: add smile-scala dependency and import the implicit conversion
        val biFunctionTrainer = new BiFunction[Array[T],Array[Int],Classifier[T]] {
          override def apply(x: Array[T], y:Array[Int]): Classifier[T] = trainer(x,y)
        }
        val x = xBroadcasted.value
        val y = yBroadcasted.value
        val measures = measuresBroadcasted.map(_.value.measure)
        //TODO: add smile-scala dependency and use smile.validation.cv
        val prediction =  CrossValidation.classification(k, x, y, biFunctionTrainer)
        val measuresOrAccuracy = if (measures.isEmpty) Seq(new Accuracy()) else measures
        measuresOrAccuracy.map { measure =>
          val result = measure.measure(y, prediction)
          result
        }.toArray
      })
      .collect()

    xBroadcasted.destroy()
    yBroadcasted.destroy()

    res

  }
}
