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

import java.lang.{StringBuilder => JStringBuilder}
import scala.annotation.tailrec

/**
 * A JsonPrinter serializes a JSON AST to a String.
 * Adopt from spray-json.
 */
trait JsonPrinter extends (JsValue => String) {

  def apply(x: JsValue): String = apply(x, None)

  def apply(x: JsValue, jsonpCallback: Option[String] = None, sb: JStringBuilder = new JStringBuilder(256)): String = {
    jsonpCallback match {
      case Some(callback) =>
        sb.append(callback).append('(')
        print(x, sb)
        sb.append(')')
      case None => print(x, sb)
    }
    sb.toString
  }

  def print(x: JsValue, sb: JStringBuilder): Unit

  protected def printLeaf(x: JsValue, sb: JStringBuilder): Unit = {
    x match {
      case JsNull        => sb.append("null")
      case JsUndefined   => sb.append("undefined")
      case JsBoolean(true)  => sb.append("true")
      case JsBoolean(false) => sb.append("false")
      case JsInt(x)      => sb.append(x)
      // Omit the ending 'L' to be compatible with other json parser/printer
      case JsLong(x)     => sb.append(x)//.append('L')
      case JsCounter(x)  => sb.append(x)
      case JsDouble(x)   => sb.append(x)
      case JsDecimal(x)  => sb.append('"').append(x.toPlainString).append('"')
      case JsDate(_) | JsTime(_) | JsDateTime(_) | JsTimestamp(_) => sb.append('"').append(x.toString).append('"')
      case JsBinary(x)   => sb.append('"').append(x.map("%02X" format _).mkString).append('"')
      case JsString(x)   => printString(x, sb)
      case JsUUID(x)     => sb.append('"').append(x.toString).append('"')
      case JsObjectId(x) => sb.append('"').append(x.toString).append('"')
      case _               => throw new IllegalStateException
    }
  }

  protected def printString(s: String, sb: JStringBuilder): Unit = {
    import JsonPrinter._
    @tailrec def firstToBeEncoded(ix: Int = 0): Int =
      if (ix == s.length) -1 else if (requiresEncoding(s.charAt(ix))) ix else firstToBeEncoded(ix + 1)

    sb.append('"')
    firstToBeEncoded() match {
      case -1 => sb.append(s)
      case first =>
        sb.append(s, 0, first)
        @tailrec def append(ix: Int): Unit =
          if (ix < s.length) {
            s.charAt(ix) match {
              case c if !requiresEncoding(c) => sb.append(c)
              case '"' => sb.append("\\\"")
              case '\\' => sb.append("\\\\")
              case '\b' => sb.append("\\b")
              case '\f' => sb.append("\\f")
              case '\n' => sb.append("\\n")
              case '\r' => sb.append("\\r")
              case '\t' => sb.append("\\t")
              case x if x <= 0xF => sb.append("\\u000").append(Integer.toHexString(x))
              case x if x <= 0xFF => sb.append("\\u00").append(Integer.toHexString(x))
              case x if x <= 0xFFF => sb.append("\\u0").append(Integer.toHexString(x))
              case x => sb.append("\\u").append(Integer.toHexString(x))
            }
            append(ix + 1)
          }
        append(first)
    }
    sb.append('"')
  }

  protected def printSeq[A](iterable: Iterable[A], printSeparator: => Unit)(f: A => Unit): Unit = {
    var first = true
    iterable.foreach { a =>
      if (first) first = false else printSeparator
      f(a)
    }
  }
}

object JsonPrinter {
  def requiresEncoding(c: Char): Boolean =
  // from RFC 4627
  // unescaped = %x20-21 / %x23-5B / %x5D-10FFFF
    c match {
      case '"'  => true
      case '\\' => true
      case c    => c < 0x20
    }
}
