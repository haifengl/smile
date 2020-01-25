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

import scala.annotation.{switch, tailrec}
import scala.language.implicitConversions
import java.lang.{StringBuilder => JStringBuilder}
import java.nio.{CharBuffer, ByteBuffer}
import java.nio.charset.Charset

/**
 * Fast, no-dependency parser for JSON as defined by http://tools.ietf.org/html/rfc4627.
 * Adopt from spray-json.
 */
object JsonParser {
  def apply(input: ParserInput): JsValue = new JsonParser(input).parseJsValue()

  class ParsingException(val summary: String, val detail: String = "")
    extends RuntimeException(if (summary.isEmpty) detail else if (detail.isEmpty) summary else summary + ":" + detail)
}

class JsonParser(input: ParserInput) {
  import JsonParser.ParsingException

  private[this] val sb = new JStringBuilder
  private[this] var cursorChar: Char = input.nextChar()
  private[this] var jsValue: JsValue = _

  def parseJsValue(): JsValue = {
    ws()
    `value`()
    require(EOI)
    jsValue
  }

  ////////////////////// GRAMMAR ////////////////////////

  private final val EOI = '\uFFFF' // compile-time constant

  // http://tools.ietf.org/html/rfc4627#section-2.1
  private def `value`(): Unit = {
    val mark = input.cursor
    def simpleValue(matched: Boolean, value: JsValue) = if (matched) jsValue = value else fail("JSON Value", mark)
    (cursorChar: @switch) match {
      case 'f' => simpleValue(`false`(), JsFalse)
      case 'n' => simpleValue(`null`(), JsNull)
      case 't' => simpleValue(`true`(), JsTrue)
      case '{' => advance(); `object`()
      case '[' => advance(); `array`()
      case '0' | '1' | '2' | '3' | '4' | '5' | '6' | '7' | '8' | '9' | '-' => `number`()
      case '"' => `string`(); jsValue = if (sb.length == 0) JsString.empty else parseString(sb.toString)
      case _ => fail("JSON Value")
    }
  }

  private def `false`() = advance() && ch('a') && ch('l') && ch('s') && ws('e')
  private def `null`() = advance() && ch('u') && ch('l') && ws('l')
  private def `true`() = advance() && ch('r') && ch('u') && ws('e')
  private def parseString(s: String): JsValue = {
    // Don't try to parse date/time/datetime/timestamp any more. The user can
    // use asDate/asTime/asDateTime/asTimestamp on demand.
    if (s.length == JsObjectId.formatLength && JsObjectId.regex.pattern.matcher(s).matches) {
      JsObjectId(s.substring(9, 33))
    } else if (s.length == JsUUID.formatLength && JsUUID.regex.pattern.matcher(s).matches) {
      JsUUID(s)
    } else {
      JsString(s)
    }
  }

  // http://tools.ietf.org/html/rfc4627#section-2.2
  private def `object`(): Unit = {
    ws()
    jsValue = if (cursorChar != '}') {
      @tailrec def members(map: Map[String, JsValue]): Map[String, JsValue] = {
        `string`()
        require(':')
        ws()
        val key = sb.toString
        `value`()
        val nextMap = map.updated(key, jsValue)
        if (ws(',')) members(nextMap) else nextMap
      }
      var map = Map.empty[String, JsValue]
      map = members(map)
      require('}')
      JsObject(map)
    } else {
      advance()
      JsObject()
    }
    ws()
  }

  // http://tools.ietf.org/html/rfc4627#section-2.3
  private def `array`(): Unit = {
    ws()
    jsValue = if (cursorChar != ']') {
      val list = Vector.newBuilder[JsValue]
      @tailrec def values(): Unit = {
        `value`()
        list += jsValue
        if (ws(',')) values()
      }
      values()
      require(']')
      JsArray(list.result: _*)
    } else {
      advance()
      JsArray()
    }
    ws()
  }

  // http://tools.ietf.org/html/rfc4627#section-2.4
  // Leading zeros are not allowed.
  private def `number`() = {
    val start = input.cursor
    val startChar = cursorChar
    ch('-')
    `int`()
    val hasFrac = `frac`()
    val hasExp = `exp`()
    val hasLong = `long`()
    val hasCounter = `counter`()
    jsValue =
      if (startChar == '0' && input.cursor - start == 1) JsInt.zero
      else {
        val s = String.valueOf(input.sliceCharArray(start, input.cursor))
        if (hasFrac || hasExp) {
          val n = s.toDouble
          n match {
            case 0.0 => JsDouble.zero
            case 1.0 => JsDouble.one
            case _   => JsDouble(n)
          }
        } else {
          if (hasLong) JsLong(s.substring(0, s.length-1).toLong)
          else if (hasCounter) JsCounter(s.substring(0, s.length-1).toLong)
          else try {
            val n = s.toInt
            n match {
              case 0 => JsInt.zero
              case 1 => JsInt.one
              case _ => JsInt(n)
            }
          } catch {
            case _: NumberFormatException => JsLong(s.toLong)
          }
        }
      }
    ws()
  }

  private def `int`(): Unit = if (!ch('0')) oneOrMoreDigits()
  private def `frac`(): Boolean = if (ch('.')) {oneOrMoreDigits(); true} else false
  private def `exp`(): Boolean = if (ch('e') || ch('E')) { ch('-') || ch('+'); oneOrMoreDigits(); true } else false
  private def `long`(): Boolean = if (ch('l') || ch('L')) true else false
  private def `counter`(): Boolean = if (ch('c') || ch('C')) true else false

  private def oneOrMoreDigits(): Unit = if (DIGIT()) zeroOrMoreDigits() else fail("DIGIT")
  @tailrec private def zeroOrMoreDigits(): Unit = if (DIGIT()) zeroOrMoreDigits()

  private def DIGIT(): Boolean = cursorChar >= '0' && cursorChar <= '9' && advance()

  // http://tools.ietf.org/html/rfc4627#section-2.5
  private def `string`(): Unit = {
    if (cursorChar == '"') cursorChar = input.nextUtf8Char() else fail("'\"'")
    sb.setLength(0)
    while (`char`()) cursorChar = input.nextUtf8Char()
    require('"')
    ws()
  }

  private def `char`() =
  // simple bloom-filter that quick-matches the most frequent case of characters that are ok to append
  // (it doesn't match control chars, EOI, '"', '?', '\', 'b' and certain higher, non-ASCII chars)
    if (((1L << cursorChar) & ((31 - cursorChar) >> 31) & 0x7ffffffbefffffffL) != 0L) appendSB(cursorChar)
    else cursorChar match {
      case '"' | EOI => false
      case '\\' => advance(); `escaped`()
      case c => (c >= ' ') && appendSB(c)
    }

  private def `escaped`() = {
    def hexValue(c: Char): Int =
      if ('0' <= c && c <= '9') c - '0'
      else if ('a' <= c && c <= 'f') c - 87
      else if ('A' <= c && c <= 'F') c - 55
      else fail("hex digit")
    def unicode() = {
      var value = hexValue(cursorChar)
      advance()
      value = (value << 4) + hexValue(cursorChar)
      advance()
      value = (value << 4) + hexValue(cursorChar)
      advance()
      value = (value << 4) + hexValue(cursorChar)
      appendSB(value.toChar)
    }
    (cursorChar: @switch) match {
      case '"' | '/' | '\\' => appendSB(cursorChar)
      case 'b' => appendSB('\b')
      case 'f' => appendSB('\f')
      case 'n' => appendSB('\n')
      case 'r' => appendSB('\r')
      case 't' => appendSB('\t')
      case 'u' => advance(); unicode()
      case _ => fail("JSON escape sequence")
    }
  }

  @tailrec private def ws(): Unit =
  // fast test whether cursorChar is one of " \n\r\t"
    if (((1L << cursorChar) & ((cursorChar - 64) >> 31) & 0x100002600L) != 0L) { advance(); ws() }

  ////////////////////////// HELPERS //////////////////////////

  private def ch(c: Char): Boolean = if (cursorChar == c) { advance(); true } else false
  private def ws(c: Char): Boolean = if (ch(c)) { ws(); true } else false
  private def advance(): Boolean = { cursorChar = input.nextChar(); true }
  private def appendSB(c: Char): Boolean = { sb.append(c); true }
  private def require(c: Char): Unit = if (!ch(c)) fail(s"'$c'")

  private def fail(target: String, cursor: Int = input.cursor, errorChar: Char = cursorChar): Nothing = {
    val ParserInput.Line(lineNr, col, text) = input.getLine(cursor)
    val summary = {
      val unexpected =
        if (errorChar != EOI) {
          val c = if (Character.isISOControl(errorChar)) "\\u%04x" format errorChar.toInt else errorChar.toString
          s"character '$c'"
        } else "end-of-input"
      val expected = if (target != "'\uFFFF'") target else "end-of-input"
      s"Unexpected $unexpected at input index $cursor (line $lineNr, position $col), expected $expected"
    }
    val detail = {
      val sanitizedText = text.map(c => if (Character.isISOControl(c)) '?' else c)
      s"\n$sanitizedText\n${" " * (col-1)}^\n"
    }
    throw new ParsingException(summary, detail)
  }
}

trait ParserInput {
  /**
   * Advance the cursor and get the next char.
   * Since the char is required to be a 7-Bit ASCII char no decoding is required.
   */
  def nextChar(): Char

  /**
   * Advance the cursor and get the next char, which could potentially be outside
   * of the 7-Bit ASCII range. Therefore decoding might be required.
   */
  def nextUtf8Char(): Char

  def cursor: Int
  def length: Int
  def sliceString(start: Int, end: Int): String
  def sliceCharArray(start: Int, end: Int): Array[Char]
  def getLine(index: Int): ParserInput.Line
}

object ParserInput {
  private final val EOI = '\uFFFF' // compile-time constant
  private final val ErrorChar = '\uFFFD' // compile-time constant, universal UTF-8 replacement character '�'

  implicit def apply(string: String): StringBasedParserInput = new StringBasedParserInput(string)
  implicit def apply(chars: Array[Char]): CharArrayBasedParserInput = new CharArrayBasedParserInput(chars)
  implicit def apply(bytes: Array[Byte]): ByteArrayBasedParserInput = new ByteArrayBasedParserInput(bytes)

  case class Line(lineNr: Int, column: Int, text: String)

  abstract class DefaultParserInput extends ParserInput {
    protected var _cursor: Int = -1
    def cursor = _cursor
    def getLine(index: Int): Line = {
      val sb = new java.lang.StringBuilder
      @tailrec def rec(ix: Int, lineStartIx: Int, lineNr: Int): Line =
        nextUtf8Char() match {
          case '\n' if index > ix => sb.setLength(0); rec(ix + 1, ix + 1, lineNr + 1)
          case '\n' | EOI => Line(lineNr, index - lineStartIx + 1, sb.toString)
          case c => sb.append(c); rec(ix + 1, lineStartIx, lineNr)
        }
      val savedCursor = _cursor
      _cursor = -1
      val line = rec(ix = 0, lineStartIx = 0, lineNr = 1)
      _cursor = savedCursor
      line
    }
  }

  private val UTF8 = Charset.forName("UTF-8")

  /**
   * ParserInput reading directly off a byte array which is assumed to contain the UTF-8 encoded representation
   * of the JSON input, without requiring a separate decoding step.
   */
  class ByteArrayBasedParserInput(bytes: Array[Byte]) extends DefaultParserInput {
    private val byteBuffer = ByteBuffer.allocate(4)
    private val charBuffer = CharBuffer.allocate(2)
    private val decoder = UTF8.newDecoder()
    def nextChar() = {
      _cursor += 1
      if (_cursor < bytes.length) (bytes(_cursor) & 0xFF).toChar else EOI
    }
    def nextUtf8Char() = {
      @tailrec def decode(byte: Byte, remainingBytes: Int): Char = {
        byteBuffer.put(byte)
        if (remainingBytes > 0) {
          _cursor += 1
          if (_cursor < bytes.length) decode(bytes(_cursor), remainingBytes - 1) else ErrorChar
        } else {
          byteBuffer.flip()
          val coderResult = decoder.decode(byteBuffer, charBuffer, false)
          charBuffer.flip()
          val result = if (coderResult.isUnderflow & charBuffer.hasRemaining) charBuffer.get() else ErrorChar
          byteBuffer.clear()
          if (!charBuffer.hasRemaining) charBuffer.clear()
          result
        }
      }

      if (charBuffer.position() > 0) {
        val result = charBuffer.get()
        charBuffer.clear()
        result
      } else {
        _cursor += 1
        if (_cursor < bytes.length) {
          val byte = bytes(_cursor)
          if (byte >= 0) byte.toChar // 7-Bit ASCII
          else if ((byte & 0xE0) == 0xC0) decode(byte, 1) // 2-byte UTF-8 sequence
          else if ((byte & 0xF0) == 0xE0) decode(byte, 2) // 3-byte UTF-8 sequence
          else if ((byte & 0xF8) == 0xF0) decode(byte, 3) // 4-byte UTF-8 sequence
          else ErrorChar
        } else EOI
      }
    }
    def length = bytes.length
    def sliceString(start: Int, end: Int) = new String(bytes, start, end - start, UTF8)
    def sliceCharArray(start: Int, end: Int) =
      UTF8.decode(ByteBuffer.wrap(java.util.Arrays.copyOfRange(bytes, start, end))).array()
  }

  class StringBasedParserInput(string: String) extends DefaultParserInput {
    def nextChar(): Char = {
      _cursor += 1
      if (_cursor < string.length) string.charAt(_cursor) else EOI
    }
    def nextUtf8Char() = nextChar()
    def length = string.length
    def sliceString(start: Int, end: Int) = string.substring(start, end)
    def sliceCharArray(start: Int, end: Int) = {
      val chars = new Array[Char](end - start)
      string.getChars(start, end, chars, 0)
      chars
    }
  }

  class CharArrayBasedParserInput(chars: Array[Char]) extends DefaultParserInput {
    def nextChar(): Char = {
      _cursor += 1
      if (_cursor < chars.length) chars(_cursor) else EOI
    }
    def nextUtf8Char() = nextChar()
    def length = chars.length
    def sliceString(start: Int, end: Int) = new String(chars, start, end - start)
    def sliceCharArray(start: Int, end: Int) = java.util.Arrays.copyOfRange(chars, start, end)
  }
}
