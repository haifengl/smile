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
  def show[R](canvas: Canvas)(implicit renderer: Canvas => R): R = {
    renderer(canvas)
  }

  /** Shows a plot grid with implicit renderer. */
  def show[R](canvas: PlotGrid)(implicit renderer: PlotGrid => R): R = {
    renderer(canvas)
  }

  /** Shows a vega-lite plot with implicit renderer. */
  def show[R](spec: VegaLite)(implicit renderer: VegaLite => R): R = {
    renderer(spec)
  }

  /** Desktop renderer of plot canvas. */
  implicit def desktop(canvas: Canvas): smile.plot.swing.CanvasWindow = {
    smile.plot.swing.Window(canvas)
  }

  /** Desktop renderer of plot grid. */
  implicit def desktop(canvas: PlotGrid): smile.plot.swing.PlotGridWindow = {
    smile.plot.swing.Window(canvas)
  }

  /** Desktop renderer of vega-lite plot with the default browser. */
  implicit def desktop(spec: VegaLite): Unit = {
    import java.nio.file.Files
    val path = Files.createTempFile("smile-plot-", ".html")
    path.toFile.deleteOnExit()
    Files.write(path, spec.embed.getBytes(java.nio.charset.StandardCharsets.UTF_8))
    java.awt.Desktop.getDesktop.browse(path.toUri)
  }

  /** Apache Zeppelin Notebook renderer of plot canvas. */
  implicit def zeppelin(canvas: Canvas): Unit = {
    print(s"%html ${smile.plot.swing.canvas2HtmlImg(canvas)}")
  }

  /** Apache Zeppelin Notebook renderer of plot grid. */
  implicit def zeppelin(canvas: PlotGrid): Unit = {
    print(s"%html ${smile.plot.swing.swing2HtmlImg(canvas)}")
  }

  /** Apache Zeppelin Notebook renderer of vega-lite plot. */
  implicit def zeppelin(spec: VegaLite): Unit = {
    print(s"%html ${spec.iframe()}")
  }
  /*
  /** Apache Toree Notebook renderer of plot canvas. */
  implicit def toree(canvas: Canvas): Unit = {
    kernel.display.content("text/html", smile.plot.swing.canvas2HtmlImg(canvas))
  }

  /** Apache Toree Notebook renderer of plot grid. */
  implicit def toree(canvas: PlotGrid): Unit = {
    kernel.display.content("text/html", smile.plot.swing.swing2HtmlImg(canvas))
  }

  /** Apache Toree Notebook renderer of vega-lite plot. */
  implicit def toree(spec: VegaLite): Unit = {
    kernel.display.content("text/html", spec.iframe())
  }

  /** Jupyter Notebook (Almond) renderer of plot canvas. */
  implicit def almond(canvas: Canvas): Unit = {
    publish.html(smile.plot.swing.canvas2HtmlImg(canvas))
  }

  /** Jupyter Notebook (Almond) renderer of plot grid. */
  implicit def almond(canvas: PlotGrid): Unit = {
    publish.html(smile.plot.swing.swing2HtmlImg(canvas))
  }

  /** Jupyter Notebook (Almond) renderer of vega-lite plot. */
  implicit def almond(spec: VegaLite): Unit = {
    publish.html(spec.iframe())
  }
  */
}
