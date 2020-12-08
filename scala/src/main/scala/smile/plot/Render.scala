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

package smile.plot

import scala.language.experimental.macros
import smile.plot.swing.{Canvas, PlotGrid}
import smile.plot.vega.VegaLite

/** Implicit renderers. */
object Render {
  implicit def renderVega: VegaLite => Unit = macro RenderMacro.renderVega
  implicit def renderCanvas: Canvas => Unit = macro RenderMacro.renderCanvas
  implicit def renderPlotGrid: PlotGrid => Unit = macro RenderMacro.renderPlotGrid

  /** Desktop renderer of plot canvas. */
  def desktop(canvas: Canvas): Unit = {
    smile.plot.swing.JWindow(canvas)
  }

  /** Desktop renderer of plot grid. */
  def desktop(grid: PlotGrid): Unit = {
    smile.plot.swing.JWindow(grid)
  }

  /** Desktop renderer of vega-lite plot with the default browser. */
  def desktop(spec: VegaLite): Unit = {
    import java.nio.file.Files
    val path = Files.createTempFile("smile-plot-", ".html")
    path.toFile.deleteOnExit()
    Files.write(path, spec.embed.getBytes(java.nio.charset.StandardCharsets.UTF_8))
    java.awt.Desktop.getDesktop.browse(path.toUri)
  }
}
