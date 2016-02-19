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

package smile.plot

import java.awt.{GridLayout, Dimension}
import java.awt.event.WindowEvent
import javax.swing.{JFrame, JPanel, WindowConstants}

case class Window(frame: JFrame, canvas: PlotCanvas) {
  def close: Unit = {
    frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING))
  }
}

object Window {
  private val windowCount = new java.util.concurrent.atomic.AtomicInteger

  def apply(canvas: PlotCanvas, title: String = ""): Window = {
    val jframe = frame(title)
    jframe.add(canvas)

    java.awt.EventQueue.invokeLater(new Runnable() {
      override def run() {
        jframe.toFront()
        jframe.repaint()
      }
    })

    Window(jframe, canvas)
  }

  /** Create a plot window frame. */
  def frame(title: String = ""): JFrame = {
    val frameTitle = if (title.isEmpty) "Smile Plot " + windowCount.addAndGet(1) else title
    val frame = new JFrame(frameTitle)
    frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE)
    frame.getContentPane.add(new JPanel(new GridLayout(4, 4)))
    frame.setSize(new Dimension(1000, 1000))
    frame.setLocationRelativeTo(null)
    frame.setVisible(true)
    frame
  }
}