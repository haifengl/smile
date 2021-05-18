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

/** A facet is a trellis plot (or small multiple) of a series of similar
  * plots that displays different subsets of the same data, facilitating
  * comparison across subsets.
  *
  * The facet channels (facet, row, and column) are encoding channels that
  * serves as macros for a facet specification. Vega-Lite automatically
  * translates this shortcut to use the facet operator.
  */
trait Facet extends View {
  /** A field definition for the (flexible) facet of trellis plots.
    * If either row or column is specified, this channel will be ignored.
    *
    * @param field A string defining the name of the field from which to
    *              pull a data value or an object defining iterated values
    *              from the repeat operator.
    * @param `type`   The encoded field’s type of measurement ("quantitative",
    *                 "temporal", "ordinal", or "nominal"). It can also be a
    *                 "geojson" type for encoding ‘geoshape’.
    *
    *                 Data type describes the semantics of the data rather than
    *                 the primitive data types (number, string, etc.). The same
    *                 primitive data type can have different types of
    *                 measurement. For example, numeric data can represent
    *                 quantitative, ordinal, or nominal data.
    *
    *                 Data values for a temporal field can be either a
    *                 date-time string (e.g., "2015-03-07 12:32:17",
    *                 "17:01", "2015-03-16", "2015") or a timestamp
    *                 number (e.g., 1552199579097).
    * @param bin      A flag for binning a quantitative field.
    *                 - If true, default binning parameters will be applied.
    *                 - If "binned", this indicates that the data for the x
    *                    (or y) channel are already binned.
    * @param timeUnit Time unit (e.g., year, yearmonth, month, hours) for a
    *                 temporal field, or a temporal field that gets casted
    *                 as ordinal.
    * @param align    The alignment to apply to row/column facet’s subplot.
    *                 The supported string values are "all", "each", and "none".
    *
    *                 - For "none", a flow layout will be used, in which
    *                   adjacent subviews are simply placed one after the other.
    *                 - For "each", subviews will be aligned into a clean
    *                   grid structure, but each row or column may be of
    *                   variable size.
    *                 - For "all", subviews will be aligned and each row
    *                   or column will be sized identically based on the
    *                   maximum observed size. String values for this
    *                   property will be applied to both grid rows and
    *                   columns.
    * @param center   Boolean flag indicating if facet’s subviews should
    *                 be centered relative to their respective rows or columns.
    * @param spacing  The spacing in pixels between facet’s sub-views.
    * @param columns  The number of columns to include in the view
    *                 composition layout.
    */
  def facet(field: String,
            `type`: String = "quantitative",
            bin: Either[Boolean, JsObject] = Left(false),
            timeUnit: String = "",
            align: String = "all",
            center: Boolean = false,
            spacing: Option[Int] = None,
            columns: Option[Int] = None): Facet = {
    if (!spec.contains("encoding")) spec.encoding = JsObject()
    spec.encoding.facet = Facet.field(field, `type`, bin, timeUnit, align, center, spacing)
    if (columns.isDefined) spec.facet.columns = columns.get
    this
  }

  /** A field definition for the vertical facet of trellis plots. */
  def row(field: String,
          `type`: String = "quantitative",
          bin: Either[Boolean, JsObject] = Left(false),
          timeUnit: String = "",
          align: String = "all",
          center: Boolean = false,
          spacing: Option[Int] = None): Facet = {
    if (!spec.contains("encoding")) spec.encoding = JsObject()
    spec.encoding.row = Facet.field(field, `type`, bin, timeUnit, align, center, spacing)
    this
  }

  /** A field definition for the horizontal facet of trellis plots. */
  def column(field: String,
             `type`: String = "quantitative",
             bin: Either[Boolean, JsObject] = Left(false),
             timeUnit: String = "",
             align: String = "all",
             center: Boolean = false,
             spacing: Option[Int] = None): Facet = {
    if (!spec.contains("encoding")) spec.encoding = JsObject()
    spec.encoding.column = Facet.field(field, `type`, bin, timeUnit, align, center, spacing)
    this
  }
}

object Facet {
  /** Returns a facet field Definition.
    *
    * @param field A string defining the name of the field from which to
    *              pull a data value or an object defining iterated values
    *              from the repeat operator.
    * @param `type`   The encoded field’s type of measurement ("quantitative",
    *                 "temporal", "ordinal", or "nominal"). It can also be a
    *                 "geojson" type for encoding ‘geoshape’.
    *
    *                 Data type describes the semantics of the data rather than
    *                 the primitive data types (number, string, etc.). The same
    *                 primitive data type can have different types of
    *                 measurement. For example, numeric data can represent
    *                 quantitative, ordinal, or nominal data.
    *
    *                 Data values for a temporal field can be either a
    *                 date-time string (e.g., "2015-03-07 12:32:17",
    *                 "17:01", "2015-03-16", "2015") or a timestamp
    *                 number (e.g., 1552199579097).
    * @param bin      A flag for binning a quantitative field.
    *                 - If true, default binning parameters will be applied.
    *                 - If "binned", this indicates that the data for the x
    *                   (or y) channel are already binned.
    * @param timeUnit Time unit (e.g., year, yearmonth, month, hours) for a
    *                 temporal field, or a temporal field that gets casted
    *                 as ordinal.
    * @param align    The alignment to apply to row/column facet’s subplot.
    *                 The supported string values are "all", "each", and "none".
    *
    *                 - For "none", a flow layout will be used, in which
    *                   adjacent subviews are simply placed one after the other.
    *                 - For "each", subviews will be aligned into a clean
    *                   grid structure, but each row or column may be of
    *                   variable size.
    *                 - For "all", subviews will be aligned and each row
    *                   or column will be sized identically based on the
    *                   maximum observed size. String values for this
    *                   property will be applied to both grid rows and
    *                   columns.
    * @param center   Boolean flag indicating if facet’s subviews should
    *                 be centered relative to their respective rows or columns.
    * @param spacing  The spacing in pixels between facet’s sub-views.
    */
  def field(field: String,
            `type`: String = "quantitative",
            bin: Either[Boolean, JsObject] = Left(false),
            timeUnit: String = "",
            align: String = "all",
            center: Boolean = false,
            spacing: Option[Int] = None): JsObject = {
    val json = View.field(field, `type`, bin, timeUnit)

    json.align = align
    json.center = center
    if (spacing.isDefined) json.spacing = spacing.get

    json
  }
}