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

import java.time.{LocalDate, LocalTime, LocalDateTime, ZoneOffset}
import java.sql.Timestamp
import java.util.{Date, UUID}
import java.math.BigDecimal
import scala.language.dynamics
import scala.language.implicitConversions

/**
 * JSON value.
 *
 * @author Haifeng Li
 */
sealed trait JsValue extends Dynamic {
  override def toString: String = compactPrint
  def compactPrint: String = CompactPrinter(this)
  def prettyPrint: String = PrettyPrinter(this)

  def apply(key: String): JsValue = {
    throw new UnsupportedOperationException
  }

  def apply(index: Int): JsValue = {
    throw new UnsupportedOperationException
  }

  def apply(start: Int, end: Int): JsArray = {
    throw new UnsupportedOperationException
  }

  def apply(start: Int, end: Int, step: Int): JsArray = {
    throw new UnsupportedOperationException
  }

  def apply(range: Range): JsArray = {
    throw new UnsupportedOperationException
  }

  def applyDynamic(key: String): JsValue = {
    throw new UnsupportedOperationException
  }

  def selectDynamic(key: String): JsValue = {
    throw new UnsupportedOperationException
  }

  def remove(key: String): Option[JsValue] = {
    throw new UnsupportedOperationException
  }

  def remove(index: Int): JsValue = {
    throw new UnsupportedOperationException
  }

  def update(key: String, value: JsValue): JsValue = {
    throw new UnsupportedOperationException
  }

  def update(index: Int, value: JsValue): JsValue = {
    throw new UnsupportedOperationException
  }

  def updateDynamic(key: String)(value: JsValue): JsValue = {
    throw new UnsupportedOperationException
  }

  def updateDynamic(index: Int)(value: JsValue): JsValue = {
    throw new UnsupportedOperationException
  }

  def get(key: String): Option[JsValue] = {
    throw new UnsupportedOperationException
  }

  def asBoolean: Boolean = {
    throw new UnsupportedOperationException
  }

  def asInt: Int = {
    throw new UnsupportedOperationException
  }

  def asLong: Long = {
    throw new UnsupportedOperationException
  }

  def asDouble: Double = {
    throw new UnsupportedOperationException
  }

  def asDecimal: BigDecimal = {
    throw new UnsupportedOperationException
  }

  def asDate: LocalDate = {
    throw new UnsupportedOperationException
  }

  def asTime: LocalTime = {
    throw new UnsupportedOperationException
  }

  def asDateTime: LocalDateTime = {
    throw new UnsupportedOperationException
  }

  def asTimestamp: Timestamp = {
    throw new UnsupportedOperationException
  }
}

object JsValueOrdering extends Ordering[JsValue] {
  def compare(a: JsValue, b: JsValue) = {
    try {
      a.asDouble.compare(b.asDouble)
    } catch {
      case _: UnsupportedOperationException => a.toString.compare(b.toString)
    }
  }
}

case object JsNull extends JsValue {
  override def toString = "null"
  override def asBoolean: Boolean = false
  override def asInt: Int = 0
  override def asLong: Long = 0L
  override def asDouble: Double = 0.0
}

case object JsUndefined extends JsValue {
  override def toString = "undefined"
  override def asBoolean: Boolean = false
  override def asInt: Int = 0
  override def asLong: Long = 0L
  override def asDouble: Double = 0.0
}

case class JsBoolean(value: Boolean) extends JsValue with Ordered[JsBoolean] {
  override def toString = value.toString
  override def equals(o: Any) = o match {
    case that: Boolean => value == that
    case JsBoolean(that) => value == that
    case _ => false
  }
  override def asBoolean: Boolean = value
  override def asInt: Int = if (value) 1 else 0
  override def asLong: Long = if (value) 1L else 0L
  override def asDouble: Double = if (value) 1.0 else 0.0

  override def compare(that: JsBoolean): Int = {
    (value, that.value) match {
      case (true, true) => 0
      case (false, false) => 0
      case (true, false) => 1
      case (false, true) => -1
    }
  }
}

object JsBoolean {
  def apply(b: Byte) = if (b != 0) JsTrue else JsFalse
  def apply(b: Int)  = if (b != 0) JsTrue else JsFalse
}

case class JsInt(value: Int) extends JsValue with Ordered[JsInt] {
  override def toString = value.toString
  override def equals(o: Any) = o match {
    case that: Int => value == that
    case that: Short => value == that
    case that: Long => value == that
    case that: Byte => value == that
    case JsInt(that) => value == that
    case JsLong(that) => value == that
    case JsCounter(that) => value == that
    case _ => false
  }
  override def asBoolean: Boolean = value != 0
  override def asInt: Int = value
  override def asLong: Long = value
  override def asDouble: Double = value

  override def compare(that: JsInt): Int = {
    value - that.value
  }
}

object JsInt {
  val zero = JsInt(0)
  val one = JsInt(1)
}

case class JsLong(value: Long) extends JsValue with Ordered[JsLong] {
  override def toString = value.toString
  override def equals(o: Any) = o match {
    case that: Int => value == that
    case that: Short => value == that
    case that: Long => value == that
    case that: Byte => value == that
    case JsInt(that) => value == that
    case JsLong(that) => value == that
    case JsCounter(that) => value == that
    case _ => false
  }
  override def asBoolean: Boolean = value != 0
  override def asInt: Int = value.toInt
  override def asLong: Long = value
  override def asDouble: Double = value

  override def compare(that: JsLong): Int = {
    value.compare(that.value)
  }
}

object JsLong {
  val zero = JsLong(0L)
  val one = JsLong(1L)
}

/** A counter is a 64 bit integer. The difference from JsLong
  * is mostly for internal representation in database. For encoding
  * reason, the effective number of bits are 56, which should be
  * big enough in practice.
  */
case class JsCounter(value: Long) extends JsValue with Ordered[JsCounter] {
  override def toString = value.toString
  override def equals(o: Any) = o match {
    case that: Int => value == that
    case that: Short => value == that
    case that: Long => value == that
    case that: Byte => value == that
    case JsInt(that) => value == that
    case JsLong(that) => value == that
    case JsCounter(that) => value == that
    case _ => false
  }
  override def asBoolean: Boolean = value != 0
  override def asInt: Int = value.toInt
  override def asLong: Long = value
  override def asDouble: Double = value

  override def compare(that: JsCounter): Int = {
    value.compare(that.value)
  }
}

object JsCounter {
  val zero = JsCounter(0L)
  val one = JsCounter(1L)
}

case class JsDouble(value: Double) extends JsValue with Ordered[JsDouble] {
  override def toString = value.toString
  override def equals(o: Any) = o match {
    case that: Double => value == that
    case JsDouble(that) => value == that
    case _ => false
  }
  override def asBoolean: Boolean = Math.abs(value) > 2*Double.MinValue
  override def asInt: Int = value.toInt
  override def asLong: Long = value.toLong
  override def asDouble: Double = value

  override def compare(that: JsDouble): Int = {
    value.compare(that.value)
  }
}

object JsDouble {
  val zero = JsDouble(0.0)
  val one = JsDouble(1.0)
}

case class JsDecimal(value: BigDecimal) extends JsValue with Ordered[JsDecimal] {
  override def toString = value.toPlainString
  override def equals(o: Any) = o match {
    case that: BigDecimal => value == that
    case JsDecimal(that) => value == that
    case _ => false
  }
  override def asBoolean: Boolean = value == BigDecimal.ZERO
  override def asInt: Int = value.intValueExact
  override def asLong: Long = value.longValueExact
  override def asDouble: Double = value.doubleValue

  override def compare(that: JsDecimal): Int = {
    value.compareTo(that.value)
  }
}

object JsDecimal {
  val zero = JsDecimal(BigDecimal.ZERO)
  val one = JsDecimal(BigDecimal.ONE)
  /** Converts a string representation into a JsDecimal.
    * The string representation consists of an optional sign,
    * '+' ( '\u002B') or '-' ('\u002D'), followed by a sequence
    * of zero or more decimal digits ("the integer"), optionally
    * followed by a fraction, optionally followed by an exponent.
    */
  def apply(x: String): JsDecimal = JsDecimal(new BigDecimal(x))
}

case class JsString(value: String) extends JsValue with Ordered[JsString] {
  override def toString = value
  override def equals(o: Any) = o match {
    case that: String => value == that
    case JsString(that) => value == that
    case _ => false
  }
  override def asBoolean: Boolean = !value.isEmpty
  override def asInt: Int = Integer.parseInt(value)
  override def asLong: Long = java.lang.Long.parseLong(value)
  override def asDouble: Double = java.lang.Double.parseDouble(value)
  override def asDecimal: BigDecimal = new BigDecimal(value)
  override def asDate: LocalDate = LocalDate.parse(value)
  override def asTime: LocalTime = LocalTime.parse(value)
  override def asDateTime: LocalDateTime = LocalDateTime.parse(value)
  override def asTimestamp: Timestamp = Timestamp.valueOf(value)

  override def compare(that: JsString): Int = {
    value.compare(that.value)
  }
}

object JsString {
  val empty = JsString("")
}

/** An immutable date without a time-zone in the ISO-8601 calendar system,
  * often viewed as year-month-day such as 2007-12-03.
  */
case class JsDate(value: LocalDate) extends JsValue with Ordered[JsDate] {
  /** The output will be in the ISO-8601 format uuuu-MM-dd. */
  override def toString = value.toString
  override def equals(o: Any) = o match {
    case that: LocalDate => value == that
    case JsDate(that) => value == that
    case _ => false
  }
  override def asDate: LocalDate = value
  /** Converts this date to the Epoch Day.
    * The Epoch Day count is a simple incrementing count of days
    * where day 0 is 1970-01-01 (ISO).
    */
  override def asLong: Long = value.toEpochDay
  /** Converts this date to the Epoch Day.
    * The Epoch Day count is a simple incrementing count of days
    * where day 0 is 1970-01-01 (ISO).
    */
  override def asDouble: Double = value.toEpochDay

  override def compare(that: JsDate): Int = {
    value.compareTo(that.value)
  }
}

object JsDate {
  /** Obtains an instance from the epoch day count. */
  def apply(date: Long): JsDate = new JsDate(LocalDate.ofEpochDay(date))
  /** The string must represent a valid date and is parsed using DateTimeFormatter.ISO_LOCAL_DATE. */
  def apply(date: String): JsDate = new JsDate(LocalDate.parse(date))
}

/** An immutable time without a time-zone in the ISO-8601 calendar system,
  * often viewed as hour-minute-second such as 10:15:30. Although LocalTime/JsTime
  * can be represented to nanosecond precision, a JSON serializer may not
  * store the nano-of-second field to save the space. To preserve the high
  * precision of time, JsTimestamp should be employed.
  */
case class JsTime(value: LocalTime) extends JsValue with Ordered[JsTime] {
  /** The output will be one of the following ISO-8601 formats:
    *  - HH:mm
    *  - HH:mm:ss
    *  - HH:mm:ss.SSS
    *  - HH:mm:ss.SSSSSS
    *  - HH:mm:ss.SSSSSSSSS
    *
    * The format used will be the shortest that outputs the full
    * value of the time where the omitted parts are implied to be zero.
    */
  override def toString = value.toString
  override def equals(o: Any) = o match {
    case that: LocalTime => value == that
    case JsTime(that) => value == that
    case _ => false
  }
  override def asTime: LocalTime = value

  /** Converts this time as seconds of day, from 0 to 24 * 60 * 60 - 1. */
  override def asInt: Int = value.toSecondOfDay
  /** Converts this time as nanos of day, from 0 to 24 * 60 * 60 * 1,000,000,000 - 1. */
  override def asLong: Long = value.toNanoOfDay
  /** Converts this time as nanos of day, from 0 to 24 * 60 * 60 * 1,000,000,000 - 1. */
  override def asDouble: Double = value.toNanoOfDay

  override def compare(that: JsTime): Int = {
    value.compareTo(that.value)
  }
}

object JsTime {
  /** Obtains an instance from a nanos-of-day value. */
  def apply(time: Long): JsTime = new JsTime(LocalTime.ofNanoOfDay(time))
  /** The string must represent a valid time and is parsed using DateTimeFormatter.ISO_LOCAL_TIME. */
  def apply(time: String): JsTime = new JsTime(LocalTime.parse(time))
}

/** An immutable date-time without a time-zone in the ISO-8601 calendar system,
  * such as 2007-12-03T10:15:30. Although LocalTime/JsTime
  * can be represented to nanosecond precision, a JSON serializer may not
  * store the nano-of-second field to save the space. To preserve the high
  * precision of time, JsTimestamp should be employed.
  */
case class JsDateTime(value: LocalDateTime) extends JsValue with Ordered[JsDateTime] {
  /** The output will be one of the following ISO-8601 formats:
    *  - uuuu-MM-dd'T'HH:mm
    *  - uuuu-MM-dd'T'HH:mm:ss
    *  - uuuu-MM-dd'T'HH:mm:ss.SSS
    *  - uuuu-MM-dd'T'HH:mm:ss.SSSSSS
    *  - uuuu-MM-dd'T'HH:mm:ss.SSSSSSSSS
    *
    * The format used will be the shortest that outputs the full
    * value of the time where the omitted parts are implied to be zero.
    */
  override def toString = value.toString
  override def equals(o: Any) = o match {
    case that: LocalDateTime => value == that
    case JsDateTime(that) => value == that
    case _ => false
  }
  override def asDate: LocalDate = value.toLocalDate
  override def asTime: LocalTime = value.toLocalTime
  override def asDateTime: LocalDateTime = value
  /** Converts this date-time to the number of seconds from the epoch of 1970-01-01T00:00:00Z. */
  override def asLong: Long = value.toEpochSecond(ZoneOffset.UTC)
  /** Converts this date-time to the number of seconds from the epoch of 1970-01-01T00:00:00Z. */
  override def asDouble: Double = value.toEpochSecond(ZoneOffset.UTC)

  override def compare(that: JsDateTime): Int = {
    value.compareTo(that.value)
  }
}

object JsDateTime {
  /** Obtains an instance from a date and time. */
  def apply(date: LocalDate, time: LocalTime): JsDateTime = new JsDateTime(LocalDateTime.of(date, time))
  /** The string must represent a valid date-time and is parsed using DateTimeFormatter.ISO_LOCAL_DATE_TIME. */
  def apply(datetime: String): JsDateTime = new JsDateTime(LocalDateTime.parse(datetime))
}

/** An SQL TIMESTAMP value. It adds the ability to hold the SQL TIMESTAMP
  * fractional seconds value, by allowing the specification of fractional
  * seconds to a precision of nanoseconds. Support the JDBC escape syntax
  * for timestamp values.
  *
  * The precision of a Timestamp object is calculated to be either:
  *
  *  - 19 , which is the number of characters in yyyy-mm-dd hh:mm:ss
  *  - 20 + s , which is the number of characters in the yyyy-mm-dd hh:mm:ss.[fff...]
  *    and s represents the scale of the given Timestamp, its fractional seconds precision.
  */
case class JsTimestamp(value: Timestamp) extends JsValue with Ordered[JsTimestamp] {
  override def toString = value.toString
  override def equals(o: Any) = o match {
    case that: Timestamp => value == that
    case JsTimestamp(that) => value == that
    case _ => false
  }
  override def asDateTime: LocalDateTime = value.toLocalDateTime
  override def asLong: Long = value.getTime
  override def asDouble: Double = value.getTime

  override def compare(that: JsTimestamp): Int = {
    value.compareTo(that.value)
  }
}

object JsTimestamp {
  /** Lifts a java.util.Date object to a JsTimestamp value. */
  def apply(time: Date): JsTimestamp = new JsTimestamp(new Timestamp(time.getTime))

  /** Constructs a JsTimestamp object using the milliseconds from the epoch.
    *
    * @param time milliseconds since January 1, 1970, 00:00:00 GMT.
    *             A negative number is the number of milliseconds
    *             before January 1, 1970, 00:00:00 GMT.
    */
  def apply(time: Long): JsTimestamp = new JsTimestamp(new Timestamp(time))

  /** Converts a String object in JDBC timestamp escape format
    * to a JsTimestamp value.
    *
    * @param time timestamp in format yyyy-[m]m-[d]d hh:mm:ss[.f...].
    *             The fractional seconds may be omitted. The leading zero
    *             for mm and dd may also be omitted.
    */
  def apply(time: String): JsTimestamp = new JsTimestamp(Timestamp.valueOf(time))
}

case class JsUUID(value: UUID) extends JsValue {
  override def toString = value.toString
  override def equals(o: Any) = o match {
    case that: UUID => value == that
    case JsUUID(that) => value == that
    case _ => false
  }
}

object JsUUID {
  val regex = """[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}""".r
  val formatLength = 36
  def apply() = new JsUUID(UUID.randomUUID)
  def apply(mostSigBits: Long, leastSigBits: Long) = new JsUUID(new UUID(mostSigBits, leastSigBits))
  def apply(uuid: String) = new JsUUID(UUID.fromString(uuid))
  def apply(uuid: Array[Byte]) = new JsUUID(UUID.nameUUIDFromBytes(uuid))
}

case class JsObjectId(value: ObjectId) extends JsValue {
  override def toString = value.toString
  override def equals(o: Any) = o match {
    case that: ObjectId => value == that
    case JsObjectId(that) => value == that
    case _ => false
  }
}

object JsObjectId {
  val regex = """ObjectId\([0-9a-fA-F]{24}\)""".r
  val formatLength = 34
  def apply() = new JsObjectId(ObjectId.generate)
  def apply(id: String) = new JsObjectId(ObjectId(id))
  def apply(id: Array[Byte]) = new JsObjectId(ObjectId(id))
}

case class JsBinary(value: Array[Byte]) extends JsValue {
  override def toString = value.map("%02X" format _).mkString
  override def equals(o: Any) = o match {
    case that: Array[Byte] => value.sameElements(that)
    case JsBinary(that) => value.sameElements(that)
    case _ => false
  }
}

case class JsObject(fields: collection.mutable.Map[String, JsValue]) extends JsValue {
  override def apply(key: String): JsValue = {
    if (fields.contains(key))
      fields(key)
    else
      JsUndefined
  }

  override def applyDynamic(key: String): JsValue = apply(key)

  override def selectDynamic(key: String): JsValue = apply(key)

  override def remove(key: String): Option[JsValue] = fields.remove(key)

  override def update(key: String, value: JsValue): JsValue = {
    fields(key) = value
    value
  }

  override def updateDynamic(key: String)(value: JsValue): JsValue = update(key, value)

  override def get(key: String): Option[JsValue] = {
    if (fields.contains(key))
      Some(fields(key))
    else
      None
  }

  /** Deep merge another object into this object.
   *
   * @param that the object to merge into this object.
   * @return the merged object
   */
  def ++=(that: JsObject): JsObject = {
    that.fields foreach {
      case (key, value) if fields.contains(key) =>
        (fields(key), value) match {
          case (a: JsObject, b: JsObject) => a ++= b
          case _ => fields(key) = value
        }
      case (key, value) => fields(key) = value
    }
    this
  }
}

object JsObject {
  def apply(fields: (String, JsValue)*) = new JsObject(collection.mutable.Map(fields: _*))
  def apply(map: Map[String, JsValue]) = new JsObject(collection.mutable.Map() ++ map)
}

case class JsArray(elements: collection.mutable.ArrayBuffer[JsValue]) extends JsValue with Iterable[JsValue] {
  override def iterator: Iterator[JsValue] = elements.iterator
  override def find(p: (JsValue) => Boolean): Option[JsValue] = elements.find(p)
  override def exists(p: (JsValue) => Boolean): Boolean = elements.exists(p)
  override def forall(p: (JsValue) => Boolean): Boolean = elements.forall(p)
  override def foreach[U](p: (JsValue) => U): Unit = elements.foreach(p)
  override def knownSize: Int = elements.knownSize
  override def isEmpty: Boolean = elements.isEmpty

  // Traversable.toString overloads JsValue.toString.
  // Get it back.
  override def toString: String = compactPrint

  override def size: Int = elements.size

  override def equals(o: Any): Boolean = o match {
    case that: Array[JsValue] => elements.sameElements(that)
    case JsArray(that) => elements.sameElements(that)
    case _ => false
  }

  override def apply(index: Int): JsValue = {
    val i = if (index >= 0) index else elements.size + index
    elements(i)
  }

  override def apply(start: Int, end: Int): JsArray = {
    apply(start until end)
  }

  override def apply(start: Int, end: Int, step: Int): JsArray = {
    apply(start until end by step)
  }

  override def apply(range: Range): JsArray = {
    JsArray(range.map(elements(_)))
  }

  override def remove(index: Int): JsValue = {
    val i = if (index >= 0) index else elements.size + index
    elements.remove(i)
  }

  override def update(index: Int, value: JsValue): JsValue = {
    val i = if (index >= 0) index else elements.size + index
    elements(i) = value
    value
  }

  override def updateDynamic(index: Int)(value: JsValue): JsValue = update(index, value)

  /** Appends a single element to this array and returns
   *  the identity of the array. It takes constant amortized time.
   *
   * @param elem  the element to append.
   * @return      the updated array.
   */
  def +=(elem: JsValue): JsArray = {
    elements += elem
    this
  }

  /** Appends a number of elements provided by an iterable object.
   *  The identity of the array is returned.
   *
   *  @param xs    the iterable object.
   *  @return      the updated buffer.
   */
  def ++=(xs: IterableOnce[JsValue]): JsValue = {
    elements ++= xs
    this
  }

  /** Prepends a single element to this buffer and returns
   *  the identity of the array. It takes time linear in
   *  the buffer size.
   *
   *  @param elem  the element to prepend.
   *  @return      the updated array.
   */
  def +=:(elem: JsValue): JsValue = {
    elem +=: elements
    this
  }

  /** Prepends a number of elements provided by an iterable object.
   *  The identity of the array is returned.
   *
   *  @param xs    the iterable object.
   *  @return      the updated array.
   */
  def ++=:(xs: IterableOnce[JsValue]): JsValue = {
    xs ++=: elements
    this
  }

  /** Inserts new elements at the index `n`. Opposed to method
   *  `update`, this method will not replace an element with a new
   *  one. Instead, it will insert a new element at index `n`.
   *
   *  @param idx   the index where a new element will be inserted.
   *  @param seq   the iterable object providing all elements to insert.
   */
  def insertAll(idx: Int, seq: Iterable[JsValue]): Unit = {
    elements.insertAll(idx, seq)
  }

  /** Removes the element on a given index position. It takes time linear in
   *  the buffer size.
   *
   *  @param idx     the index which refers to the first element to delete.
   *  @param count   the number of elements to delete
   */
  def remove(idx: Int, count: Int): Unit = {
    elements.remove(idx, count)
  }
}

object JsArray {
  def apply(elements: JsValue*) = new JsArray(collection.mutable.ArrayBuffer(elements: _*))
}
