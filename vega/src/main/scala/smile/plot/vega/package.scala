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

package smile.plot

import scala.language.implicitConversions
import smile.data._, smile.data.`type`.DataType
import smile.json._

/** Vega-lite based data visualization.
  *
  * @author Haifeng Li
  */
package object vega {
  implicit def pimpDataFrame(data: DataFrame): DataFrame2JSON = DataFrame2JSON(data)
  implicit def pimpTuple(data: Tuple): Tuple2JSON = Tuple2JSON(data)

  /** Returns the HTML of plot specification with Vega Embed. */
  def embed(spec: JsObject): String = {
    s"""
      |<!DOCTYPE html>
      |<html>
      |<head>
      |  <script src="https://cdn.jsdelivr.net/npm/vega@5.9.0"></script>
      |  <script src="https://cdn.jsdelivr.net/npm/vega-lite@4.0.2"></script>
      |  <script src="https://cdn.jsdelivr.net/npm/vega-embed@6.2.1"></script>
      |</head>
      |<body>
      |
      |<div id="vega-lite"></div>
      |
      |<script type="text/javascript">
      |  var spec = ${spec};
      |  vegaEmbed('#vega-lite', spec);
      |</script>
      |</body>
      |</html>
    """.stripMargin
  }

  /** Returns the HTML wrapped in an iframe to render in notebooks.
    * @param id the iframe HTML id.
    */
  def iframe(spec: JsObject, id: String = java.util.UUID.randomUUID.toString): String = {
    val src = xml.Utility.escape(embed(spec))
    s"""
       |  <iframe id="${id}" sandbox="allow-scripts allow-same-origin" style="border: none; width: 100%" srcdoc="${src}"></iframe>
       |  <script>
       |    (function() {
       |      function resizeIFrame(el, k) {
       |        var height = el.contentWindow.document.body.scrollHeight || '400'; // Fallback in case of no scroll height
       |        el.style.height = height + 'px';
       |        if (k <= 10) { setTimeout(function() { resizeIFrame(el, k+1) }, 1000 + (k * 250)) };
       |      }
       |      resizeIFrame(document.querySelector('#${id}'), 1);
       |    })(); // IIFE
       |  </script>
    """.stripMargin
  }

  /** Scatter plot.
    *
    * @param data a n-by-2 matrix
    * @param fields the field names.
    * @param color optional (name, nominal variable) pair for different color of dots.
    * @param shape optional (name, nominal variable) pair for different shape of dots.
    * @param sizeOrText optional (name, variable) pair for the size of dots (bubble plot) or text marks.
    * @param properties additional vega-lite specification properties.
    */
  def plot(data: Array[Array[Double]],
           fields: (String, String) = ("x", "y"),
           color: Option[(String, Either[Array[Int], Array[String]])] = None,
           shape: Option[(String, Either[Array[Int], Array[String]])] = None,
           sizeOrText: Option[(String, Either[Array[Double], Array[String]])] = None,
           properties: JsObject = JsObject()): JsObject = {
    spec(
      valuesOf(data, fields, color, shape, sizeOrText),
      mark = "point",
      x = JsObject("field" -> fields._1, "type" -> "quantitative"),
      y = JsObject("field" -> fields._2, "type" -> "quantitative"),
      color = color.map{case (name, _) => JsObject("field" -> name, "type" -> "nominal")},
      shape = shape.map{case (name, _) => JsObject("field" -> name, "type" -> "nominal")},
      size  = sizeOrText.map{case (name, _) => JsObject("field" -> name, "type" -> "quantitative")},
      text  = sizeOrText.map{case (name, _) => JsObject("field" -> name, "type" -> "nominal")}
    ) ++= properties
  }

  /** Scatter plot matrix. */
  def spm(data: DataFrame, clazz: Option[String] = None, properties: JsObject = JsObject()): JsObject = {
    val columns = clazz match {
      case None => data.names
      case Some(clazz) =>
        properties.spec =
          json"""
                |{
                |  "encoding": {
                |    "color": {
                |      "condition": {
                |        "selection": "brush",
                |        "field": "$clazz",
                |        "type": "nominal"
                |      },
                |      "value": "grey"
                |    }
                |  }
                |}
              """
        data.names.filter(name => name != clazz)
    }

    val names: JsArray = columns.map(s => JsString(s))
    val values = data.toJSON

    val spec = JsonParser(s"""
      |{
      |  "$$schema": "https://vega.github.io/schema/vega-lite/v4.json",
      |  "repeat": {
      |    "row": $names,
      |    "column": $names
      |  },
      |  "spec": {
      |    "data": {
      |      "values": $values
      |    },
      |    "mark": "point",
      |    "selection": {
      |      "brush": {
      |        "type": "interval",
      |        "resolve": "union",
      |        "on": "[mousedown[event.shiftKey], window:mouseup] > window:mousemove!",
      |        "translate": "[mousedown[event.shiftKey], window:mouseup] > window:mousemove!",
      |        "zoom": "wheel![event.shiftKey]"
      |      },
      |      "grid": {
      |        "type": "interval",
      |        "resolve": "global",
      |        "bind": "scales",
      |        "translate": "[mousedown[!event.shiftKey], window:mouseup] > window:mousemove!",
      |        "zoom": "wheel![!event.shiftKey]"
      |      }
      |    },
      |    "encoding": {
      |      "x": {"field": {"repeat": "column"}, "type": "quantitative"},
      |      "y": {
      |        "field": {"repeat": "row"},
      |        "type": "quantitative",
      |        "axis": {"minExtent": 30}
      |      }
      |    }
      |  }
      |}
    """.stripMargin)

    spec.asInstanceOf[JsObject] ++= properties
  }

  /** Line plot.
    *
    * @param data a n-by-2 matrix with variable names.
    * @param point show point mark if true.
    * @param color optional nominal name-variable pair for different color of dots.
    * @param shape optional nominal name-variable pair for different shape of dots.
    * @param properties additional vega-lite specification properties.
    */
  def line(data: Array[Array[Double]],
           fields: (String, String) = ("x", "y"),
           point: Boolean = false,
           color: Option[(String, Either[Array[Int], Array[String]])] = None,
           shape: Option[(String, Either[Array[Int], Array[String]])] = None,
           properties: JsObject = JsObject()): JsObject = {
    spec(
      valuesOf(data, fields, color, shape, None),
      mark = if (point) JsObject("type" -> "line", "point" -> true) else "line",
      x = JsObject("field" -> fields._1, "type" -> "quantitative"),
      y = JsObject("field" -> fields._2, "type" -> "quantitative"),
      color = color.map{case (name, _) => JsObject("field" -> name, "type" -> "nominal")},
      shape = shape.map{case (name, _) => JsObject("field" -> name, "type" -> "nominal")}
    ) ++= properties
  }

  /** Box plot.
    * @param data an array.
    * @param group the group of each data element.
    * @param properties additional vega-lite specification properties.
    */
  def boxplot(data: (String, Array[Double]), group: (String, Array[String]), properties: JsObject = JsObject()): JsObject = {
    spec(
      valuesOf(data, group),
      mark = JsObject("type" -> "boxplot", "extent" -> 1.5),
      x = JsObject("field" -> group._1, "type" -> "ordinal"),
      y = JsObject("field" -> data._1, "type" -> "quantitative")
    ) ++= properties
  }

  /** Bar chart.
    * @param data an array.
    */
  def bar(data: Array[Double]): JsObject = {
    bar(("y", data), ("x", data.map(i => i.toString)))
  }

  /** Bar chart.
    * @param data an array.
    * @param group the group of each data element.
    * @param properties additional vega-lite specification properties.
    */
  def bar(data: (String, Array[Double]), group: (String, Array[String]), properties: JsObject = JsObject()): JsObject = {
    spec(
      valuesOf(data, group),
      mark = "bar",
      x = JsObject("field" -> group._1, "type" -> "ordinal"),
      y = JsObject("field" -> data._1, "type" -> "quantitative")
    ) ++= properties
  }

  /** Histogram plot. */
  def hist(x: (String, Array[Double]), k: Int, properties: JsObject = JsObject()): JsObject = {
    spec(valuesOf(x._1, x._2), "bar",
      x = JsObject("bin" -> JsObject("maxbins" -> JsInt(k)), "field" -> x._1, "type" -> "quantitative"),
      y = JsObject("aggregate" -> "count", "type" -> "quantitative")
    ) ++= properties
  }

  /** Returns a JsObject with the field 'values' of an array of data records. */
  private[vega] def valuesOf(data: Array[Array[Double]],
                             fields: (String, String) = ("x", "y"),
                             color: Option[(String, Either[Array[Int], Array[String]])],
                             shape: Option[(String, Either[Array[Int], Array[String]])],
                             sizeOrText: Option[(String, Either[Array[Double], Array[String]])]): JsObject = {
    val (x, y) = fields
    val values: JsArray = (0 until data.length).map { i =>
      val row = data(i)
      val obj = JsObject(x -> row(0), y -> row(1))
      color map { case (name, color) => obj(name) = color.fold(a => JsInt(a(i)), a => JsString(a(i)))}
      shape map { case (name, shape) => obj(name) = shape.fold(a => JsInt(a(i)), a => JsString(a(i)))}
      sizeOrText map { case (name, sizeOrText)  => obj(name)  = sizeOrText.fold(a => JsDouble(a(i)), a => JsString(a(i)))}
      obj
    }
    JsObject("values" -> values)
  }

  /** Returns a JsObject with the field 'values' of an array of data records. */
  private[vega] def valuesOf(field: String, x: Array[Double]): JsObject = {
    val values: JsArray = (0 until x.length).map(i => JsObject(field -> JsDouble(x(i))))
    JsObject("values" -> values)
  }

  /** Returns a JsObject with the field 'values' of an array of data records. */
  private[vega] def valuesOf(data: (String, Array[Double]), group: (String, Array[String])): JsObject = {
    val x = group._2
    val y = data._2
    val values: JsArray = (0 until x.length).map(i => JsObject(group._1 -> x(i), data._1 -> y(i)))
    JsObject("values" -> values)
  }

  /** Returns the plot specification. */
  private[vega] def spec(data: JsObject,
                         mark: JsValue,
                         x: JsObject = JsObject("field" -> "x", "type" -> "quantitative"),
                         y: JsObject = JsObject("field" -> "y", "type" -> "quantitative"),
                         color: Option[JsObject] = None,
                         shape: Option[JsObject] = None,
                         size: Option[JsObject] = None,
                         text: Option[JsObject] = None
                        ): JsObject = {
    val spec = JsObject(
      "$schema" -> "https://vega.github.io/schema/vega-lite/v4.0.2.json",
      "data" -> data,
      "mark" -> mark,
      "encoding" -> JsObject(
        "x" -> x,
        "y" -> y
      )
    )

    color match {
      case Some(color) => spec.encoding.color = color
      case None => ()
    }

    shape match {
      case Some(shape) => spec.encoding.shape = shape
      case None => ()
    }

    size match {
      case Some(size) => spec.encoding.size = size
      case None => ()
    }

    text match {
      case Some(text) => spec.encoding.text = text
      case None => ()
    }

    spec
  }
}

package vega {

  import smile.data.measure.DiscreteMeasure

  private[vega] case class DataFrame2JSON(data: DataFrame) {
    def toJSON: JsArray = {
      JsArray((0 until data.nrows).map { i => Tuple2JSON(data(i)).toJSON }: _*)
    }
  }

  private[vega] case class Tuple2JSON(data: Tuple) {
    def toJSON: JsObject = {
      val schema = data.schema
      JsObject((0 until data.length).map(i => schema.fieldName(i) -> get(i)): _*)
    }

    private def get(i: Int): JsValue = {
      val schema = data.schema
      val field = schema.field(i)
      if (field.measure.isInstanceOf[DiscreteMeasure]) {
        JsString(data.getString(i))
      } else {
        field.`type`.id match {
          case DataType.ID.Boolean => JsBoolean(data.getBoolean(i))
          case DataType.ID.Byte => JsInt(data.getByte(i))
          case DataType.ID.Short => JsInt(data.getShort(i))
          case DataType.ID.Integer => JsInt(data.getInt(i))
          case DataType.ID.Long => JsLong(data.getLong(i))
          case DataType.ID.Float => JsDouble(data.getFloat(i))
          case DataType.ID.Double => JsDouble(data.getDouble(i))
          case _ => JsString(data.getString(i))
        }
      }
    }
  }
}