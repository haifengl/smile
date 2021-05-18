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

import scala.reflect.macros.whitebox
import scala.util.Try

/** Guess the notebook environment. */
class RenderMacro(val c: whitebox.Context) {
  import c.universe.{Try => _, _}

  /** Try to compile a piece of code.  */
  private def compile(tree: Tree) = Try(c.typecheck(tree))

  /** Materialize the vega renderer. */
  def renderVega: Tree = {
    val possibilities: Try[Tree] =
      compile(q"""(spec: VegaLite) => { println(org.apache.zeppelin.spark.utils.DisplayUtils.html(spec.iframe())) }""") orElse
        compile(q"""(spec: VegaLite) => { publish.html(spec.iframe()) }""") orElse
        compile(q"""(spec: VegaLite) => { display.html(spec.iframe()) }""") orElse
        compile(q"""(spec: VegaLite) => { kernel.display.content("text/html", spec.iframe()) }""") orElse
        compile(q"""(spec: VegaLite) => { smile.plot.Render.desktop(spec) }""")

    possibilities.getOrElse(c.abort(c.enclosingPosition, "No default VegaLite renderer could be materialized"))
  }

  /** Materialize the canvas renderer. */
  def renderCanvas: Tree = {
    val possibilities: Try[Tree] =
      compile(q"""(canvas: Canvas) => { println(org.apache.zeppelin.spark.utils.DisplayUtils.html(smile.plot.swing.Html.canvas(canvas))) }""") orElse
        compile(q"""(canvas: Canvas) => { publish.html(smile.plot.swing.Html.canvas(canvas)) }""") orElse
        compile(q"""(canvas: Canvas) => { display.html(smile.plot.swing.Html.canvas(canvas)) }""") orElse
        compile(q"""(canvas: Canvas) => { kernel.display.content("text/html", smile.plot.swing.Html.canvas(canvas)) }""") orElse
        compile(q"""(canvas: Canvas) => { smile.plot.Render.desktop(canvas) }""")

    possibilities.getOrElse(c.abort(c.enclosingPosition, "No default Canvas renderer could be materialized"))
  }

  /** Materialize the plot grid renderer. */
  def renderPlotGrid: Tree = {
    val possibilities: Try[Tree] =
      compile(q"""(grid: PlotGrid) => { println(org.apache.zeppelin.spark.utils.DisplayUtils.html(smile.plot.swing.Html.of(grid))) }""") orElse
        compile(q"""(grid: PlotGrid) => { publish.html(smile.plot.swing.Html.of(grid)) }""") orElse
        compile(q"""(grid: PlotGrid) => { display.html(smile.plot.swing.Html.of(grid)) }""") orElse
        compile(q"""(grid: PlotGrid) => { kernel.display.content("text/html", smile.plot.swing.Html.of(grid)) }""") orElse
        compile(q"""(grid: PlotGrid) => { smile.plot.Render.desktop(grid) }""")

    possibilities.getOrElse(c.abort(c.enclosingPosition, "No default PlotGrid renderer could be materialized"))
  }
}