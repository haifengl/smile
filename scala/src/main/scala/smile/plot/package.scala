/*******************************************************************************
 * (C) Copyright 2015 Haifeng Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile

import java.awt.{Dimension, Color}
import javax.swing.{JFrame, WindowConstants}

/**
 * Graphics & plotting shell commands.
 *
 * @author Haifeng Li
 */
package object plot {

  val windowCount = new java.util.concurrent.atomic.AtomicInteger

  /** Create a window/JFrame */
  def window(title: String = ""): JFrame = {
    val t = if (title.isEmpty) { "SMILE Plot " + windowCount.addAndGet(1) } else title
    val frame = new JFrame(t)
    frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE)
    frame.setSize(new Dimension(1000, 1000))
    frame.setLocationRelativeTo(null)
    frame.setVisible(true)
    frame
  }

  /**
   * Scatter plot.
   *
   * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
   * @param color the color used to draw points.
   * @param legend the legend used to draw points.
   *               <ul>
   *               <li> . : dot
   *               <li> + : +
   *               <li> - : -
   *               <li> | : |
   *               <li> * : star
   *               <li> x : x
   *               <li> o : circle
   *               <li> O : large circle
   *               <li> @ : solid circle
   *               <li> # : large solid circle
   *               <li> s : square
   *               <li> S : large square
   *               <li> q : solid square
   *               <li> Q : large solid square
   *               <li> others : dot
   *               </ul>
   *
   * @return a tuple of window frame and plot canvas which can be added other shapes.
   */
  def plot(data: Array[Array[Double]], legend: Char = '*', color: Color = Color.BLACK): (JFrame, PlotCanvas) = {
    val canvas = ScatterPlot.plot(data, legend, color)

    val win = window()
    win.add(canvas)

    (win, canvas)
  }

  /**
   * Scatter plot.
   *
   * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
   * @param labels labels of points.
   *
   * @return a tuple of window frame and plot canvas which can be added other shapes.
   */
  def plot(data: Array[Array[Double]], labels: Array[String]): (JFrame, PlotCanvas) = {
    val canvas = ScatterPlot.plot(data, labels)

    val win = window()
    win.add(canvas)

    (win, canvas)
  }

  /**
   * Scatter plot.
   *
   * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
   * @param label the class labels of data.
   * @param legend the legend for each class.
   * @param palette the colors for each class.
   *
   * @return a tuple of window frame and plot canvas which can be added other shapes.
   */
  def plot(data: Array[Array[Double]], label: Array[Int], legend: Array[Char], palette: Array[Color]): (JFrame, PlotCanvas) = {
    val canvas = ScatterPlot.plot(data, label, legend, palette)

    val win = window()
    win.add(canvas)

    (win, canvas)
  }

  /**
   * Scatter plot which connects points by straight lines.
   *
   * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
   * @param style the stroke style of line.
   * @param legend the legend used to draw data points. The default value ' ' makes the point indistinguishable
   *               from the line on purpose.
   * @param color the color of line.
   *
   * @return a plot canvas which can be added other shapes.
   */
  def line(data: Array[Array[Double]], style: Line.Style = Line.Style.SOLID, color: Color = Color.BLACK, legend: Char = ' '): (JFrame, PlotCanvas) = {
    val canvas = LinePlot.plot(data, style, color)

    if (legend != ' ') {
      val scatter = new ScatterPlot(data, legend)
      scatter.setColor(color)
      canvas.add(scatter)
    }

    val win = window()
    win.add(canvas)

    (win, canvas)
  }
}
