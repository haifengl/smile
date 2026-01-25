/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.plot.vega

import com.typesafe.scalalogging.LazyLogging
import smile.data.*
import smile.json.*

/** Vega-Lite specifications are JSON objects that describe a diverse range
  * of interactive visualizations. Besides using a single view specification
  * as a standalone visualization, Vega-Lite also provides operators for
  * composing multiple view specifications into a layered or multi-view
  * specification. These operators include layer, facet, concat, and repeat.
  */
trait VegaLite extends LazyLogging {
  /** The specification */
  val spec: JsObject

  override def toString: String = spec.prettyPrint

  // ====== Properties of Top-Level Specifications ======
  /** CSS color property to use as the background of the entire view. */
  def background(color: String): this.type = {
    spec.background = color
    this
  }

  /** Specifies padding for all sides.
    * The visualization padding, in pixels, is from the edge of the
    * visualization canvas to the data rectangle.
    */
  def padding(size: Int): this.type = {
    spec.padding = size
    this
  }

  /** Specifies padding for each side.
    * The visualization padding, in pixels, is from the edge of the
    * visualization canvas to the data rectangle.
    */
  def padding(left: Int, top: Int, right: Int, bottom: Int): this.type = {
    spec.padding = JsObject(
      "left" -> left,
      "top" -> top,
      "right" -> right,
      "bottom" -> bottom
    )
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
  def autosize(`type`: String = "pad", resize: Boolean = false, contains: String = "content"): this.type = {
    spec.autosize = JsObject(
      "type" -> `type`,
      "resize" -> resize,
      "contains" -> contains
    )
    this
  }

  /** Sets Vega-Lite configuration object that lists configuration properties
    * of a visualization for creating a consistent theme. This property can
    * only be defined at the top-level of a specification.
    */
  def config(properties: JsObject): this.type = {
    spec.config = properties
    this
  }

  /** Optional metadata that will be passed to Vega. This object is completely
    * ignored by Vega and Vega-Lite and can be used for custom metadata.
    */
  def usermeta(data: JsValue): this.type = {
    spec.usermeta = data
    this
  }

  // ====== Common Properties ======
  /** Sets the name of the visualization for later reference. */
  def name(name: String): this.type = {
    spec.name = name
    this
  }

  /** Sets the description of this mark for commenting purpose. */
  def description(description: String): this.type = {
    spec.description = description
    this
  }

  /** Sets a descriptive title to a chart. */
  def title(title: String): this.type = {
    spec.title = title
    this
  }

  /** Sets a JSON array describing the data source. Set to null to ignore
    * the parent's data source. If no data is set, it is derived from
    * the parent.
    */
  def data(json: JsArray): this.type = {
    if (json == null || json == JsNull || json == JsUndefined) spec.remove("data")
    else spec.data = JsObject("values" -> json)
    this
  }

  /** Sets an array of objects describing the data source.
    */
  def data(rows: JsObject*): this.type = {
    data(JsArray(rows*))
  }

  /** Sets a data frame describing the data source.
    */
  def data(df: DataFrame): this.type = {
    data(df.toJSON)
  }

  /** Sets the url of the data source.
    *
    * @param url A URL from which to load the data set.
    * @param format Type of input data: "json", "csv", "tsv", "dsv".
    *               Default value: The default format type is determined
    *               by the extension of the file URL. If no extension is
    *               detected, "json" will be used by default.
    */
  def data(url: String, format: JsValue = JsUndefined): this.type = {
    spec.data = JsObject("url" -> JsString(url))
    format match {
      case format: JsObject => spec.data.format = format
      case format: JsString => spec.data.format = JsObject("type" -> format)
      case _ => ()
    }
    this
  }

  /** An array of data transformations such as filter and new field
    * calculation. Data transformations in Vega-Lite are described
    * via either view-level transforms (the transform property) or
    * field transforms inside encoding (bin, timeUnit, aggregate,
    * sort, and stack).
    *
    * When both types of transforms are specified, the view-level
    * transforms are executed first based on the order in the
    * array. Then the inline transforms are executed in this order:
    * bin, timeUnit, aggregate, sort, and stack.
    */
  def transform(transforms: JsArray): this.type = {
    spec.transform = transforms
    this
  }

  /** An array of data transformations such as filter and new field
    * calculation. Data transformations in Vega-Lite are described
    * via either view-level transforms (the transform property) or
    * field transforms inside encoding (bin, timeUnit, aggregate,
    * sort, and stack).
    *
    * When both types of transforms are specified, the view-level
    * transforms are executed first based on the order in the
    * array. Then the inline transforms are executed in this order:
    * bin, timeUnit, aggregate, sort, and stack.
    */
  def transform(transforms: JsObject*): this.type = {
    spec.transform = JsArray(transforms*)
    this
  }

  /** Returns the HTML of plot specification with Vega Embed. */
  def html: String = {
    s"""
       |<!DOCTYPE html>
       |<html>
       |<head>
       |  <script type="text/javascript" src="https://cdn.jsdelivr.net/npm/vega@5"></script>
       |  <script type="text/javascript" src="https://cdn.jsdelivr.net/npm/vega-lite@5"></script>
       |  <script type="text/javascript" src="https://cdn.jsdelivr.net/npm/vega-embed@6"></script>
       |</head>
       |<body>
       |
       |<div id="vega-lite"></div>
       |
       |<script type="text/javascript">
       |  var spec = $spec;
       |  var opt = {
       |    "mode": "vega-lite",
       |    "renderer": "canvas",
       |    "actions": {"editor": true, "source": true, "export": true}
       |  };
       |  vegaEmbed('#vega-lite', spec, opt).catch(console.error);
       |</script>
       |</body>
       |</html>
    """.stripMargin
  }

  /** Returns the HTML wrapped in an iframe to render in notebooks.
    * @param id the iframe HTML id.
    */
  def iframe(id: String = java.util.UUID.randomUUID.toString): String = {
    val src = xml.Utility.escape(html)
    s"""
       |  <iframe id="$id" sandbox="allow-scripts allow-same-origin" style="border: none; width: 100%" srcdoc="$src"></iframe>
       |  <script>
       |    (function() {
       |      function resizeIFrame(el, k) {
       |        var height = el.contentWindow.document.body.scrollHeight || '600'; // Fallback in case of no scroll height
       |        el.style.height = height + 'px';
       |        if (k <= 10) { setTimeout(function() { resizeIFrame(el, k+1) }, 100 + (k * 250)) };
       |      }
       |      resizeIFrame(document.getElementById("$id"), 1);
       |    })(); // IIFE
       |  </script>
    """.stripMargin
  }

  /** Displays the plot with the default browser.
    * @param silent If true, silently swallow any exception.
    */
  @throws[java.io.IOException]
  @throws[java.awt.HeadlessException]
  def show(silent: Boolean = false): Unit = {
    try {
      import java.nio.file.Files
      val path = Files.createTempFile("smile-plot-", ".html")
      path.toFile.deleteOnExit()
      Files.write(path, html.getBytes(java.nio.charset.StandardCharsets.UTF_8))
      java.awt.Desktop.getDesktop.browse(path.toUri)
    } catch {
      case ex: Exception =>
        if (silent) logger.warn(s"Failed to show ${spec.name}", ex)
        else throw ex
    }
  }
}

object VegaLite {
  /** The schema of Vega-Lite. */
  val $schema = "https://vega.github.io/schema/vega-lite/v5.json"
  /** The MIME type of Vega-Lite. */
  val mime: String = "application/vnd.vegalite.v5+json"

  /** Returns a single view specification to be used in a composition.
    * @param title view title.
    */
  def view(title: String = ""): View = {
    val view = new View {
      override val spec: JsObject = VegaLite.spec()
    }
    if (title.nonEmpty) view.title(title)
    view
  }

  /** Returns a facet specification with inline data. */
  def facet(title: String = ""): Facet = {
    val facet = new Facet {
      override val spec: JsObject = VegaLite.spec()
    }
    if (title.nonEmpty) facet.title(title)
    facet
  }

  /** Returns a layered view specification.  */
  def layer(layers: View*): Layer = {
    val layer = new Layer {
      override val spec: JsObject = VegaLite.spec()
    }
    layer.layer(layers*)
    layer
  }

  /** Horizontal concatenation. Put multiple views into a column.  */
  def hconcat(views: VegaLite*): ViewLayoutComposition = {
    new ViewLayoutComposition {
      override val spec: JsObject = VegaLite.spec()
      spec.hconcat = JsArray(views.map(_.spec)*)
    }
  }

  /** Vertical concatenation. Put multiple views into a row.  */
  def vconcat(views: VegaLite*): ViewLayoutComposition = {
    new ViewLayoutComposition {
      override val spec: JsObject = VegaLite.spec()
      spec.vconcat = JsArray(views.map(_.spec)*)
    }
  }

  /** General (wrappable) concatenation. Put multiple views into
    * a flexible flow layout.
    */
  def concat(columns: Int, views: VegaLite*): ViewLayoutComposition = {
    new ViewLayoutComposition {
      override val spec: JsObject = VegaLite.spec()
      spec.columns = columns
      spec.concat = JsArray(views.map(_.spec)*)
    }
  }

  /** Creates a view for each entry in an array of fields. This operator
    * generates multiple plots like facet. However, unlike facet it allows
    * full replication of a data set in each view.
    *
    * @param fields The fields that should be used for each entry.
    */
  def repeat(view: VegaLite, fields: String*): ViewLayoutComposition = {
    new ViewLayoutComposition {
      override val spec: JsObject = VegaLite.spec()
      spec.repeat = JsObject("layer" -> JsArray(fields.map(JsString(_))*))
      spec.spec = view.spec
    }
  }

  /** Creates a view for each entry in an array of fields. This operator
    * generates multiple plots like facet. However, unlike facet it allows
    * full replication of a data set in each view.
    *
    * @param row An array of fields to be repeated vertically.
    * @param column An array of fields to be repeated horizontally.
    */
  def repeat(view: VegaLite, row: Seq[String], column: Seq[String]): ViewLayoutComposition = {
    new ViewLayoutComposition {
      override val spec: JsObject = VegaLite.spec()
      spec.repeat = JsObject(
        "row" -> JsArray(row.map(JsString(_))*),
        "column" -> JsArray(column.map(JsString(_))*)
      )
      spec.spec = view.spec
    }
  }

  /** Scatterplot Matrix (SPLOM). */
  def splom(df: DataFrame, color: String = ""): ViewLayoutComposition = {
    val fields = df.names.filter(_ != color).toIndexedSeq
    val spec = json"""{
                     |  "mark": "point",
                     |  "encoding": {
                     |    "x": {
                     |      "field": {"repeat": "column"},
                     |      "type": "quantitative",
                     |      "scale": {"zero": false}
                     |    },
                     |    "y": {
                     |      "field": {"repeat": "row"},
                     |      "type": "quantitative",
                     |      "scale": {"zero": false}
                     |    }
                     |  }
                     |}"""
    if (color.nonEmpty)
      spec.encoding.color = JsObject(
        "field" -> color,
        "type" -> "nominal"
      )

    val vega = repeat(view(), fields.reverse, fields)
    vega.data(df)
    vega.spec.spec = spec
    vega
  }

  /** Returns a specification.
    * @param properties The properties of top-Level specifications
    *                   or common properties.
    */
  private def spec(properties: (String, JsValue)*): JsObject = {
    val vega = JsObject(
      "$schema" -> $schema,
      "config" -> JsObject(
        "view" -> JsObject(
          "continuousWidth" -> 400,
          "continuousHeight" -> 400
        )
      )
    )

    properties.foreach { case (field , value) =>
      vega(field) = value
    }

    vega
  }
}
