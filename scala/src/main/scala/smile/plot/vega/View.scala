/*
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
  */

package smile.plot.vega

import smile.json._

/** Single view specification, which describes a view that uses a single
  * mark type to visualize the data.
  */
trait View extends VegaLite {
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
    assert(width == "container", "Invalid width: " + width)
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
    assert(height == "container", "Invalid height: " + height)
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

  /** Sets the mark definition object.
    * Marks are the basic visual building block of a visualization.
    * They provide basic shapes whose properties (such as position,
    * size, and color) can be used to visually encode data, either
    * from a data field, or a constant value.
    */
  def mark(mark: JsObject): View = {
    spec.mark = mark
    this
  }

  /** Sets the mark property of a string (one of "bar", "circle",
    * "square", "tick", "line", "area", "point", "rule", "geoshape",
    * and "text"). */
  def mark(mark: String): View = {
    spec.mark = mark
    this
  }

  /** Sets the channels x and y (quantitative),
    * and optional color with default properties.
    */
  def encode(x: String, y: String, color: Option[(String, String)] = None): View = {
    this.x(x, "quantitative")
    this.y(y, "quantitative")
    color match {
      case Some((field, t)) => this.color(field, t)
      case None => ()
    }
    this
  }

  /** Sets the x channel as a datum field. */
  def x(datum: JsValue): View = {
    setPropertyDatum("x", datum)
    this
  }

  /** Sets the y channel as a datum field. */
  def y(datum: JsValue): View = {
    setPropertyDatum("y", datum)
    this
  }

  /** Sets the x field. */
  def x(field: JsValue,
        `type`: String,
        bin: Either[Boolean, JsObject] = Left(false),
        timeUnit: String = "",
        aggregate: String = "",
        title: String = "",
        scale: JsValue = JsUndefined,
        axis: JsValue = JsUndefined,
        sort: Option[String] = None,
        band: Option[Double] = None,
        impute: JsValue = JsUndefined,
        stack: JsValue = JsUndefined): View = {
    if (!spec.contains("encoding")) spec.encoding = JsObject()
    spec.encoding.x = View.positionField(field, `type`, bin, timeUnit,
      aggregate, title, scale, axis, sort, band, impute, stack)
    this
  }

  /** Sets the y field. */
  def y(field: JsValue,
        `type`: String,
        bin: Either[Boolean, JsObject] = Left(false),
        timeUnit: String = "",
        aggregate: String = "",
        title: String = "",
        scale: JsValue = JsUndefined,
        axis: JsValue = JsUndefined,
        sort: Option[String] = None,
        band: Option[Double] = None,
        impute: JsValue = JsUndefined,
        stack: JsValue = JsUndefined): View = {
    if (!spec.contains("encoding")) spec.encoding = JsObject()
    spec.encoding.y = View.positionField(field, `type`, bin, timeUnit,
      aggregate, title, scale, axis, sort, band, impute, stack)
    this
  }

  /** Sets the x2 field. */
  def x2(field: JsValue,
         bin: Either[Boolean, JsObject] = Left(false),
         timeUnit: String = "",
         aggregate: String = "",
         title: String = ""): View = {
    if (!spec.contains("encoding")) spec.encoding = JsObject()
    spec.encoding.x2 = View.field(field, null, bin, timeUnit, aggregate, title)
    this
  }

  /** Sets the y2 field. */
  def y2(field: JsValue,
         bin: Either[Boolean, JsObject] = Left(false),
         timeUnit: String = "",
         aggregate: String = "",
         title: String = ""): View = {
    if (!spec.contains("encoding")) spec.encoding = JsObject()
    spec.encoding.y2 = View.field(field, null, bin, timeUnit, aggregate, title)
    this
  }

  /** Sets the xError field. */
  def xError(field: JsValue,
             bin: Either[Boolean, JsObject] = Left(false),
             timeUnit: String = "",
             aggregate: String = "",
             title: String = ""): View = {
    if (!spec.contains("encoding")) spec.encoding = JsObject()
    spec.encoding.x = View.field(field, null, bin, timeUnit, aggregate, title)
    this
  }

  /** Sets the yError field. */
  def yError(field: JsValue,
             bin: Either[Boolean, JsObject] = Left(false),
             timeUnit: String = "",
             aggregate: String = "",
             title: String = ""): View = {
    if (!spec.contains("encoding")) spec.encoding = JsObject()
    spec.encoding.y = View.field(field, null, bin, timeUnit, aggregate, title)
    this
  }

  /** Sets the xError2 field. */
  def xError2(field: JsValue,
              bin: Either[Boolean, JsObject] = Left(false),
              timeUnit: String = "",
              aggregate: String = "",
              title: String = ""): View = {
    if (!spec.contains("encoding")) spec.encoding = JsObject()
    spec.encoding.xError2 = View.field(field, null, bin, timeUnit, aggregate, title)
    this
  }

  /** Sets the yError2 field. */
  def yError2(field: JsValue,
              bin: Either[Boolean, JsObject] = Left(false),
              timeUnit: String = "",
              aggregate: String = "",
              title: String = ""): View = {
    if (!spec.contains("encoding")) spec.encoding = JsObject()
    spec.encoding.yError2 = View.field(field, null, bin, timeUnit, aggregate, title)
    this
  }

  /** Sets the longitude field. */
  def longitude(field: JsValue,
                `type`: String = "quantitative",
                bin: Either[Boolean, JsObject] = Left(false),
                timeUnit: String = "",
                aggregate: String = "",
                title: String = ""): View = {
    if (!spec.contains("encoding")) spec.encoding = JsObject()
    spec.encoding.longitude = View.field(field, `type`, bin, timeUnit, aggregate, title)
    this
  }

  /** Sets the latitude field. */
  def latitude(field: JsValue,
               `type`: String = "quantitative",
               bin: Either[Boolean, JsObject] = Left(false),
               timeUnit: String = "",
               aggregate: String = "",
               title: String = ""): View = {
    if (!spec.contains("encoding")) spec.encoding = JsObject()
    spec.encoding.latitude = View.field(field, `type`, bin, timeUnit, aggregate, title)
    this
  }

  /** Sets the longitude2 field. */
  def longitude2(field: JsValue,
                 bin: Either[Boolean, JsObject] = Left(false),
                 timeUnit: String = "",
                 aggregate: String = "",
                 title: String = ""): View = {
    if (!spec.contains("encoding")) spec.encoding = JsObject()
    spec.encoding.longitude2 = View.field(field, null, bin, timeUnit, aggregate, title)
    this
  }

  /** Sets the latitude2 field. */
  def latitude2(field: JsValue,
                bin: Either[Boolean, JsObject] = Left(false),
                timeUnit: String = "",
                aggregate: String = "",
                title: String = ""): View = {
    if (!spec.contains("encoding")) spec.encoding = JsObject()
    spec.encoding.latitude2 = View.field(field, null, bin, timeUnit, aggregate, title)
    this
  }

  /** Sets the theta field. */
  def theta(field: JsValue,
            `type`: String = "quantitative",
            bin: Either[Boolean, JsObject] = Left(false),
            timeUnit: String = "",
            aggregate: String = "",
            title: String = "",
            scale: JsValue = JsUndefined,
            sort: Option[String] = None,
            stack: JsValue = JsUndefined): View = {
    if (!spec.contains("encoding")) spec.encoding = JsObject()
    spec.encoding.theta = View.polarField(field, `type`, bin, timeUnit, aggregate, title, scale, sort, stack)
    this
  }

  /** Sets the radius field. */
  def radius(field: JsValue,
             `type`: String = "quantitative",
             bin: Either[Boolean, JsObject] = Left(false),
             timeUnit: String = "",
             aggregate: String = "",
             title: String = "",
             scale: JsValue = JsUndefined,
             sort: Option[String] = None,
             stack: JsValue = JsUndefined): View = {
    if (!spec.contains("encoding")) spec.encoding = JsObject()
    spec.encoding.radius = View.polarField(field, `type`, bin, timeUnit, aggregate, title, scale, sort, stack)
    this
  }

  /** Sets the theta2 field. */
  def theta2(field: JsValue,
             bin: Either[Boolean, JsObject] = Left(false),
             timeUnit: String = "",
             aggregate: String = "",
             title: String = ""): View = {
    if (!spec.contains("encoding")) spec.encoding = JsObject()
    spec.encoding.theta2 = View.field(field, null, bin, timeUnit, aggregate, title)
    this
  }

  /** Sets the radius2 field. */
  def radius2(field: JsValue,
              bin: Either[Boolean, JsObject] = Left(false),
              timeUnit: String = "",
              aggregate: String = "",
              title: String = ""): View = {
    if (!spec.contains("encoding")) spec.encoding = JsObject()
    spec.encoding.radius2 = View.field(field, null, bin, timeUnit, aggregate, title)
    this
  }

  /** Sets a mark property. */
  def setProperty(prop: String,
          field: JsValue,
          `type`: String,
          bin: Either[Boolean, JsObject] = Left(false),
          timeUnit: String = "",
          aggregate: String = "",
          scale: JsValue = JsUndefined,
          legend: JsValue = JsUndefined,
          condition: JsValue = JsUndefined): View = {
    if (!spec.contains("encoding")) spec.encoding = JsObject()
    spec.encoding(prop) = View.markPropField(field, `type`, bin, timeUnit, aggregate, scale, legend, condition)
    this
  }

  /** Sets a mark property by value. */
  def setPropertyValue(prop: String, value: JsValue): View = {
    if (!spec.contains("encoding")) spec.encoding = JsObject()
    spec.encoding(prop) = JsObject("value" -> value)
    this
  }

  /** Sets a mark property by datum. */
  def setPropertyDatum(prop: String, datum: JsValue): View = {
    if (!spec.contains("encoding")) spec.encoding = JsObject()
    spec.encoding(prop) = JsObject("datum" -> datum)
    this
  }

  /** Sets the color property. */
  def color(field: JsValue,
            `type`: String,
            bin: Either[Boolean, JsObject] = Left(false),
            timeUnit: String = "",
            aggregate: String = "",
            scale: JsValue = JsUndefined,
            legend: JsValue = JsUndefined,
            condition: JsValue = JsUndefined): View = {
    setProperty("color", field, `type`, bin, timeUnit, aggregate, scale, legend, condition)
  }

  /** Sets the color value. */
  def color(value: JsValue): View = {
    setPropertyValue("color", value)
  }

  /** Sets the angle property. */
  def angle(field: JsValue,
            `type`: String,
            bin: Either[Boolean, JsObject] = Left(false),
            timeUnit: String = "",
            aggregate: String = "",
            scale: JsValue = JsUndefined,
            legend: JsValue = JsUndefined,
            condition: JsValue = JsUndefined): View = {
    setProperty("angle", field, `type`, bin, timeUnit, aggregate, scale, legend, condition)
  }

  /** Sets the angle value. */
  def angle(value: JsValue): View = {
    setPropertyValue("angle", value)
  }

  /** Sets the fill property. */
  def fill(field: JsValue,
           `type`: String,
           bin: Either[Boolean, JsObject] = Left(false),
           timeUnit: String = "",
           aggregate: String = "",
           scale: JsValue = JsUndefined,
           legend: JsValue = JsUndefined,
           condition: JsValue = JsUndefined): View = {
    setProperty("fill", field, `type`, bin, timeUnit, aggregate, scale, legend, condition)
  }

  /** Sets the fill value. */
  def fill(value: JsValue): View = {
    setPropertyValue("fill", value)
  }

  /** Sets the stroke property. */
  def stroke(field: JsValue,
             `type`: String,
             bin: Either[Boolean, JsObject] = Left(false),
             timeUnit: String = "",
             aggregate: String = "",
             scale: JsValue = JsUndefined,
             legend: JsValue = JsUndefined,
             condition: JsValue = JsUndefined): View = {
    setProperty("stroke", field, `type`, bin, timeUnit, aggregate, scale, legend, condition)
  }

  /** Sets the stroke value. */
  def stroke(value: JsValue): View = {
    setPropertyValue("stroke", value)
  }

  /** Sets the shape property. */
  def shape(field: JsValue,
            `type`: String,
            bin: Either[Boolean, JsObject] = Left(false),
            timeUnit: String = "",
            aggregate: String = "",
            scale: JsValue = JsUndefined,
            legend: JsValue = JsUndefined,
            condition: JsValue = JsUndefined): View = {
    setProperty("shape", field, `type`, bin, timeUnit, aggregate, scale, legend, condition)
  }

  /** Sets the shape value. */
  def shape(value: JsValue): View = {
    setPropertyValue("shape", value)
  }

  /** Sets the size property. */
  def size(field: JsValue,
           `type`: String,
           bin: Either[Boolean, JsObject] = Left(false),
           timeUnit: String = "",
           aggregate: String = "",
           scale: JsValue = JsUndefined,
           legend: JsValue = JsUndefined,
           condition: JsValue = JsUndefined): View = {
    setProperty("size", field, `type`, bin, timeUnit, aggregate, scale, legend, condition)
  }

  /** Sets the size value. */
  def size(value: JsValue): View = {
    setPropertyValue("size", value)
  }

  /** Sets the text property. */
  def text(field: JsValue,
           `type`: String,
           bin: Either[Boolean, JsObject] = Left(false),
           timeUnit: String = "",
           aggregate: String = "",
           scale: JsValue = JsUndefined,
           legend: JsValue = JsUndefined,
           condition: JsValue = JsUndefined): View = {
    setProperty("text", field, `type`, bin, timeUnit, aggregate, scale, legend, condition)
  }

  /** Sets the text value. */
  def text(value: JsValue): View = {
    setPropertyValue("text", value)
  }

  /** Sets the opacity property. */
  def opacity(field: JsValue,
              `type`: String,
              bin: Either[Boolean, JsObject] = Left(false),
              timeUnit: String = "",
              aggregate: String = "",
              scale: JsValue = JsUndefined,
              legend: JsValue = JsUndefined,
              condition: JsValue = JsUndefined): View = {
    setProperty("opacity", field, `type`, bin, timeUnit, aggregate, scale, legend, condition)
  }

  /** Sets the opacity value. */
  def opacity(value: JsValue): View = {
    setPropertyValue("opacity", value)
  }

  /** Sets the view background’s fill and stroke.
    * The background property of a top-level view specification defines the
    * background of the whole visualization canvas. Meanwhile, the view
    * property of a single-view or layer specification can define the
    * background of the view.
    */
  def view(background: JsObject): View = {
    spec.view = background
    this
  }

  /** Selections are the basic building block in Vega-Lite’s grammar of
    * interaction. They map user input (e.g., mouse moves and clicks,
    * touch presses, etc.) into data queries, which can subsequently
    * be used to drive conditional encoding rules, filter data points,
    * or determine scale domains.
    *
    * The simplest definition of selection consists of a name and a type.
    * The selection type determines the default events that trigger a
    * selection and the resultant data query.
    *
    * Vega-Lite currently supports three selection types:
    *
    * - "single" to select a single discrete data value on click.
    * - "multi" to select multiple discrete data value; the first value
    *   is selected on click and additional values toggled on shift-click.
    * - "interval" to select a continuous range of data values on drag.
    *
    * @param selections (name, type) pairs
    */
  def selection(selections: (String, String)*): View = {
    spec.selection = JsObject(
      selections.map { case (name, t) =>
          name -> JsObject("type" -> JsString(t))
      }: _*
    )
    this
  }

  /** Sets geographic projection, which will be applied to shape path for
    * "geoshape" marks and to latitude and "longitude" channels for other
    * marks.
    *
    * A cartographic projection maps longitude and latitude pairs to x, y
    * coordinates. As with Vega, one can use projections in Vega-Lite to
    * layout both geographic points (such as locations on a map) represented
    * by longitude and latitude coordinates, or to project geographic regions
    * (such as countries and states) represented using the GeoJSON format.
    * Projections are specified at the unit specification level, alongside
    * encoding. Geographic coordinate data can then be mapped to longitude
    * and latitude channels (and longitude2 and latitude2 for ranged marks).
    */
  def projection(projection: JsObject): View = {
    spec.projection = projection
    this
  }
}

object View {
  /** Returns a field definition.
    * To encode a particular field in the data set with an encoding channel,
    * the channel’s field definition must describe the field name and its
    * data type.
    *
    * @param field A string defining the name of the field from which to
    *              pull a data value or an object defining iterated values
    *              from the repeat operator. field is not required if
    *              aggregate is count.
    * @param `type`    The encoded field’s type of measurement ("quantitative",
    *                  "temporal", "ordinal", or "nominal"). It can also be a
    *                  "geojson" type for encoding ‘geoshape’.
    *
    *                  Data type describes the semantics of the data rather than
    *                  the primitive data types (number, string, etc.). The same
    *                  primitive data type can have different types of
    *               measurement. For example, numeric data can represent
    *                  quantitative, ordinal, or nominal data.
    *
    *                  Data values for a temporal field can be either a
    *                  date-time string (e.g., "2015-03-07 12:32:17",
    *                  "17:01", "2015-03-16", "2015") or a timestamp
    *                  number (e.g., 1552199579097).
    *
    *                  Secondary channels (e.g., x2, y2, xError, yError) do not
    *                  have type as they have exactly the same type as their
    *                  primary channels (e.g., x, y)
    * @param bin       A flag for binning a quantitative field.
    *                  - If true, default binning parameters will be applied.
    *                  - If "binned", this indicates that the data for the x
    *                    (or y) channel are already binned.
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
  def field(field: JsValue,
            `type`: String,
            bin: Either[Boolean, JsObject] = Left(false),
            timeUnit: String = "",
            aggregate: String = "",
            title: String = ""): JsObject = {
    val json = JsObject()

    // field is not required if aggregate is count.
    if (field == null && aggregate != "count")
      throw new IllegalArgumentException("field is null while aggregate is not count")

    if (field != null) json.field = field
    if (`type` != null) json("type") = `type`

    bin match {
      case Left(flag) => if (flag && `type` == "quantitative") json.bin = flag
      case Right(params) => json.bin = params
    }

    if (timeUnit.nonEmpty) json.timeUnit = timeUnit
    if (aggregate.nonEmpty) json.aggregate = aggregate
    if (title == null) json.title = JsNull
    else if (title.nonEmpty) json.title = title

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
    *              from the repeat operator. field is not required if
    *              aggregate is count
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
    * @param bin       A flag for binning a quantitative field.
    *                  - If true, default binning parameters will be applied.
    *                  - If "binned", this indicates that the data for the x
    *                    (or y) channel are already binned.
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
  def positionField(field: JsValue,
                    `type`: String,
                    bin: Either[Boolean, JsObject] = Left(false),
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

  /** Returns a polar field definition.
    * theta and radius position channels determine the position or interval
    * on polar coordindates for arc and text marks.
    *
    * @param field A string defining the name of the field from which to
    *              pull a data value or an object defining iterated values
    *              from the repeat operator. field is not required if
    *              aggregate is count
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
    * @param bin       A flag for binning a quantitative field.
    *                  - If true, default binning parameters will be applied.
    *                  - If "binned", this indicates that the data for the x
    *                    (or y) channel are already binned.
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
  def polarField(field: JsValue,
                 `type`: String,
                 bin: Either[Boolean, JsObject] = Left(false),
                 timeUnit: String = "",
                 aggregate: String = "",
                 title: String = "",
                 scale: JsValue = JsUndefined,
                 sort: Option[String] = None,
                 stack: JsValue = JsUndefined): JsObject = {
    val json = this.field(field, `type`, bin, timeUnit, aggregate, title)

    if (scale != JsUndefined) json.scale = scale
    if (sort.isDefined) json.sort = sort.get
    if (stack != JsUndefined) json.stack = stack

    json
  }

  /** Returns a mark property field Definition.
    * x and y position channels determine the position of the marks,
    * or width/height of horizontal/vertical "area" and "bar".
    * In addition, x2 and y2 can specify the span of ranged area, bar,
    * rect, and rule.
    *
    * @param field A string defining the name of the field from which to
    *              pull a data value or an object defining iterated values
    *              from the repeat operator. field is not required if
    *              aggregate is count
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
    * @param bin       A flag for binning a quantitative field.
    *                  - If true, default binning parameters will be applied.
    *                  - If "binned", this indicates that the data for the x
    *                    (or y) channel are already binned.
    * @param timeUnit  Time unit (e.g., year, yearmonth, month, hours) for a
    *                  temporal field, or a temporal field that gets casted
    *                  as ordinal.
    * @param aggregate Aggregation function for the field (e.g., "mean",
    *                  "sum", "median", "min", "max", "count").
    * @param scale     An object defining properties of the channel’s scale,
    *                  which is the function that transforms values in the data
    *                  domain (numbers, dates, strings, etc) to visual values
    *                  (pixels, colors, sizes) of the encoding channels.
    *
    *                  If null, the scale will be disabled and the data value
    *                  will be directly encoded.
    *
    *                  If undefined, default scale properties are applied.
    * @param legend    An object defining properties of the legend. If null,
    *                  the legend for the encoding channel will be removed.
    * @param condition One or more value definition(s) with a selection or
    *                  a test predicate.
    *
    *                  Note: A field definition’s condition property can only
    *                  contain conditional value definitions since Vega-Lite
    *                  only allows at most one encoded field per encoding
    *                  channel.
    */
  def markPropField(field: JsValue,
                    `type`: String,
                    bin: Either[Boolean, JsObject] = Left(false),
                    timeUnit: String = "",
                    aggregate: String = "",
                    scale: JsValue = JsUndefined,
                    legend: JsValue = JsUndefined,
                    condition: JsValue = JsUndefined): JsObject = {
    val json = this.field(field, `type`, bin, timeUnit, aggregate)

    if (scale != JsUndefined) json.scale = scale
    if (legend != JsUndefined) json.legend = legend
    if (condition != JsUndefined) json.condition = condition

    json
  }
}
