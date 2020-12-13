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

/** To superimpose one chart on top of another. */
trait Layer extends View with ViewComposition {
  /** Sets the Layer or single View specifications to be layered.
    *
    * Note: Specifications inside layer cannot use row and column
    * channels as layering facet specifications is not allowed.
    * Instead, use the facet operator and place a layer inside a facet.
    */
  def layer(layers: View*): Layer = {
    spec.layer = JsArray(layers.map(_.spec): _*)
    this
  }
}
