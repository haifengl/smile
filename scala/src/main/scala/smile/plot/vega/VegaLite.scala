/*******************************************************************************
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 ******************************************************************************/

package smile.plot.vega

import smile.data._
import smile.json._

/** Vega-lite specification. */
trait VegaLite {
  /** The specification */
  val spec: JsObject

  override def toString: String = spec.prettyPrint

  /** The MIME type */
  val mime: String = "application/vnd.vegalite.v4+json"

  /** Returns the HTML of plot specification with Vega Embed. */
  def embed: String = {
    s"""
       |<!DOCTYPE html>
       |<html>
       |<head>
       |  <script src="https://cdn.jsdelivr.net/npm/vega@5"></script>
       |  <script src="https://cdn.jsdelivr.net/npm/vega-lite@4"></script>
       |  <script src="https://cdn.jsdelivr.net/npm/vega-embed@6"></script>
       |</head>
       |<body>
       |
       |<div id="vega-lite" style="width: 100%; height: 100vh"></div>
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
  def iframe(id: String = java.util.UUID.randomUUID.toString): String = {
    val src = xml.Utility.escape(embed)
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
}

/** Single view plot. */
case class View(override val spec: JsObject) extends VegaLite {
  /** Adds a descriptive title to a chart. */
  def title(title: String): View = {
    spec.title = title
    this
  }

  /** Sets the width of the data rectangle (plotting) dimensions. */
  def width(width: Int): View = {
    spec.width = width
    this
  }

  /** Sets the height of the data rectangle (plotting) dimensions. */
  def height(height: Int): View = {
    spec.height = height
    this
  }

  /** Sets the top-level width properties to "container" to indicate
    * that the width of the plot should be the same as its surrounding
    * container. The width and height can be set independently,
    * for example, you can have a responsive width and a fixed height
    * by setting width to "container" and height to a number.
    *
    * After setting width or height to "container", you need to ensure
    * that the container’s width or height is determined outside the plot.
    * For example, the container can be a `<div>` element that has style
    * width: 100%; height: 300px. When the container is not available
    * or its size is not defined (e.g., in server-side rendering),
    * the default width and height are config.view.continuousWidth
    * and config.view.continuousHeight, respectively.
    */
  def width(width: String): View = {
    spec.width = "container"
    this
  }

  /** Sets the top-level height properties to "container" to indicate
    * that the height of the plot should be the same as its surrounding
    * container. The width and height can be set independently,
    * for example, you can have a responsive width and a fixed height
    * by setting width to "container" and height to a number.
    *
    * After setting width or height to "container", you need to ensure
    * that the container’s width or height is determined outside the plot.
    * For example, the container can be a `<div>` element that has style
    * width: 100%; height: 300px. When the container is not available
    * or its size is not defined (e.g., in server-side rendering),
    * the default width and height are config.view.continuousWidth
    * and config.view.continuousHeight, respectively.
    */
  def height(height: String): View = {
    spec.height = "container"
    this
  }

  /** For a discrete x-field, sets the width per discrete step. */
  def widthStep(step: Int): View = {
    spec.width = JsObject("step" -> JsInt(step))
    this
  }

  /** For a discrete y-field, sets the height per discrete step.. */
  def heightStep(step: Int): View = {
    spec.height = JsObject("step" -> JsInt(step))
    this
  }

  /** Sets the overall size of the visualization. The total size of
    * a Vega-Lite visualization may be determined by multiple factors:
    * specified width, height, and padding values, as well as content
    * such as axes, legends, and titles.
    *
    * @param `type` The sizing format type. One of "pad", "fit", "fit-x",
    *               "fit-y", or "none". See the [[https://vega.github.io/vega-lite/docs/size.html#autosize autosize type]]
    *               documentation for descriptions of each.
    * @param resize A boolean flag indicating if autosize layout should
    *               be re-calculated on every view update.
    * @param contains Determines how size calculation should be performed,
    *                 one of "content" or "padding". The default setting
    *                 ("content") interprets the width and height settings
    *                 as the data rectangle (plotting) dimensions, to which
    *                 padding is then added. In contrast, the "padding"
    *                 setting includes the padding within the view size
    *                 calculations, such that the width and height settings
    *                 indicate the total intended size of the view.
    */
  def autosize(`type`: String = "pad", resize: Boolean = false, contains: String = "content"): View = {
    spec.autosize = JsObject(
      "type" -> `type`,
      "resize" -> resize,
      "contains" -> contains
    )
    this
  }

  /** Sets the mark definition object.
    * Marks are the basic visual building block of a visualization.
    * They provide basic shapes whose properties (such as position,
    * size, and color) can be used to visually encode data, either
    * from a data field, or a constant value.
    *
    * The mark property of a single view specification can either
    * be a string describing a mark type or a mark
    * definition object.
    *
    * @param mark
    */
  def mark(mark: JsObject): View = {
    spec.mark = mark
    this
  }

  /** Sets the mark as area. */
  def area: View = {
    spec.mark = "area"
    this
  }

  /** Sets the mark as bar. */
  def bar: View = {
    spec.mark = "bar"
    this
  }

  /** Sets the mark as circle. */
  def circle: View = {
    spec.mark = "circle"
    this
  }

  /** Sets the mark as line. */
  def line: View = {
    spec.mark = "line"
    this
  }

  /** Sets the mark as point. */
  def point: View = {
    spec.mark = "point"
    this
  }

  /** Sets the mark as rect. */
  def rect: View = {
    spec.mark = "rect"
    this
  }

  /** Sets the mark as rule. */
  def rule: View = {
    spec.mark = "rule"
    this
  }

  /** Sets the mark as square. */
  def square: View = {
    spec.mark = "square"
    this
  }

  /** Sets the mark as text. */
  def text: View = {
    spec.mark = "text"
    this
  }

  /** Sets the mark as tick. */
  def tick: View = {
    spec.mark = "tick"
    this
  }

  /** Sets the mark as geoshape. */
  def geoshape: View = {
    spec.mark = "geoshape"
    this
  }

  /** Sets the x field. */
  def x(field: String,
        `type`: String = "quantitative",
        bin: Boolean = false,
        timeUnit: String = "",
        aggregate: String = "",
        title: String = "",
        scale: JsValue = JsUndefined,
        axis: JsValue = JsUndefined,
        sort: Option[String] = None,
        band: Option[Double] = None,
        impute: JsValue = JsUndefined,
        stack: JsValue = JsUndefined): View = {
    if (!spec.fields.contains("encoding")) spec.encoding = JsObject()
    spec.encoding.x = VegaLite.positionField(field, `type`, bin, timeUnit,
      aggregate, title, scale, axis, sort, band, impute, stack)
    this
  }

  /** Sets the y field. */
  def y(field: String,
        `type`: String = "quantitative",
        bin: Boolean = false,
        timeUnit: String = "",
        aggregate: String = "",
        title: String = "",
        scale: JsValue = JsUndefined,
        axis: JsValue = JsUndefined,
        sort: Option[String] = None,
        band: Option[Double] = None,
        impute: JsValue = JsUndefined,
        stack: JsValue = JsUndefined): View = {
    if (!spec.fields.contains("encoding")) spec.encoding = JsObject()
    spec.encoding.y = VegaLite.positionField(field, `type`, bin, timeUnit,
      aggregate, title, scale, axis, sort, band, impute, stack)
    this
  }

  /** Sets the x2 field. */
  def x2(field: String,
         bin: Boolean = false,
         timeUnit: String = "",
         aggregate: String = "",
         title: String = ""): View = {
    if (!spec.fields.contains("encoding")) spec.encoding = JsObject()
    spec.encoding.x2 = VegaLite.field(field, null, bin, timeUnit, aggregate, title)
    this
  }

  /** Sets the y2 field. */
  def y2(field: String,
         bin: Boolean = false,
         timeUnit: String = "",
         aggregate: String = "",
         title: String = ""): View = {
    if (!spec.fields.contains("encoding")) spec.encoding = JsObject()
    spec.encoding.y2 = VegaLite.field(field, null, bin, timeUnit, aggregate, title)
    this
  }

  /** Sets the xError field. */
  def xError(field: String,
        bin: Boolean = false,
        timeUnit: String = "",
        aggregate: String = "",
        title: String = ""): View = {
    if (!spec.fields.contains("encoding")) spec.encoding = JsObject()
    spec.encoding.x = VegaLite.field(field, null, bin, timeUnit, aggregate, title)
    this
  }

  /** Sets the yError field. */
  def yError(field: String,
        bin: Boolean = false,
        timeUnit: String = "",
        aggregate: String = "",
        title: String = ""): View = {
    if (!spec.fields.contains("encoding")) spec.encoding = JsObject()
    spec.encoding.y = VegaLite.field(field, null, bin, timeUnit, aggregate, title)
    this
  }

  /** Sets the xError2 field. */
  def xError2(field: String,
         bin: Boolean = false,
         timeUnit: String = "",
         aggregate: String = "",
         title: String = ""): View = {
    if (!spec.fields.contains("encoding")) spec.encoding = JsObject()
    spec.encoding.xError2 = VegaLite.field(field, null, bin, timeUnit, aggregate, title)
    this
  }

  /** Sets the yError2 field. */
  def yError2(field: String,
         bin: Boolean = false,
         timeUnit: String = "",
         aggregate: String = "",
         title: String = ""): View = {
    if (!spec.fields.contains("encoding")) spec.encoding = JsObject()
    spec.encoding.yError2 = VegaLite.field(field, null, bin, timeUnit, aggregate, title)
    this
  }

  /** Sets the longitude field. */
  def longitude(field: String,
         `type`: String = "quantitative",
         bin: Boolean = false,
         timeUnit: String = "",
         aggregate: String = "",
         title: String = ""): View = {
    if (!spec.fields.contains("encoding")) spec.encoding = JsObject()
    spec.encoding.longitude = VegaLite.field(field, `type`, bin, timeUnit, aggregate, title)
    this
  }

  /** Sets the latitude field. */
  def latitude(field: String,
         `type`: String = "quantitative",
         bin: Boolean = false,
         timeUnit: String = "",
         aggregate: String = "",
         title: String = ""): View = {
    if (!spec.fields.contains("encoding")) spec.encoding = JsObject()
    spec.encoding.latitude = VegaLite.field(field, `type`, bin, timeUnit, aggregate, title)
    this
  }

  /** Sets the longitude2 field. */
  def longitude2(field: String,
                bin: Boolean = false,
                timeUnit: String = "",
                aggregate: String = "",
                title: String = ""): View = {
    if (!spec.fields.contains("encoding")) spec.encoding = JsObject()
    spec.encoding.longitude2 = VegaLite.field(field, null, bin, timeUnit, aggregate, title)
    this
  }

  /** Sets the latitude2 field. */
  def latitude2(field: String,
               bin: Boolean = false,
               timeUnit: String = "",
               aggregate: String = "",
               title: String = ""): View = {
    if (!spec.fields.contains("encoding")) spec.encoding = JsObject()
    spec.encoding.latitude2 = VegaLite.field(field, null, bin, timeUnit, aggregate, title)
    this
  }

  /** Sets the color property. */
  def color(field: String,
            `type`: String = "nominal",
            bin: Boolean = false,
            timeUnit: String = "",
            aggregate: String = "",
            title: String = ""): View = {
    if (!spec.fields.contains("encoding")) spec.encoding = JsObject()
    spec.encoding.color = VegaLite.field(field, `type`, bin, timeUnit, aggregate, title)
    this
  }
/*
  /** Sets the color value. */
  def color(value: String): View = {
    if (!spec.fields.contains("encoding")) spec.encoding = JsObject()
    spec.encoding.color = JsObject("value" -> JsString(value))
    this
  }
*/
  /** Sets the shape property. */
  def shape(field: String,
            `type`: String = "nominal",
            bin: Boolean = false,
            timeUnit: String = "",
            aggregate: String = "",
            title: String = ""): View = {
    if (!spec.fields.contains("encoding")) spec.encoding = JsObject()
    spec.encoding.shape = VegaLite.field(field, `type`, bin, timeUnit, aggregate, title)
    this
  }
/*
  /** Sets the shape value. */
  def shape(value: String): View = {
    if (!spec.fields.contains("encoding")) spec.encoding = JsObject()
    spec.encoding.shape = JsObject("value" -> JsString(value))
    this
  }
*/
  /** Sets the channels x, y with default properties. */
  def encode(x: String, y: String, color: String = ""): View = {
    this.x(x)
    this.y(y)
    this.color(color)
    this
  }
}

object VegaLite {
  /** The schema of vega-lite. */
  val schema = "https://vega.github.io/schema/vega-lite/v4.json"

  /** Creates a single view vega-lite specification with inline data. */
  def apply(data: DataFrame): View = {
    val spec = JsObject(
      "$schema" -> schema,
      "data" -> JsObject(
        "values" -> data.toJSON
      )
    )

    View(spec)
  }

  /** Creates a single view vega-lite specification with data from from URL.
    *
    * @param url An URL from which to load the data set.
    * @param format Type of input data: "json", "csv", "tsv", "dsv".
    *               Default value: The default format type is determined
    *               by the extension of the file URL. If no extension is
    *               detected, "json" will be used by default.
    */
  def apply(url: String, format: String = ""): View = {
    val spec = JsObject(
      "$schema" -> schema,
      "data" -> JsObject(
        "url" -> JsString(url)
      )
    )

    if (!format.isEmpty) {
      spec.data.format = JsObject("type" -> JsString(format))
    }

    View(spec)
  }

  /** Returns a field definition.
    * To encode a particular field in the data set with an encoding channel,
    * the channel’s field definition must describe the field name and its
    * data type.
    *
    * @param field A string defining the name of the field from which to
    *              pull a data value or an object defining iterated values
    *              from the repeat operator.
    * @param `type` The encoded field’s type of measurement ("quantitative",
    *               "temporal", "ordinal", or "nominal"). It can also be a
    *               "geojson" type for encoding ‘geoshape’.
    *
    *               Data type describes the semantics of the data rather than
    *               the primitive data types (number, string, etc.). The same
    *               primitive data type can have different types of
    *               measurement. For example, numeric data can represent
    *               quantitative, ordinal, or nominal data.
    *
    *               Data values for a temporal field can be either a
    *               date-time string (e.g., "2015-03-07 12:32:17",
    *               "17:01", "2015-03-16", "2015") or a timestamp
    *               number (e.g., 1552199579097).
    *
    *               Secondary channels (e.g., x2, y2, xError, yError) do not
    *               have type as they have exactly the same type as their
    *               primary channels (e.g., x, y)
    * @param bin A flag for binning a quantitative field.
    * @param timeUnit  Time unit (e.g., year, yearmonth, month, hours) for a
    *                  temporal field, or a temporal field that gets casted
    *                  as ordinal.
    * @param aggregate Aggregation function for the field (e.g., "mean",
    *                  "sum", "median", "min", "max", "count").
    * @param title     A title for the field. If null, the title will be
    *                  removed. The default value is derived from the
    *                  field’s name and transformation function (aggregate,
    *                  bin and timeUnit).
    */
  def field(field: String,
            `type`: String = "quantitative",
            bin: Boolean = false,
            timeUnit: String = "",
            aggregate: String = "",
            title: String = ""): JsObject = {
    val json = JsObject("field" -> JsString(field))

    if (`type` != null) json("type") = `type`
    if (bin && `type` == "quantitative") json.bin = bin
    if (!timeUnit.isEmpty) json.timeUnit = timeUnit
    if (!aggregate.isEmpty) json.aggregate = aggregate
    if (title == null) json.title = JsNull
    else if (!title.isEmpty) json.title = title

    json
  }

  /** Returns a position field definition.
    * x and y position channels determine the position of the marks,
    * or width/height of horizontal/vertical "area" and "bar".
    * In addition, x2 and y2 can specify the span of ranged area, bar,
    * rect, and rule.
    *
    * @param field A string defining the name of the field from which to
    *              pull a data value or an object defining iterated values
    *              from the repeat operator.
    * @param `type` The encoded field’s type of measurement ("quantitative",
    *               "temporal", "ordinal", or "nominal"). It can also be a
    *               "geojson" type for encoding ‘geoshape’.
    *
    *               Data type describes the semantics of the data rather than
    *               the primitive data types (number, string, etc.). The same
    *               primitive data type can have different types of
    *               measurement. For example, numeric data can represent
    *               quantitative, ordinal, or nominal data.
    *
    *               Data values for a temporal field can be either a
    *               date-time string (e.g., "2015-03-07 12:32:17",
    *               "17:01", "2015-03-16", "2015") or a timestamp
    *               number (e.g., 1552199579097).
    * @param bin A flag for binning a quantitative field.
    * @param timeUnit  Time unit (e.g., year, yearmonth, month, hours) for a
    *                  temporal field, or a temporal field that gets casted
    *                  as ordinal.
    * @param aggregate Aggregation function for the field (e.g., "mean",
    *                  "sum", "median", "min", "max", "count").
    * @param title     A title for the field. If null, the title will be
    *                  removed. The default value is derived from the
    *                  field’s name and transformation function (aggregate,
    *                  bin and timeUnit).
    * @param scale An object defining properties of the channel’s scale,
    *              which is the function that transforms values in the data
    *              domain (numbers, dates, strings, etc) to visual values
    *              (pixels, colors, sizes) of the encoding channels.
    *
    *              If null, the scale will be disabled and the data value
    *              will be directly encoded.
    *
    *              If undefined, default scale properties are applied.
    * @param axis  An object defining properties of axis’s gridlines, ticks
    *              and labels.
    *
    *              If null, the axis for the encoding channel will be
    *              removed.
    *
    *              If undefined, default axis properties are applied.
    * @param sort  Sort order for the encoded field.
    *
    *              For continuous fields (quantitative or temporal), sort
    *              can be either "ascending" or "descending".
    *
    *              For discrete fields, sort can be one of the following:
    *              - "ascending" or "descending" – for sorting by the values’
    *              natural order in JavaScript.
    *              - A string indicating an encoding channel name to sort
    *              by (e.g., "x" or "y") with an optional minus prefix for
    *              descending sort (e.g., "-x" to sort by x-field, descending).
    *              This channel string is short-form of a sort-by-encoding
    *              definition. For example, "sort": "-x" is equivalent to
    *              "sort": {"encoding": "x", "order": "descending"}.
    * @param band  For rect-based marks (rect, bar, and image), mark size
    *              relative to bandwidth of band scales or time units.
    *              If set to 1, the mark size is set to the bandwidth
    *              or the time unit interval. If set to 0.5, the mark
    *              size is half of the bandwidth or the time unit interval.
    *
    *              For other marks, relative position on a band of a stacked,
    *              binned, time unit or band scale. If set to 0, the marks
    *              will be positioned at the beginning of the band. If set
    *              to 0.5, the marks will be positioned in the middle of
    *              the band.
    * @param impute An object defining the properties of the Impute Operation
    *               to be applied. The field value of the other positional
    *               channel is taken as key of the Impute Operation. The
    *               field of the color channel if specified is used as
    *               groupby of the Impute Operation.
    * @param stack  Type of stacking offset if the field should be stacked.
    *               stack is only applicable for x, y, theta, and radius
    *               channels with continuous domains. For example, stack
    *               of y can be used to customize stacking for a vertical
    *               bar chart.
    *
    *               stack can be one of the following values:
    *
    *               - "zero" or true: stacking with baseline offset at zero
    *                 value of the scale (for creating typical stacked bar
    *                 and area chart).
    *               - "normalize": stacking with normalized domain (for
    *                 creating normalized stacked bar and area charts.
    *               - "center": stacking with center baseline
    *                 (for streamgraph).
    *               - null or false: No-stacking. This will produce layered
    *               bar and area chart.
    */
  def positionField(field: String,
                    `type`: String = "quantitative",
                    bin: Boolean = false,
                    timeUnit: String = "",
                    aggregate: String = "",
                    title: String = "",
                    scale: JsValue = JsUndefined,
                    axis: JsValue = JsUndefined,
                    sort: Option[String] = None,
                    band: Option[Double] = None,
                    impute: JsValue = JsUndefined,
                    stack: JsValue = JsUndefined): JsObject = {
    val json = this.field(field, `type`, bin, timeUnit, aggregate, title)

    if (scale != JsUndefined) json.scale = scale
    if (axis != JsUndefined) json.axis = axis
    if (sort.isDefined) json.sort = sort.get
    if (band.isDefined) json.band = band.get
    if (impute != JsUndefined) json.impute = impute
    if (stack != JsUndefined) json.stack = stack

    json
  }
}