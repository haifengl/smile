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

  /** String interpolator for JSON.
    * `json''' '''` for JSON Object and `jarr''' '''` for JSON Array. */
  implicit class JsonHelper(private val sc: StringContext) extends AnyVal {
    /** JSON object string interpolation. */
    def json(args: Any*): JsObject = {
      JsonParser(sc.s(args: _*).stripMargin).asInstanceOf[JsObject]
    }

    /** JSON array string interpolation. */
    def jsan(args: Any*): JsArray = {
      JsonParser(sc.s(args: _*).stripMargin).asInstanceOf[JsArray]
    }
  }

  implicit def jsObjectTopLevel(x: JsObject): JsTopLevel = Left(x)
  implicit def jsArrayTopLevel(x: JsArray): JsTopLevel = Right(x)
  implicit def pimpString(string: String): PimpedString = new PimpedString(string)

  implicit def boolean2JsValue(x: Boolean): JsBoolean = JsBoolean(x)
  implicit def int2JsValue(x: Int): JsInt = JsInt(x)
  implicit def long2JsValue(x: Long): JsLong = JsLong(x)
  implicit def double2JsValue(x: Double): JsDouble = JsDouble(x)
  implicit def bigdecimal2JsValue(x: BigDecimal): JsDecimal = JsDecimal(x)
  implicit def string2JsValue(x: String): JsString = JsString(x)
  implicit def localDate2JsValue(x: LocalDate): JsDate = JsDate(x)
  implicit def localTime2JsValue(x: LocalTime): JsTime = JsTime(x)
  implicit def localDateTime2JsValue(x: LocalDateTime): JsDateTime = JsDateTime(x)
  implicit def timestamp2JsValue(x: Timestamp): JsTimestamp = JsTimestamp(x)
  implicit def date2JsValue(x: Date): JsTimestamp = JsTimestamp(x)
  implicit def uuid2JsValue(x: UUID): JsUUID = JsUUID(x)
  implicit def objectId2JsValue(x: ObjectId): JsObjectId = JsObjectId(x)
  implicit def byteArray2JsValue(x: Array[Byte]): JsBinary = JsBinary(x)

  implicit def array2JsValue[T <: JsValue](x: Array[T]): JsArray = seq2JsValue(ArraySeq.unsafeWrapArray(x))
  implicit def seq2JsValue[T <: JsValue](x: Seq[T]): JsArray = JsArray(x: _*)
  implicit def map2JsValue[T <: JsValue](x: Seq[(String, T)]): JsObject = JsObject(x: _*)
  implicit def map2JsValue(x: collection.mutable.Map[String, JsValue]): JsObject = JsObject(x)
  implicit def map2JsValue[T <: JsValue](x: collection.immutable.Map[String, T]): JsObject = JsObject(x)

  implicit def pimpBooleanSeq(x: Seq[Boolean]): PimpedBooleanSeq = new PimpedBooleanSeq(x)
  implicit def pimpIntSeq(x: Seq[Int]): PimpedIntSeq = new PimpedIntSeq(x)
  implicit def pimpLongSeq(x: Seq[Long]): PimpedLongSeq = new PimpedLongSeq(x)
  implicit def pimpDoubleSeq(x: Seq[Double]): PimpedDoubleSeq = new PimpedDoubleSeq(x)
  implicit def pimpBigDecimalSeq(x: Seq[BigDecimal]): PimpedBigDecimalSeq = new PimpedBigDecimalSeq(x)
  implicit def pimpStringSeq(x: Seq[String]): PimpedStringSeq = new PimpedStringSeq(x)
  implicit def pimpLocalDateArray(x: Seq[LocalDate]): PimpedLocalDateSeq = new PimpedLocalDateSeq(x)
  implicit def pimpLocalTimeArray(x: Seq[LocalTime]): PimpedLocalTimeSeq = new PimpedLocalTimeSeq(x)
  implicit def pimpLocalDateTimeArray(x: Seq[LocalDateTime]): PimpedLocalDateTimeSeq = new PimpedLocalDateTimeSeq(x)
  implicit def pimpDateArray(x: Seq[Date]): PimpedDateSeq = new PimpedDateSeq(x)
  implicit def pimpTimestampArray(x: Seq[Timestamp]): PimpedTimestampSeq = new PimpedTimestampSeq(x)

  implicit def pimpBooleanArray(x: Array[Boolean]): PimpedBooleanSeq = new PimpedBooleanSeq(ArraySeq.unsafeWrapArray(x))
  implicit def pimpIntArray(x: Array[Int]): PimpedIntSeq = new PimpedIntSeq(ArraySeq.unsafeWrapArray(x))
  implicit def pimpLongArray(x: Array[Long]): PimpedLongSeq = new PimpedLongSeq(ArraySeq.unsafeWrapArray(x))
  implicit def pimpDoubleArray(x: Array[Double]): PimpedDoubleSeq = new PimpedDoubleSeq(ArraySeq.unsafeWrapArray(x))
  implicit def pimpBigDecimalArray(x: Array[BigDecimal]): PimpedBigDecimalSeq = new PimpedBigDecimalSeq(ArraySeq.unsafeWrapArray(x))
  implicit def pimpStringArray(x: Array[String]): PimpedStringSeq = new PimpedStringSeq(ArraySeq.unsafeWrapArray(x))
  implicit def pimpLocalDateArray(x: Array[LocalDate]): PimpedLocalDateSeq = new PimpedLocalDateSeq(ArraySeq.unsafeWrapArray(x))
  implicit def pimpLocalTimeArray(x: Array[LocalTime]): PimpedLocalTimeSeq = new PimpedLocalTimeSeq(ArraySeq.unsafeWrapArray(x))
  implicit def pimpLocalDateTimeArray(x: Array[LocalDateTime]): PimpedLocalDateTimeSeq = new PimpedLocalDateTimeSeq(ArraySeq.unsafeWrapArray(x))
  implicit def pimpDateArray(x: Array[Date]): PimpedDateSeq = new PimpedDateSeq(ArraySeq.unsafeWrapArray(x))
  implicit def pimpTimestampArray(x: Array[Timestamp]): PimpedTimestampSeq = new PimpedTimestampSeq(ArraySeq.unsafeWrapArray(x))

  implicit def pimpBooleanMap(x: Map[String, Boolean]): PimpedBooleanMap = new PimpedBooleanMap(x)
  implicit def pimpIntMap(x: Map[String, Int]): PimpedIntMap = new PimpedIntMap(x)
  implicit def pimpLongMap(x: Map[String, Long]): PimpedLongMap = new PimpedLongMap(x)
  implicit def pimpDoubleMap(x: Map[String, Double]): PimpedDoubleMap = new PimpedDoubleMap(x)
  implicit def pimpBigDecimalMap(x: Map[String, BigDecimal]): PimpedBigDecimalMap = new PimpedBigDecimalMap(x)
  implicit def pimpStringMap(x: Map[String, String]): PimpedStringMap = new PimpedStringMap(x)
  implicit def pimpDateMap(x: Map[String, Date]): PimpedDateMap = new PimpedDateMap(x)

  implicit def pimpBooleanMutableMap(x: collection.mutable.Map[String, Boolean]): PimpedBooleanMutableMap = new PimpedBooleanMutableMap(x)
  implicit def pimpIntMutableMap(x: collection.mutable.Map[String, Int]): PimpedIntMutableMap = new PimpedIntMutableMap(x)
  implicit def pimpLongMutableMap(x: collection.mutable.Map[String, Long]): PimpedLongMutableMap = new PimpedLongMutableMap(x)
  implicit def pimpDoubleMutableMap(x: collection.mutable.Map[String, Double]): PimpedDoubleMutableMap = new PimpedDoubleMutableMap(x)
  implicit def pimpBigDecimalMutableMap(x: collection.mutable.Map[String, BigDecimal]): PimpedBigDecimalMutableMap = new PimpedBigDecimalMutableMap(x)
  implicit def pimpStringMutableMap(x: collection.mutable.Map[String, String]): PimpedStringMutableMap = new PimpedStringMutableMap(x)
  implicit def pimpDateMutableMap(x: collection.mutable.Map[String, Date]): PimpedDateMutableMap = new PimpedDateMutableMap(x)

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
