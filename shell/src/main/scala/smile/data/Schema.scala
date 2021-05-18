/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
 *
 * Smile Shell is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile Shell is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.data

import spray.json._
import smile.data.`type`.StructType

final class StructTypeOps(schema: StructType) {
  def json(value: JsValue): Option[Tuple] = {
    value match {
      case JsArray(elements) =>
        if (elements.length < schema.length)
          return None

        val row = new Array[AnyRef](schema.length)
        for (i <- 0 until schema.length) {
          try {
            row(i) = schema.field(i).valueOf(elements(i).compactPrint)
          } catch {
            case _ : Throwable => return None
          }
        }
        Some(Tuple.of(row, schema))

      case JsObject(fields) =>
        val row = new Array[AnyRef](schema.length)
        for (i <- 0 until schema.length) {
          val value = fields.get(schema.field(i).name)
          if (value.isDefined) {
              try {
                row(i) = schema.field(i).valueOf(value.get.compactPrint)
              } catch {
                case _ : Throwable => return None
              }
          }
        }
        Some(Tuple.of(row, schema))

      case _ => None
    }
  }

  def csv(line: List[String]): Option[Tuple] = {
    if (line.length < schema.length)
      return None

    val row = new Array[AnyRef](schema.length)
    for (i <- 0 until schema.length) {
      try {
        row(i) = schema.field(i).valueOf(line(i))
      } catch {
        case _ : Throwable => return None
      }
    }

    Some(Tuple.of(row, schema))
  }

  def csv(line: Map[String, String]): Option[Tuple] = {
    if (line.size < schema.length)
      return None

    val row = new Array[AnyRef](schema.length)
    for (i <- 0 until schema.length) {
      val field = schema.field(i)
      val value = line.get(field.name)
      if (value.isEmpty) return None

      try {
        row(i) = field.valueOf(value.get)
      } catch {
        case _ : Throwable => return None
      }
    }

    Some(Tuple.of(row, schema))
  }
}
