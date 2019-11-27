/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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
 *******************************************************************************/

package smile.plot.swing

import java.awt.{GridLayout, Dimension}
import java.awt.event.WindowEvent
import javax.swing.{JFrame, JPanel, WindowConstants}

/** A window/JFrame with plot canvas. */
case class Window(frame: JFrame, canvas: PlotCanvas) {
  /** Closes the window programmatically. */
  def close: Unit = {
    frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING))
  }
}

object Window {
  /** The number of created windows, as the default window title. */
  private val windowCount = new java.util.concurrent.atomic.AtomicInteger

  /** Create a plot window frame. */
  def apply(canvas: PlotCanvas, title: String = ""): Window = {
    val jframe = frame(title)
    jframe.add(canvas)

    java.awt.EventQueue.invokeLater(() => {
        jframe.toFront()
        jframe.repaint()
      }
    )

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