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

/** all view layout composition (facet, concat, and repeat) can have the
  * following layout properties: align, bounds, center, spacing.
  */
trait ViewLayoutComposition extends ViewComposition {
  /** The alignment to apply to grid rows and columns. The supported string
    * values are "all" (the default), "each", and "none".
    *
    * - For "none", a flow layout will be used, in which adjacent subviews
    *   are simply placed one after the other.
    * - For "each", subviews will be aligned into a clean grid structure,
    *   but each row or column may be of variable size.
    * - For "all", subviews will be aligned and each row or column will be
    *   sized identically based on the maximum observed size. String values
    *   for this property will be applied to both grid rows and columns.
    */
  def align(align: String): ViewLayoutComposition = {
    spec.align = align
    this
  }

  /** Sets different alignments for rows and columns.. */
  def align(row: String, column: String): ViewLayoutComposition = {
    spec.align = JsObject(
      "row" -> row,
      "column" -> column
    )
    this
  }

  /** The bounds calculation method to use for determining the extent
    * of a sub-plot. One of full (the default) or flush.
    *
    * - If set to full, the entire calculated bounds (including axes,
    *   title, and legend) will be used.
    * - If set to flush, only the specified width and height values
    *   for the sub-view will be used. The flush setting can be useful
    *   when attempting to place sub-plots without axes or legends into
    *   a uniform grid structure.
    */
  def bounds(bounds: String): ViewLayoutComposition = {
    spec.bounds = bounds
    this
  }

  /** Boolean flag indicating if subviews should be centered relative to
    * their respective rows or columns.
    */
  def center(flag: Boolean): ViewLayoutComposition = {
    spec.center = flag
    this
  }

  /** Sets different spacing values for rows and columns.. */
  def center(row: Int, column: Int): ViewLayoutComposition = {
    spec.center = JsObject(
      "row" -> row,
      "column" -> column
    )
    this
  }

  /** The spacing in pixels between sub-views of the composition operator. */
  def spacing(size: Int): ViewLayoutComposition = {
    spec.spacing = size
    this
  }

  /** Sets different spacing values for rows and columns.. */
  def spacing(row: Int, column: Int): ViewLayoutComposition = {
    spec.spacing = JsObject(
      "row" -> row,
      "column" -> column
    )
    this
  }
}

