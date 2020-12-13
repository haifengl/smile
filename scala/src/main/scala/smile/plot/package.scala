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

package smile

import smile.plot.swing.{Canvas, PlotGrid}
import smile.plot.vega.VegaLite

/** Data visualization.
  *
  * @author Haifeng Li
  */
package object plot {
  /** Shows a plot canvas with implicit renderer. */
  def show(canvas: Canvas)(implicit renderer: Canvas => Unit): Unit = {
    renderer(canvas)
  }

  /** Shows a plot grid with implicit renderer. */
  def show(grid: PlotGrid)(implicit renderer: PlotGrid => Unit): Unit = {
    renderer(grid)
  }

  /** Shows a vega-lite plot with implicit renderer. */
  def show(spec: VegaLite)(implicit renderer: VegaLite => Unit): Unit = {
    renderer(spec)
  }
}
