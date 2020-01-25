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

import org.specs2.mutable._

class CompactPrinterSpec extends Specification {

  "The CompactPrinter" should {
    "print JsNull to 'null'" in {
      CompactPrinter(JsNull) mustEqual "null"
    }
    "print JsTrue to 'true'" in {
      CompactPrinter(JsTrue) mustEqual "true"
    }
    "print JsFalse to 'false'" in {
      CompactPrinter(JsFalse) mustEqual "false"
    }
    "print JsInt(0) to '0'" in {
      CompactPrinter(JsInt(0)) mustEqual "0"
    }
    "print JsLong(12435234235235235) to '12435234235235235'" in {
      CompactPrinter(JsLong(12435234235235235L)) mustEqual "12435234235235235"
    }
    "print JsDouble(1.23) to '1.23'" in {
      CompactPrinter(JsDouble(1.23)) mustEqual "1.23"
    }
    "print JsDouble(-1E10) to '-1E10'" in {
      CompactPrinter(JsDouble(-1E10)) mustEqual "-1.0E10"
    }
    "print JsDouble(12.34e-10) to '12.34e-10'" in {
      CompactPrinter(JsDouble(12.34e-10)) mustEqual "1.234E-9"
    }
    "print JsDecimal(12345678912345012412.1243243253252352352352352) to '12345678912345012412.1243243253252352352352352'" in {
      CompactPrinter(JsDecimal("12345678912345012412.1243243253252352352352352")) mustEqual """"12345678912345012412.1243243253252352352352352""""
    }
    "print JsString(\"xyz\") to \"xyz\"" in {
      CompactPrinter(JsString("xyz")) mustEqual "\"xyz\""
    }
    "properly escape special chars in JsString" in {
      CompactPrinter(JsString("\"\\\b\f\n\r\t")) mustEqual """"\"\\\b\f\n\r\t""""
      CompactPrinter(JsString("\u1000")) mustEqual "\"\u1000\""
      CompactPrinter(JsString("\u0100")) mustEqual "\"\u0100\""
      CompactPrinter(JsString("\u0010")) mustEqual "\"\\u0010\""
      CompactPrinter(JsString("\u0001")) mustEqual "\"\\u0001\""
      CompactPrinter(JsString("\u001e")) mustEqual "\"\\u001e\""
      // don't escape as it isn't required by the spec
      CompactPrinter(JsString("\u007f")) mustEqual "\"\u007f\""
      CompactPrinter(JsString("飞机因此受到损伤")) mustEqual "\"飞机因此受到损伤\""
      CompactPrinter(JsString("\uD834\uDD1E")) mustEqual "\"\uD834\uDD1E\""
    }
    "properly print a simple JsObject" in (
      CompactPrinter(JsObject("key" -> JsInt(42), "key2" -> JsString("value")))
        mustEqual """{"key2":"value","key":42}"""
      )
    "properly print a simple JsArray" in (
      CompactPrinter(JsArray(JsNull, JsDouble(1.23), JsObject("key" -> JsBoolean(true))))
        mustEqual """[null,1.23,{"key":true}]"""
      )
    "properly print a JSON padding (JSONP) if requested" in {
      CompactPrinter(JsTrue, Some("customCallback")) mustEqual("customCallback(true)")
    }
  }
}
