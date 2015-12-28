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

  /** Create a window/JFrame */
  def window(title: String = "SMILE"): JFrame = {
    val frame = new JFrame(title)
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
   * @param title plot and window title
   *
   * @return a plot canvas which can be added other shapes.
   */
  def plot(data: Array[Array[Double]], legend: Char = '*', color: Color = Color.RED, title: String = "Scatter Plot"): PlotCanvas = {

    val canvas = ScatterPlot.plot(data, legend, color)
    canvas.setTitle(title)
    val win = window(title)
    win.add(canvas)
    canvas
  }
}
