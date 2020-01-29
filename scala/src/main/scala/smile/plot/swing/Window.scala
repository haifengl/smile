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

import java.awt.{Dimension, GridLayout}
import java.awt.event.WindowEvent

import javax.swing.{JComponent, JFrame, JPanel, SwingUtilities, WindowConstants}

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

  /** Create a window frame. */
  def apply(canvas: JComponent): JFrame = {
    val jframe = frame()
    jframe.add(canvas)

    SwingUtilities.invokeAndWait(() => {
      jframe.toFront()
      jframe.repaint()
    })

    jframe
  }

  /** Create a plot window frame. */
  def apply(canvas: PlotCanvas): Window = {
    val title = Option(canvas.getTitle)
    val jframe = frame(title)
    jframe.add(canvas)

    SwingUtilities.invokeAndWait(() => {
      canvas.reset()
      canvas.repaint()
      jframe.toFront()
    })

    Window(jframe, canvas)
  }

  /** Create a plot window frame. */
  def frame(title: Option[String] = None): JFrame = {
    val frame = new JFrame(title.getOrElse("Smile Plot " + windowCount.addAndGet(1)))
    frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE)
    frame.getContentPane.add(new JPanel(new GridLayout(4, 4)))
    frame.setSize(new Dimension(1000, 1000))
    frame.setLocationRelativeTo(null)
    frame.setVisible(true)
    frame
  }
}