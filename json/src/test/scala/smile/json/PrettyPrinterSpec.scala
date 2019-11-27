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

class PrettyPrinterSpec extends Specification {

  "The PrettyPrinter" should {
    "print a more complicated JsObject nicely aligned" in {
      val json = JsonParser {
        """{
          |  "Boolean no": false,
          |  "Boolean yes":true,
          |  "Unic\u00f8de" :  "Long string with newline\nescape",
          |  "key with \"quotes\"" : "string",
          |  "key with spaces": null,
          |  "number": -1.2323424E-5,
          |  "simpleKey" : "some value",
          |  "sub object" : {
          |    "sub key": 26.5,
          |    "a": "b",
          |    "array": [1, 2, { "yes":1, "no":0 }, ["a", "b", null], false]
          |  },
          |  "zero": 0
          |}""".stripMargin
      }
      
      PrettyPrinter(json) mustEqual {
        """{
          |  "sub object": {
          |    "a": "b",
          |    "array": [1, 2, {
          |      "no": 0,
          |      "yes": 1
          |    }, ["a", "b", null], false],
          |    "sub key": 26.5
          |  },
          |  "zero": 0,
          |  "number": -1.2323424E-5,
          |  "key with \"quotes\"": "string",
          |  "key with spaces": null,
          |  "Boolean no": false,
          |  "Unicøde": "Long string with newline\nescape",
          |  "Boolean yes": true,
          |  "simpleKey": "some value"
          |}""".stripMargin
      }
    }
  }
}
