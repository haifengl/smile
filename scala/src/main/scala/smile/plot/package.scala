/*******************************************************************************
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
 ******************************************************************************/

package smile

import scala.language.implicitConversions
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

  /*
  /** Apache Zeppelin Notebook renderer of plot canvas. */
  implicit def zeppelin(canvas: Canvas): Window = {
    print(s"%html ${smile.plot.swing.canvas2HtmlImg(canvas)}")
  }

  /** Apache Zeppelin Notebook renderer of plot grid. */
  implicit def zeppelin(grid: PlotGrid): Window = {
    print(s"%html ${smile.plot.swing.swing2HtmlImg(grid)}")
  }

  /** Apache Zeppelin Notebook renderer of vega-lite plot. */
  implicit def zeppelin(spec: VegaLite): Window = {
    print(s"%html ${spec.iframe()}")
  }

  /** Apache Toree Notebook renderer of plot canvas. */
  implicit def toree(canvas: Canvas): Window = {
    kernel.display.content("text/html", smile.plot.swing.canvas2HtmlImg(canvas))
  }

  /** Apache Toree Notebook renderer of plot grid. */
  implicit def toree(canvas: PlotGrid): Window = {
    kernel.display.content("text/html", smile.plot.swing.swing2HtmlImg(canvas))
  }

  /** Apache Toree Notebook renderer of vega-lite plot. */
  implicit def toree(spec: VegaLite): Window = {
    kernel.display.content("text/html", spec.iframe())
  }

  /** Jupyter Notebook (Almond) renderer of plot canvas. */
  implicit def almond(canvas: Canvas): Window = {
    publish.html(smile.plot.swing.canvas2HtmlImg(canvas))
  }

  /** Jupyter Notebook (Almond) renderer of plot grid. */
  implicit def almond(canvas: PlotGrid): Window = {
    publish.html(smile.plot.swing.swing2HtmlImg(canvas))
  }

  /** Jupyter Notebook (Almond) renderer of vega-lite plot. */
  implicit def almond(spec: VegaLite): Window = {
    publish.html(spec.iframe())
  }
  */
}
