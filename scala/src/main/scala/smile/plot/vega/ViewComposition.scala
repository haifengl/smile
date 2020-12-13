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

/** All view composition specifications (layer, facet, concat, and repeat)
  * can have the resolve property for scale, axes, and legend resolution.
  *
  * Vega-Lite determines whether scale domains should be unioned.
  * If the scale domain is unioned, axes and legends can be merged.
  * Otherwise they have to be independent.
  *
  * There are two options to resolve a scale, axis, or legend: "shared"
  * and "independent". Independent scales imply independent axes and legends.
  */
trait ViewComposition extends VegaLite {
  /** Scale resolutions.
    * For scales, resolution can be specified for every channel.
    */
  def resolveScale(scale: JsObject): ViewComposition = {
    if (!spec.contains("resolve")) spec.resolve = JsObject()
    spec.resolve.scale = scale
    this
  }

  /** Axis resolutions.
    * For axes, resolutions can be defined for x and y (positional channels).
    */
  def resolveAxis(axis: JsObject): ViewComposition = {
    if (!spec.contains("resolve")) spec.resolve = JsObject()
    spec.resolve.axis = axis
    this
  }

  /** Legend resolutions.
    * For legends, resolutions can be defined for color, opacity, shape,
    * and size (non-positional channels).
    */
  def resolveLegend(legend: JsObject): ViewComposition = {
    if (!spec.contains("resolve")) spec.resolve = JsObject()
    spec.resolve.legend = legend
    this
  }
}
