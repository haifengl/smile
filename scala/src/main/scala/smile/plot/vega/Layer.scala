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
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.plot.vega

import smile.json.*

/** To superimpose one chart on top of another. */
trait Layer extends View with ViewComposition {
  /** Sets the Layer or single View specifications to be layered.
    *
    * Note: Specifications inside layer cannot use row and column
    * channels as layering facet specifications is not allowed.
    * Instead, use the facet operator and place a layer inside a facet.
    */
  def layer(layers: View*): this.type = {
    spec.layer = JsArray(layers.map(_.spec)*)
    this
  }
}
