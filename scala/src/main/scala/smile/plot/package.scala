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

import javax.swing.JComponent
import scala.language.implicitConversions
import smile.json.JsObject
import smile.plot.swing.{Canvas, PlotGroup}

package object plot {
  /** Shows a swing component with implicit renderer. */
  def show(canvas: JComponent)(implicit renderer: JComponent => Unit): Unit = {
    renderer(canvas)
  }

  /** Shows a swing-based plot with implicit renderer. */
  def show(canvas: Canvas)(implicit renderer: Canvas => Unit): Unit = {
    renderer(canvas)
  }

  /** Shows a vega-based plot with implicit renderer. */
  def show(spec: JsObject)(implicit renderer: JsObject => Unit): Unit = {
    renderer(spec)
  }

  /** Swing component renderer. */
  implicit def desktop(canvas: PlotGroup): Unit = {
    canvas.window
  }

  /** Swing based plot renderer. */
  implicit def desktop(canvas: Canvas): Unit = {
    canvas.window
  }

  /** Vega plot renderer with JavaFX. */
  implicit def javafx(spec: JsObject): Unit = {
    vega.Window(spec)
  }

  /** Swing component renderer in Apache Zeppelin Notebook. */
  implicit def zeppelin(canvas: Canvas): Unit = {
    print(s"%html ${swing.canvas2Image(canvas)}")
  }

  /** Swing component renderer in Apache Zeppelin Notebook. */
  implicit def zeppelin(canvas: JComponent): Unit = {
    print(s"%html ${swing.component2Image(canvas)}")
  }

  /** Vega plot renderer in Apache Zeppelin Notebook. */
  implicit def zeppelin(spec: JsObject): Unit = {
    print(s"%html ${vega.iframe(spec)}")
  }
/*
  /** Swing component renderer in Apache Toree Notebook. */
  implicit def toree(canvas: JComponent): Unit = {
    kernel.display.content("text/html", swing.img(canvas))
  }

  /** Vega plot renderer in Apache Toree Notebook. */
  implicit def toree(spec: JsObject): Unit = {
    kernel.display.content("text/html", iframe(spec))
  }

  /** Swing component renderer in Jupyter-scala (Almond) Notebook. */
  implicit def almond(canvas: JComponent): Unit = {
    publish.html(swing.img(canvas))
  }

  /** Vega plot renderer in Jupyter-scala (Almond) Notebook. */
  implicit def almond(spec: JsObject): Unit = {
    publish.html(iframe(spec))
  }
 */
}
