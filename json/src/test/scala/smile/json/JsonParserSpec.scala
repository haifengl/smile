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

class JsonParserSpec extends Specification {

  "The JsonParser" should {
    "parse 'null' to JsNull" in {
      JsonParser("null") === JsNull
    }
    "parse 'true' to JsTrue" in {
      JsonParser("true") === JsTrue
    }
    "parse 'false' to JsFalse" in {
      JsonParser("false") === JsFalse
    }
    "parse '0' to JsInt" in {
      JsonParser("0") === JsInt(0)
    }
    "parse '1.23' to JsDouble" in {
      JsonParser("1.23") === JsDouble(1.23)
    }
    "parse '-1E10' to JsDouble" in {
      JsonParser("-1E10") === JsDouble(-1E+10)
    }
    "parse '12.34e-10' to JsDouble" in {
      JsonParser("12.34e-10") === JsDouble(1.234E-9)
    }
    "parse \"xyz\" to JsString" in {
      JsonParser("\"xyz\"") === JsString("xyz")
    }
    "parse escapes in a JsString" in {
      JsonParser(""""\"\\/\b\f\n\r\t"""") === JsString("\"\\/\b\f\n\r\t")
      JsonParser("\"L\\" + "u00e4nder\"") === JsString("Länder")
    }
    "parse '1302806349000L' to JsLong" in {
      JsonParser("1302806349000L") === JsLong(1302806349000L)
    }
    "parse '1302806349000l' to JsLong" in {
      JsonParser("1302806349000l") === JsLong(1302806349000L)
    }
    /* we don't parse string to date/time/datetime/timestamp any more.
    "parse '2015-08-10T10:00:00.123Z' to JsDate" in {
      JsonParser("\"2015-08-10T10:00:00.123Z\"") === JsDate("2015-08-10T10:00:00.123Z")
    }
    */
    "parse 'CA761232-ED42-11CE-BACD-00AA0057B223' to JsUUID" in {
      JsonParser("\"CA761232-ED42-11CE-BACD-00AA0057B223\"") === JsUUID("CA761232-ED42-11CE-BACD-00AA0057B223")
    }
    "parse \"ObjectId(507f191e810c19729de860ea)\" to JsObjectId" in {
      JsonParser("\"ObjectId(507f191e810c19729de860ea)\"") === JsObjectId("507f191e810c19729de860ea")
    }
    "parse all representations of the slash (SOLIDUS) character in a JsString" in {
      JsonParser( "\"" + "/\\/\\u002f" + "\"") === JsString("///")
    }
    "parse a simple JsObject" in (
      JsonParser(""" { "key" :42, "key2": "value" }""") ===
        JsObject("key" -> JsInt(42), "key2" -> JsString("value"))
      )
    "parse a simple JsArray" in (
      JsonParser("""[null, 1.23 ,{"key":true } ] """) ===
        JsArray(JsNull, JsDouble(1.23), JsObject("key" -> JsTrue))
      )
    "parse directly from UTF-8 encoded bytes" in {
      val json = JsObject(
        "7-bit" -> JsString("This is regular 7-bit ASCII text."),
        "2-bytes" -> JsString("2-byte UTF-8 chars like £, æ or Ö"),
        "3-bytes" -> JsString("3-byte UTF-8 chars like ﾖ, ᄅ or ᐁ."),
        "4-bytes" -> JsString("4-byte UTF-8 chars like \uD801\uDC37, \uD852\uDF62 or \uD83D\uDE01."))
      JsonParser(json.prettyPrint.getBytes("UTF-8")) === json
    }
    "parse directly from UTF-8 encoded bytes when string starts with a multi-byte character" in {
      val json = JsString("£0.99")
      JsonParser(json.prettyPrint.getBytes("UTF-8")) === json
    }
    "be reentrant" in {
      val largeJsonSource = scala.io.Source.fromInputStream(getClass.getResourceAsStream("/test.json")).mkString
      List.fill(20)(largeJsonSource).map(JsonParser(_)).map {
        _.asInstanceOf[JsObject].fields("questions").asInstanceOf[JsArray].elements.size
      } === List.fill(20)(100)
    }

    "produce proper error messages" in {
      def errorMessage(input: String) =
        try JsonParser(input) catch { case e: JsonParser.ParsingException => e.getMessage }

      errorMessage("""[null, 1.23 {"key":true } ]""") ===
        """Unexpected character '{' at input index 12 (line 1, position 13), expected ']':
          |[null, 1.23 {"key":true } ]
          |            ^
          |""".stripMargin

      errorMessage("""[null, 1.23, {  key":true } ]""") ===
        """Unexpected character 'k' at input index 16 (line 1, position 17), expected '"':
          |[null, 1.23, {  key":true } ]
          |                ^
          |""".stripMargin

      errorMessage("""{"a}""") ===
        """Unexpected end-of-input at input index 4 (line 1, position 5), expected '"':
          |{"a}
          |    ^
          |""".stripMargin

      errorMessage("""{}x""") ===
        """Unexpected character 'x' at input index 2 (line 1, position 3), expected end-of-input:
          |{}x
          |  ^
          |""".stripMargin
    }

    "parse store.json" in {
      val json = JsObject(
        "store" -> JsObject(
          "book" -> JsArray(
            JsObject(
              "category" -> "reference",
              "author" -> "Nigel Rees",
              "title" -> "Sayings of the Century",
              "price" -> 8.95
            ),
            JsObject(
              "category" -> "fiction",
              "author" -> "Evelyn Waugh",
              "title" -> "Sword of Honour",
              "price" -> 12.99
            ),
            JsObject(
              "category" -> "fiction",
              "author" -> "Herman Melville",
              "title" -> "Moby Dick",
              "isbn" -> "0-553-21311-3",
              "price" -> 8.99
            ),
            JsObject(
              "category" -> "fiction",
              "author" -> "J. R. R. Tolkien",
              "title" -> "The Lord of the Rings",
              "isbn" -> "0-395-19395-8",
              "price" -> 22.99
            )
          ),
          "bicycle" -> JsObject(
            "color" -> "red",
            "price" -> 19.95
          )
        )
      )

      val jsonSource = scala.io.Source.fromInputStream(getClass.getResourceAsStream("/store.json")).mkString
      JsonParser(jsonSource) === json
    }
  }
}
