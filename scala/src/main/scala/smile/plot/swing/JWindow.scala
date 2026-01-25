/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.plot.swing

import java.awt.event.WindowEvent
import javax.swing.JFrame

/** JFrame window. */
trait JWindow {
  val frame: JFrame

  /** Closes the window programmatically. */
  def close(): Unit = {
    frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING))
  }
}

/** Plot canvas window. */
case class CanvasWindow(override val frame: JFrame, canvas: Canvas) extends JWindow

/** Plot grid window. */
case class MultiFigureWindow(override val frame: JFrame, canvas: MultiFigurePane) extends JWindow

object JWindow {
  /** Opens a plot window. */
  def apply(canvas: Canvas): CanvasWindow = {
    CanvasWindow(canvas.window, canvas)
  }

  /** Opens a plot grid window. */
  def apply(canvas: MultiFigurePane): MultiFigureWindow = {
    MultiFigureWindow(canvas.window, canvas)
  }
}