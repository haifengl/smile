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

import java.awt.event.WindowEvent
import javax.swing.JFrame

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
  def apply(canvas: PlotCanvas): Window = {
    Window(canvas.window, canvas)
  }
}