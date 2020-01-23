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
import scala.annotation.tailrec

/**
 * A JsonPrinter that produces a nicely readable JSON source.
 * Adopt from spray-json.
 */
trait PrettyPrinter extends JsonPrinter {
  val Indent = 2

  def print(x: JsValue, sb: StringBuilder): Unit = {
    print(x, sb, 0)
  }

  protected def print(x: JsValue, sb: StringBuilder, indent: Int): Unit = {
    x match {
      case JsObject(x) => printObject(x, sb, indent)
      case JsArray(x)  => printArray(x, sb, indent)
      case _ => printLeaf(x, sb)
    }
  }

  protected def printObject(members: Iterable[(String, JsValue)], sb: StringBuilder, indent: Int): Unit = {
    sb.append("{\n")
    printSeq(members, sb.append(",\n")) { m =>
      printIndent(sb, indent + Indent)
      printString(m._1, sb)
      sb.append(": ")
      print(m._2, sb, indent + Indent)
    }
    sb.append('\n')
    printIndent(sb, indent)
    sb.append("}")
  }

  protected def printArray(elements: Iterable[JsValue], sb: StringBuilder, indent: Int): Unit = {
    sb.append('[')
    printSeq(elements, sb.append(", "))(print(_, sb, indent))
    sb.append(']')
  }

  protected def printIndent(sb: StringBuilder, indent: Int): Unit = {
    @tailrec def rec(indent: Int): Unit =
      if (indent > 0) {
        sb.append(' ')
        rec(indent - 1)
      }
    rec(indent)
  }
}

object PrettyPrinter extends PrettyPrinter
