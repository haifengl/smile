/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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
 *******************************************************************************/

package smile

import java.math.BigDecimal
import java.time.{LocalDate, LocalTime, LocalDateTime}
import java.sql.Timestamp
import java.util.{Date, UUID}
import scala.language.implicitConversions
import scala.collection.immutable.ArraySeq

/**
 * @author Haifeng Li
 */
package object json {
  type JsTopLevel = Either[JsObject, JsArray]

  val JsTrue = new JsBoolean(true)
  val JsFalse = new JsBoolean(false)

  /** Enable json''' '''. */
  implicit class JsonHelper(private val sc: StringContext) extends AnyVal {
    def json(args: Any*): JsObject = {
      JsonParser(sc.s(args: _*).stripMargin).asInstanceOf[JsObject]
    }
  }

  implicit def jsObjectTopLevel(x: JsObject) = Left(x)
  implicit def jsArrayTopLevel(x: JsArray) = Right(x)
  implicit def pimpString(string: String) = new PimpedString(string)

  implicit def boolean2JsValue(x: Boolean) = JsBoolean(x)
  implicit def int2JsValue(x: Int) = JsInt(x)
  implicit def long2JsValue(x: Long) = JsLong(x)
  implicit def double2JsValue(x: Double) = JsDouble(x)
  implicit def bigdecimal2JsValue(x: BigDecimal) = JsDecimal(x)
  implicit def string2JsValue(x: String) = JsString(x)
  implicit def localDate2JsValue(x: LocalDate) = JsDate(x)
  implicit def localTime2JsValue(x: LocalTime) = JsTime(x)
  implicit def localDateTime2JsValue(x: LocalDateTime) = JsDateTime(x)
  implicit def timestamp2JsValue(x: Timestamp) = JsTimestamp(x)
  implicit def date2JsValue(x: Date) = JsTimestamp(x)
  implicit def uuid2JsValue(x: UUID) = JsUUID(x)
  implicit def objectId2JsValue(x: ObjectId) = JsObjectId(x)
  implicit def byteArray2JsValue(x: Array[Byte]) = JsBinary(x)

  implicit def array2JsValue[T <: JsValue](x: Array[T]): JsArray = seq2JsValue(ArraySeq.unsafeWrapArray(x))
  implicit def seq2JsValue[T <: JsValue](x: Seq[T]): JsArray = JsArray(x: _*)
  implicit def map2JsValue[T <: JsValue](x: Seq[(String, T)]): JsObject = JsObject(x: _*)
  implicit def map2JsValue(x: collection.mutable.Map[String, JsValue]): JsObject = JsObject(x)
  implicit def map2JsValue[T <: JsValue](x: collection.immutable.Map[String, T]): JsObject = JsObject(x)

  implicit def pimpBooleanSeq(x: Seq[Boolean]) = new PimpedBooleanSeq(x)
  implicit def pimpIntSeq(x: Seq[Int]) = new PimpedIntSeq(x)
  implicit def pimpLongSeq(x: Seq[Long]) = new PimpedLongSeq(x)
  implicit def pimpDoubleSeq(x: Seq[Double]) = new PimpedDoubleSeq(x)
  implicit def pimpBigDecimalSeq(x: Seq[BigDecimal]) = new PimpedBigDecimalSeq(x)
  implicit def pimpStringSeq(x: Seq[String]) = new PimpedStringSeq(x)
  implicit def pimpLocalDateArray(x: Seq[LocalDate]) = new PimpedLocalDateSeq(x)
  implicit def pimpLocalTimeArray(x: Seq[LocalTime]) = new PimpedLocalTimeSeq(x)
  implicit def pimpLocalDateTimeArray(x: Seq[LocalDateTime]) = new PimpedLocalDateTimeSeq(x)
  implicit def pimpDateArray(x: Seq[Date]) = new PimpedDateSeq(x)
  implicit def pimpTimestampArray(x: Seq[Timestamp]) = new PimpedTimestampSeq(x)

  implicit def pimpBooleanArray(x: Array[Boolean]) = new PimpedBooleanSeq(ArraySeq.unsafeWrapArray(x))
  implicit def pimpIntArray(x: Array[Int]) = new PimpedIntSeq(ArraySeq.unsafeWrapArray(x))
  implicit def pimpLongArray(x: Array[Long]) = new PimpedLongSeq(ArraySeq.unsafeWrapArray(x))
  implicit def pimpDoubleArray(x: Array[Double]) = new PimpedDoubleSeq(ArraySeq.unsafeWrapArray(x))
  implicit def pimpBigDecimalArray(x: Array[BigDecimal]) = new PimpedBigDecimalSeq(ArraySeq.unsafeWrapArray(x))
  implicit def pimpStringArray(x: Array[String]) = new PimpedStringSeq(ArraySeq.unsafeWrapArray(x))
  implicit def pimpLocalDateArray(x: Array[LocalDate]) = new PimpedLocalDateSeq(ArraySeq.unsafeWrapArray(x))
  implicit def pimpLocalTimeArray(x: Array[LocalTime]) = new PimpedLocalTimeSeq(ArraySeq.unsafeWrapArray(x))
  implicit def pimpLocalDateTimeArray(x: Array[LocalDateTime]) = new PimpedLocalDateTimeSeq(ArraySeq.unsafeWrapArray(x))
  implicit def pimpDateArray(x: Array[Date]) = new PimpedDateSeq(ArraySeq.unsafeWrapArray(x))
  implicit def pimpTimestampArray(x: Array[Timestamp]) = new PimpedTimestampSeq(ArraySeq.unsafeWrapArray(x))

  implicit def pimpBooleanMap(x: Map[String, Boolean]) = new PimpedBooleanMap(x)
  implicit def pimpIntMap(x: Map[String, Int]) = new PimpedIntMap(x)
  implicit def pimpLongMap(x: Map[String, Long]) = new PimpedLongMap(x)
  implicit def pimpDoubleMap(x: Map[String, Double]) = new PimpedDoubleMap(x)
  implicit def pimpBigDecimalMap(x: Map[String, BigDecimal]) = new PimpedBigDecimalMap(x)
  implicit def pimpStringMap(x: Map[String, String]) = new PimpedStringMap(x)
  implicit def pimpDateMap(x: Map[String, Date]) = new PimpedDateMap(x)

  implicit def pimpBooleanMutableMap(x: collection.mutable.Map[String, Boolean]) = new PimpedBooleanMutableMap(x)
  implicit def pimpIntMutableMap(x: collection.mutable.Map[String, Int]) = new PimpedIntMutableMap(x)
  implicit def pimpLongMutableMap(x: collection.mutable.Map[String, Long]) = new PimpedLongMutableMap(x)
  implicit def pimpDoubleMutableMap(x: collection.mutable.Map[String, Double]) = new PimpedDoubleMutableMap(x)
  implicit def pimpBigDecimalMutableMap(x: collection.mutable.Map[String, BigDecimal]) = new PimpedBigDecimalMutableMap(x)
  implicit def pimpStringMutableMap(x: collection.mutable.Map[String, String]) = new PimpedStringMutableMap(x)
  implicit def pimpDateMutableMap(x: collection.mutable.Map[String, Date]) = new PimpedDateMutableMap(x)

  implicit def json2Boolean(x: JsBoolean): Boolean = x.value
  implicit def json2Int(x: JsInt): Int = x.value
  implicit def json2Long(x: JsLong): Long = x.value
  implicit def json2Double(x: JsDouble): Double = x.value
  implicit def json2BigDecimal(x: JsDecimal): BigDecimal = x.value
  implicit def json2String(x: JsString): String = x.value
  implicit def json2Date(x: JsDate): LocalDate = x.value
  implicit def json2Time(x: JsTime): LocalTime = x.value
  implicit def json2DateTime(x: JsDateTime): LocalDateTime = x.value
  implicit def json2Timestamp(x: JsTimestamp): Timestamp = x.value
  implicit def json2Date(x: JsTimestamp): Date = x.value
  implicit def json2ObjectId(x: JsObjectId): ObjectId = x.value
  implicit def json2UUID(x: JsUUID): UUID = x.value
  implicit def json2Binary(x: JsBinary): Array[Byte] = x.value

  implicit def json2Boolean(json: JsValue): Boolean = json.asBoolean
  implicit def json2Int(json: JsValue): Int = json.asInt
  implicit def json2Long(json: JsValue): Long = json.asLong
  implicit def json2Double(json: JsValue): Double = json.asDouble
  implicit def json2BigDecimal(json: JsValue): BigDecimal = json.asDecimal
  implicit def json2LocalDate(json: JsValue): LocalDate = json.asDate
  implicit def json2LocalTime(json: JsValue): LocalTime = json.asTime
  implicit def json2LocalDateTime(json: JsValue): LocalDateTime = json.asDateTime
  implicit def json2Timestamp(json: JsValue): Timestamp = json.asTimestamp
  implicit def json2Date(json: JsValue): Date = json.asTimestamp
  implicit def json2String(json: JsValue): String = json.toString
  implicit def json2ByteArray(json: JsValue): Array[Byte] = json match {
    case JsBinary(x) => x
    case _ => throw new UnsupportedOperationException("convert JsValue to Array[Byte]")
  }
}

package json {

  private[json] class PimpedString(string: String) {
    def parseJson: JsValue = JsonParser(string)
    def parseJsObject: JsObject = parseJson.asInstanceOf[JsObject]
  }

  private[json] class PimpedBooleanSeq(seq: Seq[Boolean]) {
    def toJsArray: JsArray = JsArray(seq.map {e => JsBoolean(e)}: _*)
  }

  private[json] class PimpedIntSeq(seq: Seq[Int]) {
    def toJsArray: JsArray = JsArray(seq.map {e => JsInt(e)}: _*)
  }

  private[json] class PimpedLongSeq(seq: Seq[Long]) {
    def toJsArray: JsArray = JsArray(seq.map {e => JsLong(e)}: _*)
  }

  private[json] class PimpedDoubleSeq(seq: Seq[Double]) {
    def toJsArray: JsArray = JsArray(seq.map {e => JsDouble(e)}: _*)
  }

  private[json] class PimpedBigDecimalSeq(seq: Seq[BigDecimal]) {
    def toJsArray: JsArray = JsArray(seq.map {e => JsDecimal(e)}: _*)
  }

  private[json] class PimpedStringSeq(seq: Seq[String]) {
    def toJsArray: JsArray = JsArray(seq.map {e => JsString(e)}: _*)
  }

  private[json] class PimpedLocalDateSeq(seq: Seq[LocalDate]) {
    def toJsArray: JsArray = JsArray(seq.map {e => JsDate(e)}: _*)
  }

  private[json] class PimpedLocalTimeSeq(seq: Seq[LocalTime]) {
    def toJsArray: JsArray = JsArray(seq.map {e => JsTime(e)}: _*)
  }

  private[json] class PimpedLocalDateTimeSeq(seq: Seq[LocalDateTime]) {
    def toJsArray: JsArray = JsArray(seq.map {e => JsDateTime(e)}: _*)
  }

  private[json] class PimpedTimestampSeq(seq: Seq[Timestamp]) {
    def toJsArray: JsArray = JsArray(seq.map {e => JsTimestamp(e)}: _*)
  }

  private[json] class PimpedDateSeq(seq: Seq[Date]) {
    def toJsArray: JsArray = JsArray(seq.map {e => JsTimestamp(e)}: _*)
  }

  private[json] class PimpedBooleanMap(map: Map[String, Boolean]) {
    def toJsObject: JsObject = JsObject(map.map { case (k, v) => (k, JsBoolean(v)) })
  }

  private[json] class PimpedIntMap(map: Map[String, Int]) {
    def toJsObject: JsObject = JsObject(map.map { case (k, v) => (k, JsInt(v)) })
  }

  private[json] class PimpedLongMap(map: Map[String, Long]) {
    def toJsObject: JsObject = JsObject(map.map { case (k, v) => (k, JsLong(v)) })
  }

  private[json] class PimpedDoubleMap(map: Map[String, Double]) {
    def toJsObject: JsObject = JsObject(map.map { case (k, v) => (k, JsDouble(v)) })
  }

  private[json] class PimpedBigDecimalMap(map: Map[String, BigDecimal]) {
    def toJsObject: JsObject = JsObject(map.map { case (k, v) => (k, JsDecimal(v)) })
  }

  private[json] class PimpedStringMap(map: Map[String, String]) {
    def toJsObject: JsObject = JsObject(map.map { case (k, v) => (k, JsString(v)) })
  }

  private[json] class PimpedLocalDateMap(map: Map[String, LocalDate]) {
    def toJsObject: JsObject = JsObject(map.map { case (k, v) => (k, JsDate(v)) })
  }

  private[json] class PimpedLocalTimeMap(map: Map[String, LocalTime]) {
    def toJsObject: JsObject = JsObject(map.map { case (k, v) => (k, JsTime(v)) })
  }

  private[json] class PimpedLocalDateTimeMap(map: Map[String, LocalDateTime]) {
    def toJsObject: JsObject = JsObject(map.map { case (k, v) => (k, JsDateTime(v)) })
  }

  private[json] class PimpedTimestampMap(map: Map[String, Timestamp]) {
    def toJsObject: JsObject = JsObject(map.map { case (k, v) => (k, JsTimestamp(v)) })
  }

  private[json] class PimpedDateMap(map: Map[String, Date]) {
    def toJsObject: JsObject = JsObject(map.map { case (k, v) => (k, JsTimestamp(v)) })
  }

  private[json] class PimpedBooleanMutableMap(map: collection.mutable.Map[String, Boolean]) {
    def toJsObject: JsObject = JsObject(map.map { case (k, v) =>
      val js: JsValue = JsBoolean(v)
      (k, js)
    })
  }

  private[json] class PimpedIntMutableMap(map: collection.mutable.Map[String, Int]) {
    def toJsObject: JsObject = JsObject(map.map { case (k, v) =>
      val js: JsValue = JsInt(v)
      (k, js)
    })
  }

  private[json] class PimpedLongMutableMap(map: collection.mutable.Map[String, Long]) {
    def toJsObject: JsObject = JsObject(map.map { case (k, v) =>
      val js: JsValue = JsLong(v)
      (k, js)
    })
  }

  private[json] class PimpedDoubleMutableMap(map: collection.mutable.Map[String, Double]) {
    def toJsObject: JsObject = JsObject(map.map { case (k, v) =>
      val js: JsValue = JsDouble(v)
      (k, js)
    })
  }

  private[json] class PimpedBigDecimalMutableMap(map: collection.mutable.Map[String, BigDecimal]) {
    def toJsObject: JsObject = JsObject(map.map { case (k, v) =>
      val js: JsValue = JsDecimal(v)
      (k, js)
    })
  }

  private[json] class PimpedStringMutableMap(map: collection.mutable.Map[String, String]) {
    def toJsObject: JsObject = JsObject(map.map { case (k, v) =>
      val js: JsValue = JsString(v)
      (k, js)
    })
  }

  private[json] class PimpedDLocalateMutableMap(map: collection.mutable.Map[String, LocalDate]) {
    def toJsObject: JsObject = JsObject(map.map { case (k, v) =>
      val js: JsValue = JsDate(v)
      (k, js)
    })
  }

  private[json] class PimpedLocalTimeMutableMap(map: collection.mutable.Map[String, LocalTime]) {
    def toJsObject: JsObject = JsObject(map.map { case (k, v) =>
      val js: JsValue = JsTime(v)
      (k, js)
    })
  }

  private[json] class PimpedLocalDateTimeMutableMap(map: collection.mutable.Map[String, LocalDateTime]) {
    def toJsObject: JsObject = JsObject(map.map { case (k, v) =>
      val js: JsValue = JsDateTime(v)
      (k, js)
    })
  }

  private[json] class PimpedTimestampMutableMap(map: collection.mutable.Map[String, Timestamp]) {
    def toJsObject: JsObject = JsObject(map.map { case (k, v) =>
      val js: JsValue = JsTimestamp(v)
      (k, js)
    })
  }

  private[json] class PimpedDateMutableMap(map: collection.mutable.Map[String, Date]) {
    def toJsObject: JsObject = JsObject(map.map { case (k, v) =>
      val js: JsValue = JsTimestamp(v)
      (k, js)
    })
  }
}
