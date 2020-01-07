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

import java.lang.StringBuilder

/**
 * A JsonPrinter that produces compact JSON source without any superfluous whitespace.
 * Adopt from spray-json.
 */
trait CompactPrinter extends JsonPrinter {

  def print(x: JsValue, sb: StringBuilder): Unit = {
    x match {
      case JsObject(x) => printObject(x, sb)
      case JsArray(x)  => printArray(x, sb)
      case _ => printLeaf(x, sb)
    }
  }

  protected def printObject(members: Iterable[(String, JsValue)], sb: StringBuilder): Unit = {
    sb.append('{')
    printSeq(members, sb.append(',')) { m =>
      printString(m._1, sb)
      sb.append(':')
      print(m._2, sb)
    }
    sb.append('}')
  }

  protected def printArray(elements: Iterable[JsValue], sb: StringBuilder): Unit = {
    sb.append('[')
    printSeq(elements, sb.append(','))(print(_, sb))
    sb.append(']')
  }
}

object CompactPrinter extends CompactPrinter
