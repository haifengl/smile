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

package smile.json

import scala.language.implicitConversions
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.sql.Timestamp
import java.time.{LocalDate, LocalTime}
import com.typesafe.scalalogging.LazyLogging

/** JSON Serializer in BSON format as defined by http://bsonspec.org/spec.html.
  * This is not fully compatible with BSON spec, where the root must be a document/JsObject.
  * In contrast, the root can be any JsValue in our implementation. Correspondingly, the
  * root will always has the type byte as the first byte.
  *
  * Not Multi-threading safe. Each thread should have its own BsonSerializer instance.
  * Data size limit to 10MB by default.
  *
  * Although JsTime/JsDateTime can be represented to nanosecond precision, we don't
  * store the nano-of-second field to save the space. To preserve the high
  * precision of time, JsTimestamp should be employed and of course consumes more space.
  *
  * @author Haifeng Li
  */
class JsonSerializer(buffer: ByteBuffer = ByteBuffer.allocate(10 * 1024 * 1024)) extends LazyLogging {

  import JsonSerializer._

  def serialize(json: JsValue): Array[Byte] = {
    buffer.clear
    serialize(buffer, json, None)
    buffer
  }

  def deserialize(bytes: Array[Byte]): JsValue = {
    val buffer = ByteBuffer.wrap(bytes)
    deserialize(buffer)
  }

  def deserialize(buffer: ByteBuffer): JsValue = {
    buffer.get match { // data type
      case TYPE_BOOLEAN   => boolean(buffer)
      case TYPE_INT32     => int(buffer)
      case TYPE_INT64     => long(buffer)
      case TYPE_DOUBLE    => double(buffer)
      case TYPE_BIGDECIMAL=> decimal(buffer)
      case TYPE_DATE      => date(buffer)
      case TYPE_TIME      => time(buffer)
      case TYPE_DATETIME  => datetime(buffer)
      case TYPE_TIMESTAMP => timestamp(buffer)
      case TYPE_STRING    => string(buffer)
      case TYPE_BINARY    => binary(buffer)
      case TYPE_OBJECTID  => objectId(buffer)
      case TYPE_NULL      => JsNull
      case TYPE_UNDEFINED => JsUndefined
      case TYPE_DOCUMENT  =>
        val doc = JsObject()
        deserialize(buffer, doc)

      case TYPE_ARRAY     =>
        val doc = JsObject()
        deserialize(buffer, doc)
        val elements = doc.fields.map{case (k, v) => (k.toInt, v)}.toSeq.sortBy(_._1).map(_._2)
        JsArray(elements: _*)

      case x => throw new IllegalStateException("Unsupported BSON type: %02X" format x)
    }
  }

  /** Clears the object buffer. */
  def clear: Unit = buffer.clear

  private def serialize(buffer: ByteBuffer, string: Option[String]): Unit = {
    if (string.isDefined) {
      serialize(buffer, string.get)
    }
  }

  private def serialize(buffer: ByteBuffer, string: String): Unit = {
    buffer.put(string.getBytes(UTF8))
    buffer.put(END_OF_STRING)
  }

  private def serialize(buffer: ByteBuffer, json: JsBoolean, ename: Option[String]): Unit = {
    buffer.put(TYPE_BOOLEAN)
    serialize(buffer, ename)
    buffer.put(if (json.value) TRUE else FALSE)
  }

  private def serialize(buffer: ByteBuffer, json: JsInt, ename: Option[String]): Unit = {
    buffer.put(TYPE_INT32)
    serialize(buffer, ename)
    buffer.putInt(json.value)
  }

  private def serialize(buffer: ByteBuffer, json: JsLong, ename: Option[String]): Unit = {
    buffer.put(TYPE_INT64)
    serialize(buffer, ename)
    buffer.putLong(json.value)
  }

  private def serialize(buffer: ByteBuffer, json: JsDouble, ename: Option[String]): Unit = {
    buffer.put(TYPE_DOUBLE)
    serialize(buffer, ename)
    buffer.putDouble(json.value)
  }

  private def serialize(buffer: ByteBuffer, json: JsDecimal, ename: Option[String]): Unit = {
    buffer.put(TYPE_BIGDECIMAL)
    serialize(buffer, ename)
    val bytes = json.value.toPlainString.getBytes(UTF8)
    buffer.putInt(bytes.length)
    buffer.put(bytes)
  }

  private def serialize(buffer: ByteBuffer, json: JsString, ename: Option[String]): Unit = {
    buffer.put(TYPE_STRING)
    serialize(buffer, ename)
    val bytes = json.value.getBytes(UTF8)
    buffer.putInt(bytes.length)
    buffer.put(bytes)
  }

  private def serialize(buffer: ByteBuffer, json: JsDate, ename: Option[String]): Unit = {
    buffer.put(TYPE_DATE)
    serialize(buffer, ename)
    val value = json.value
    val date = value.getYear * 10000 + value.getMonthValue * 100 + value.getDayOfMonth
    buffer.putInt(date)
  }

  private def serialize(buffer: ByteBuffer, json: JsTime, ename: Option[String]): Unit = {
    buffer.put(TYPE_TIME)
    serialize(buffer, ename)
    val value = json.value
    val time = value.getHour * 10000 + value.getMinute * 100 + value.getSecond
    buffer.putInt(time)
  }

  private def serialize(buffer: ByteBuffer, json: JsDateTime, ename: Option[String]): Unit = {
    buffer.put(TYPE_DATETIME)
    serialize(buffer, ename)
    val value = json.value
    val date = value.getYear * 10000 + value.getMonthValue * 100 + value.getDayOfMonth
    val time = value.getHour * 10000 + value.getMinute * 100 + value.getSecond
    buffer.putInt(date)
    buffer.putInt(time)
  }

  private def serialize(buffer: ByteBuffer, json: JsTimestamp, ename: Option[String]): Unit = {
    buffer.put(TYPE_TIMESTAMP)
    serialize(buffer, ename)
    buffer.putLong(json.value.getTime)
    buffer.putInt(json.value.getNanos)
  }

  private def serialize(buffer: ByteBuffer, json: JsObjectId, ename: Option[String]): Unit = {
    buffer.put(TYPE_OBJECTID)
    serialize(buffer, ename)
    buffer.put(json.value.id)
  }

  private def serialize(buffer: ByteBuffer, json: JsUUID, ename: Option[String]): Unit = {
    buffer.put(TYPE_BINARY)
    serialize(buffer, ename)
    buffer.putInt(16)
    buffer.put(BINARY_SUBTYPE_UUID)
    buffer.putLong(json.value.getMostSignificantBits)
    buffer.putLong(json.value.getLeastSignificantBits)
  }

  private def serialize(buffer: ByteBuffer, json: JsBinary, ename: Option[String]): Unit = {
    buffer.put(TYPE_BINARY)
    serialize(buffer, ename)
    buffer.putInt(json.value.size)
    buffer.put(BINARY_SUBTYPE_GENERIC)
    buffer.put(json.value)
  }

  private def cstring(buffer: ByteBuffer): String = {
    val str = new collection.mutable.ArrayBuffer[Byte](64)
    var b = buffer.get
    while (b != END_OF_STRING) {str += b; b = buffer.get}
    new String(str.toArray)
  }

  private def ename(buffer: ByteBuffer): String = cstring(buffer)

  private def boolean(buffer: ByteBuffer): JsBoolean = {
    val b = buffer.get
    if (b == 0) JsFalse else JsTrue
  }

  private def int(buffer: ByteBuffer): JsInt = {
    val x = buffer.getInt
    if (x == 0) JsInt.zero else JsInt(x)
  }

  private def long(buffer: ByteBuffer): JsLong = {
    val x = buffer.getLong
    if (x == 0) JsLong.zero else JsLong(x)
  }

  private def double(buffer: ByteBuffer): JsDouble = {
    val x = buffer.getDouble
    if (x == 0.0) JsDouble.zero else JsDouble(x)
  }

  private def decimal(buffer: ByteBuffer): JsDecimal = {
    val length = buffer.getInt
    val dst = new Array[Byte](length)
    buffer.get(dst)
    JsDecimal(new String(dst, UTF8))
  }

  private def date(buffer: ByteBuffer): JsDate = {
    val value = buffer.getInt
    val year = value / 10000
    val month = (value % 10000) / 100
    val day = value % 100
    val date = LocalDate.of(year, month, day)
    JsDate(date)
  }

  private def time(buffer: ByteBuffer): JsTime = {
    val value = buffer.getInt
    val hour = value / 10000
    val minute = (value % 10000) / 100
    val second = value % 100
    val time = LocalTime.of(hour, minute, second)
    JsTime(time)
  }

  private def datetime(buffer: ByteBuffer): JsDateTime = {
    val value = buffer.getInt
    val year = value / 10000
    val month = (value % 10000) / 100
    val day = value % 100
    val date = LocalDate.of(year, month, day)

    val value2 = buffer.getInt
    val hour = value2 / 10000
    val minute = (value2 % 10000) / 100
    val second = value2 % 100
    val time = LocalTime.of(hour, minute, second)
    JsDateTime(date, time)
  }

  private def timestamp(buffer: ByteBuffer): JsTimestamp = {
    val milliseconds = buffer.getLong
    val nanos = buffer.getInt
    val timestamp = new Timestamp(milliseconds)
    timestamp.setNanos(nanos)
    JsTimestamp(timestamp)
  }

  private def objectId(buffer: ByteBuffer): JsValue = {
    val id = new Array[Byte](ObjectId.size)
    buffer.get(id)
    JsObjectId(ObjectId(id))
  }

  private def string(buffer: ByteBuffer): JsString = {
    val length = buffer.getInt
    val dst = new Array[Byte](length)
    buffer.get(dst)
    JsString(new String(dst, UTF8))
  }

  private def binary(buffer: ByteBuffer): JsValue = {
    val length = buffer.getInt
    val subtype = buffer.get
    if (subtype == BINARY_SUBTYPE_UUID) {
      JsUUID(buffer.getLong, buffer.getLong)
    } else {
      val dst = new Array[Byte](length)
      buffer.get(dst)
      JsBinary(dst)
    }
  }

  private def serialize(buffer: ByteBuffer, json: JsObject, ename: Option[String]): Unit = {
    buffer.put(TYPE_DOCUMENT)
    serialize(buffer, ename)

    val start = buffer.position
    buffer.putInt(0) // placeholder for document size

    json.fields.toSeq.sortBy(_._1).foreach { case (field, value) =>
      serialize(buffer, value, Some(field))
    }

    buffer.put(END_OF_DOCUMENT)
    buffer.putInt(start, buffer.position - start) // update document size
  }

  private def serialize(buffer: ByteBuffer, json: JsArray, ename: Option[String]): Unit = {
    buffer.put(TYPE_ARRAY)
    serialize(buffer, ename)

    val start = buffer.position
    buffer.putInt(0) // placeholder for document size

    json.elements.zipWithIndex.foreach { case (value, index) =>
      serialize(buffer, value, Some(index.toString))
    }

    buffer.put(END_OF_DOCUMENT)
    buffer.putInt(start, buffer.position - start) // update document size
  }

  private def serialize(buffer: ByteBuffer, json: JsValue, ename: Option[String]): Unit = {
    json match {
      case x: JsBoolean  => serialize(buffer, x, ename)
      case x: JsInt      => serialize(buffer, x, ename)
      case x: JsLong     => serialize(buffer, x, ename)
      case x: JsDouble   => serialize(buffer, x, ename)
      case x: JsDecimal  => serialize(buffer, x, ename)
      case x: JsString   => serialize(buffer, x, ename)
      case x: JsDate     => serialize(buffer, x, ename)
      case x: JsTime     => serialize(buffer, x, ename)
      case x: JsDateTime => serialize(buffer, x, ename)
      case x: JsTimestamp=> serialize(buffer, x, ename)
      case x: JsUUID     => serialize(buffer, x, ename)
      case x: JsObjectId => serialize(buffer, x, ename)
      case x: JsBinary   => serialize(buffer, x, ename)
      case x: JsObject   => serialize(buffer, x, ename)
      case x: JsArray    => serialize(buffer, x, ename)
      case JsNull        => buffer.put(TYPE_NULL); serialize(buffer, ename)
      case JsUndefined   => buffer.put(TYPE_UNDEFINED); serialize(buffer, ename)
      case JsCounter(_)  => throw new IllegalArgumentException("BSON doesn't support JsCounter")
    }
  }

  private def deserialize(buffer: ByteBuffer, json: JsObject): JsObject = {
    val start = buffer.position
    val size = buffer.getInt // document size

    val loop = new scala.util.control.Breaks
    loop.breakable {
      while (true) {
        buffer.get match {
          case END_OF_DOCUMENT => loop.break
          case TYPE_BOOLEAN    => json(ename(buffer)) = boolean(buffer)
          case TYPE_INT32      => json(ename(buffer)) = int(buffer)
          case TYPE_INT64      => json(ename(buffer)) = long(buffer)
          case TYPE_DOUBLE     => json(ename(buffer)) = double(buffer)
          case TYPE_BIGDECIMAL => json(ename(buffer)) = decimal(buffer)
          case TYPE_DATE       => json(ename(buffer)) = date(buffer)
          case TYPE_TIME       => json(ename(buffer)) = time(buffer)
          case TYPE_DATETIME   => json(ename(buffer)) = datetime(buffer)
          case TYPE_TIMESTAMP  => json(ename(buffer)) = timestamp(buffer)
          case TYPE_STRING     => json(ename(buffer)) = string(buffer)
          case TYPE_OBJECTID   => json(ename(buffer)) = objectId(buffer)
          case TYPE_BINARY     => json(ename(buffer)) = binary(buffer)
          case TYPE_NULL       => json(ename(buffer)) = JsNull
          case TYPE_UNDEFINED  => json(ename(buffer)) = JsUndefined
          case TYPE_DOCUMENT   =>
            val doc = JsObject()
            json(ename(buffer)) = deserialize(buffer, doc)

          case TYPE_ARRAY      =>
            val doc = JsObject()
            val field = ename(buffer)
            deserialize(buffer, doc)
            json(field) = JsArray(doc.fields.map { case (k, v) => (k.toInt, v) }.toSeq.sortBy(_._1).map(_._2): _*)

          case x               => throw new IllegalStateException("Unsupported BSON type: %02X" format x)
        }
      }
    }

    if (buffer.position - start != size)
      logger.warn(s"BSON size $size but deserialize finishes at ${buffer.position}, starts at $start")

    json
  }
}

object JsonSerializer {
  val UTF8 = Charset.forName("UTF-8")

  /** End of document */
  val END_OF_DOCUMENT             : Byte = 0x00

  /** End of string */
  val END_OF_STRING               : Byte = 0x00

  /** Type markers, based on BSON (http://bsonspec.org/spec.html). */
  val TYPE_DOUBLE                 : Byte = 0x01
  val TYPE_STRING                 : Byte = 0x02
  val TYPE_DOCUMENT               : Byte = 0x03
  val TYPE_ARRAY                  : Byte = 0x04
  val TYPE_BINARY                 : Byte = 0x05
  val TYPE_UNDEFINED              : Byte = 0x06
  val TYPE_OBJECTID               : Byte = 0x07
  val TYPE_BOOLEAN                : Byte = 0x08
  val TYPE_TIMESTAMP              : Byte = 0x09 // Called UTC datetime in BSON, UTC milliseconds since the Unix epoch.
  val TYPE_NULL                   : Byte = 0x0A
  val TYPE_REGEX                  : Byte = 0x0B
  val TYPE_DBPOINTER              : Byte = 0x0C
  val TYPE_JAVASCRIPT             : Byte = 0x0D
  val TYPE_SYMBOL                 : Byte = 0x0E
  val TYPE_JAVASCRIPT_WITH_SCOPE  : Byte = 0x0F
  val TYPE_INT32                  : Byte = 0x10
  val TYPE_MONGODB_TIMESTAMP      : Byte = 0x11 // Special internal type used by MongoDB.
  val TYPE_INT64                  : Byte = 0x12
  val TYPE_DECIMAL128             : Byte = 0x13 // 128-bit IEEE 754-2008 decimal floating point
  val TYPE_DATE                   : Byte = 0x20 // Java8 LocalDate
  val TYPE_TIME                   : Byte = 0x21 // Java8 LocalTime
  val TYPE_DATETIME               : Byte = 0x22 // Java8 LocalDateTime
  val TYPE_BIGDECIMAL             : Byte = 0x23 // Java BigDecimal
  val TYPE_MINKEY                 : Byte = 0xFF.toByte
  val TYPE_MAXKEY                 : Byte = 0x7F

  /** Binary subtypes */
  val BINARY_SUBTYPE_GENERIC      : Byte = 0x00
  val BINARY_SUBTYPE_FUNCTION     : Byte = 0x01
  val BINARY_SUBTYPE_BINARY_OLD   : Byte = 0x02
  val BINARY_SUBTYPE_UUID_OLD     : Byte = 0x03
  val BINARY_SUBTYPE_UUID         : Byte = 0x04
  val BINARY_SUBTYPE_MD5          : Byte = 0x05
  val BINARY_SUBTYPE_USER_DEFINED : Byte = 0x80.toByte

  val TRUE                        : Byte = 0x01
  val FALSE                       : Byte = 0x00

  /** Encoding of "undefined" */
  val undefined = Array(TYPE_UNDEFINED)
  val `null` = Array(TYPE_NULL)

  /** Helper function convert ByteBuffer to Array[Byte]. */
  implicit def byteBuffer2ArrayByte(buffer: ByteBuffer): Array[Byte] = {
    val bytes = new Array[Byte](buffer.position)
    buffer.position(0)
    buffer.get(bytes)
    bytes
  }
}
