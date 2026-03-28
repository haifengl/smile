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
package smile.plot

import scala.language.experimental.macros
import smile.plot.swing.{Canvas, MultiFigurePane}
import smile.plot.vega.VegaLite

/** Implicit renderers. */
object Render {
  implicit def renderVega: VegaLite => Unit = macro RenderMacro.renderVega
  implicit def renderCanvas: Canvas => Unit = macro RenderMacro.renderCanvas
  implicit def renderMultiFigure: MultiFigurePane => Unit = macro RenderMacro.renderMultiFigurePane

  /** Desktop renderer of plot canvas. */
  def desktop(canvas: Canvas): Unit = {
    smile.plot.swing.JWindow(canvas)
  }

  /** Desktop renderer of multi-figure pane. */
  def desktop(canvas: MultiFigurePane): Unit = {
    smile.plot.swing.JWindow(canvas)
  }

  /** Desktop renderer of vega-lite plot with the default browser. */
  def desktop(spec: VegaLite): Unit = {
    spec.show()
  }
}
