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

/** Vega-Lite specifications are JSON objects that describe a diverse range
  * of interactive visualizations. Besides using a single view specification
  * as a standalone visualization, Vega-Lite also provides operators for
  * composing multiple view specifications into a layered or multi-view
  * specification. These operators include layer, facet, concat, and repeat.
  */
trait VegaLite {
  /** The specification */
  val spec: JsObject

  override def toString: String = spec.prettyPrint

  // ====== Properties of Top-Level Specifications ======
  /** CSS color property to use as the background of the entire view. */
  def background(color: String): VegaLite = {
    spec.background = color
    this
  }

  /** Specifies padding for all sides.
    * The visualization padding, in pixels, is from the edge of the
    * visualization canvas to the data rectangle.
    */
  def padding(size: Int): VegaLite = {
    spec.padding = size
    this
  }

  /** Specifies padding for each side.
    * The visualization padding, in pixels, is from the edge of the
    * visualization canvas to the data rectangle.
    */
  def padding(left: Int, top: Int, right: Int, bottom: Int): VegaLite = {
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
  def autosize(`type`: String = "pad", resize: Boolean = false, contains: String = "content"): VegaLite = {
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
  def config(properties: JsObject): VegaLite = {
    spec.config = properties
    this
  }

  /** Optional metadata that will be passed to Vega. This object is completely
    * ignored by Vega and Vega-Lite and can be used for custom metadata.
    */
  def usermeta(data: JsValue): VegaLite = {
    spec.usermeta = data
    this
  }

  // ====== Common Properties ======
  /** Sets the name of the visualization for later reference. */
  def name(name: String): VegaLite = {
    spec.name = name
    this
  }

  /** Sets the description of this mark for commenting purpose. */
  def description(description: String): VegaLite = {
    spec.description = description
    this
  }

  /** Sets a descriptive title to a chart. */
  def title(title: String): VegaLite = {
    spec.title = title
    this
  }

  /** Sets an object describing the data source. Set to null to ignore
    * the parent’s data source. If no data is set, it is derived from
    * the parent.
    */
  def data(df: DataFrame): VegaLite = {
    if (df == null) spec.remove("data")
    else spec.data = JsObject("values" -> df.toJSON)
    this
  }

  /** Sets the url of the data source.
    *
    * @param url An URL from which to load the data set.
    * @param format Type of input data: "json", "csv", "tsv", "dsv".
    *               Default value: The default format type is determined
    *               by the extension of the file URL. If no extension is
    *               detected, "json" will be used by default.
    */
  def data(url: String, format: String = ""): VegaLite = {
    spec.data = JsObject("url" -> JsString(url))
    if (!format.isEmpty) spec.data.format = JsObject("type" -> JsString(format))
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
  def transform(transforms: JsObject*): VegaLite = {
    spec.transform = JsArray(transforms)
    this
  }

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
       |  var opt = {
       |    "mode": "vega-lite",
       |    "renderer": "canvas",
       |    "actions": {"editor": true, "source": true, "export": true}
       |  };
       |  vegaEmbed('#vega-lite', spec, opt)
       |    .then(result => console.log(result))
       |    .catch(console.error);
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
       |        var height = el.contentWindow.document.body.scrollHeight || '600'; // Fallback in case of no scroll height
       |        el.style.height = height + 'px';
       |        if (k <= 10) { setTimeout(function() { resizeIFrame(el, k+1) }, 1000 + (k * 250)) };
       |      }
       |      resizeIFrame(document.querySelector('#${id}'), 1);
       |    })(); // IIFE
       |  </script>
    """.stripMargin
  }
}

object VegaLite {
  /** The schema of Vega-Lite. */
  val $schema = "https://vega.github.io/schema/vega-lite/v4.json"
  /** The MIME type of Vega-Lite. */
  val mime: String = "application/vnd.vegalite.v4+json"

  /** Returns a single view vega-lite specification with inline data. */
  def apply(df: DataFrame): View = {
    new View {
      override val spec = of(df)
    }
  }

  /** Returns a single view vega-lite specification with data from from URL.
    *
    * @param url An URL from which to load the data set.
    * @param format Type of input data: "json", "csv", "tsv", "dsv".
    *               Default value: The default format type is determined
    *               by the extension of the file URL. If no extension is
    *               detected, "json" will be used by default.
    */
  def apply(url: String, format: String = ""): View = {
    new View {
      override val spec = of(url, format)
    }
  }

  /** Returns a view to be used in a composition.  */
  def view: View = {
    new View {
      override val spec = JsObject()
    }
  }

  /** Returns a layered view.  */
  def layer(df: DataFrame, layers: View*): Layer = {
    new Layer {
      override val spec = of(df)
      layer(layers: _*)
    }
  }

  /** Returns a specification.
    * @param properties The properties of top-Level specifications
    *                   or common properties.
    */
  private def of(properties: (String, JsValue)*): JsObject = {
    val vega = JsObject(
      "$schema" -> $schema,
      "config" -> JsObject(
        "view" -> JsObject(
          "continuousWidth" -> 400,
          "continuousHeight" -> 300
        )
      )
    )

    properties.foreach { case (field , value) =>
      vega(field) = value
    }

    vega
  }

  /** Returns a specification object. */
  private def of(df: DataFrame): JsObject = {
    of("data" -> JsObject("values" -> df.toJSON))
  }

  /** Returns a specification object. */
  private def of(url: String, format: String = ""): JsObject = {
    val data = JsObject("url" -> JsString(url))
    if (!format.isEmpty) data.format = JsObject("type" -> JsString(format))
    of("data" -> data)
  }
}