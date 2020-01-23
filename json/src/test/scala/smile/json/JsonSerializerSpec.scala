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

/**
 * @author Haifeng Li
 */
class JsonSerializerSpec extends Specification {

  "The JsonSerializer" should {
    "serialize JsNull" in {
      val serializer = new JsonSerializer
      serializer.deserialize(serializer.serialize(JsNull)) === JsNull
    }
    "serialize JsTrue" in {
      val serializer = new JsonSerializer
      serializer.deserialize(serializer.serialize(JsTrue)) === JsTrue
    }
    "serialize JsFalse" in {
      val serializer = new JsonSerializer
      serializer.deserialize(serializer.serialize(JsFalse)) === JsFalse
    }
    "serialize 0" in {
      val serializer = new JsonSerializer
      serializer.deserialize(serializer.serialize(JsInt.zero)) === JsInt.zero
    }
    "serialize  '1.23'" in {
      val serializer = new JsonSerializer
      serializer.deserialize(serializer.serialize(JsDouble(1.23))) === JsDouble(1.23)
    }
    "serialize \"xyz\"" in {
      val serializer = new JsonSerializer
      serializer.deserialize(serializer.serialize(JsString("xyz"))) === JsString("xyz")
    }
    "serialize escapes in a JsString" in {
      val serializer = new JsonSerializer
      serializer.deserialize(serializer.serialize(JsString("\"\\/\b\f\n\r\t"))) === JsString("\"\\/\b\f\n\r\t")
      serializer.deserialize(serializer.serialize(JsString("Länder"))) === JsString("Länder")
    }
    "serialize '1302806349000'" in {
      val serializer = new JsonSerializer
      serializer.deserialize(serializer.serialize(JsLong(1302806349000L))) === JsLong(1302806349000L)
    }
    "serialize '2015-08-10'" in {
      val serializer = new JsonSerializer
      serializer.deserialize(serializer.serialize(JsDate("2015-08-10"))) === JsDate("2015-08-10")
    }
    "serialize '10:00:00'" in {
      val serializer = new JsonSerializer
      serializer.deserialize(serializer.serialize(JsTime("10:00:00"))) === JsTime("10:00:00")
    }
    "serialize '10:00:00.123'" in {
      val serializer = new JsonSerializer
      serializer.deserialize(serializer.serialize(JsTime("10:00:00.123"))) === JsTime("10:00:00")
    }
    "serialize '2015-08-10T10:00:00'" in {
      val serializer = new JsonSerializer
      serializer.deserialize(serializer.serialize(JsDateTime("2015-08-10T10:00:00"))) === JsDateTime("2015-08-10T10:00:00")
    }
    "serialize '2015-08-10T10:00:00.123'" in {
      val serializer = new JsonSerializer
      serializer.deserialize(serializer.serialize(JsDateTime("2015-08-10T10:00:00.123"))) === JsDateTime("2015-08-10T10:00:00")
    }
    "serialize '2015-08-10 10:00:00.123'" in {
      val serializer = new JsonSerializer
      serializer.deserialize(serializer.serialize(JsTimestamp("2015-08-10 10:00:00.123"))) === JsTimestamp("2015-08-10 10:00:00.123")
    }
    "serialize 'CA761232-ED42-11CE-BACD-00AA0057B223'" in {
      val serializer = new JsonSerializer
      serializer.deserialize(serializer.serialize(JsUUID("CA761232-ED42-11CE-BACD-00AA0057B223"))) === JsUUID("CA761232-ED42-11CE-BACD-00AA0057B223")
    }
    "serialize test.json" in {
      val serializer = new JsonSerializer
      val jsonSource = scala.io.Source.fromInputStream(getClass.getResourceAsStream("/test.json")).mkString
      val json = JsonParser(jsonSource)
      val bson = serializer.serialize(json)
      serializer.deserialize(bson) === json
    }
  }
}
