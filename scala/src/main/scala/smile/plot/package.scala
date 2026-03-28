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
package smile

import smile.plot.swing.{Canvas, MultiFigurePane}
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

  /** Shows a multi-figure pane with implicit renderer. */
  def show(canvas: MultiFigurePane)(implicit renderer: MultiFigurePane => Unit): Unit = {
    renderer(canvas)
  }

  /** Shows a vega-lite plot with implicit renderer. */
  def show(spec: VegaLite)(implicit renderer: VegaLite => Unit): Unit = {
    renderer(spec)
  }
}
