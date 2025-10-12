/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.plot

import scala.language.experimental.macros
import scala.reflect.macros.whitebox
import scala.util.Try
import smile.plot.swing.Canvas
import smile.plot.vega.VegaLite

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
  def renderMultiFigurePane: Tree = {
    val possibilities: Try[Tree] =
      compile(q"""(grid: MultiFigurePane) => { println(org.apache.zeppelin.spark.utils.DisplayUtils.html(smile.plot.swing.Html.of(grid))) }""") orElse
        compile(q"""(grid: MultiFigurePane) => { publish.html(smile.plot.swing.Html.of(grid)) }""") orElse
        compile(q"""(grid: MultiFigurePane) => { display.html(smile.plot.swing.Html.of(grid)) }""") orElse
        compile(q"""(grid: MultiFigurePane) => { kernel.display.content("text/html", smile.plot.swing.Html.of(grid)) }""") orElse
        compile(q"""(grid: MultiFigurePane) => { smile.plot.Render.desktop(grid) }""")

    possibilities.getOrElse(c.abort(c.enclosingPosition, "No default PlotGrid renderer could be materialized"))
  }
}

object RenderMacro {
  implicit def renderVegaMacro: VegaLite => Unit = macro RenderMacro.renderVega
  implicit def renderCanvasMacro: Canvas => Unit = macro RenderMacro.renderCanvas
  implicit def renderMultiFigureMacro: Canvas => Unit = macro RenderMacro.renderMultiFigurePane
}
