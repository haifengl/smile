/*
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
 */

package smile.plot.swing

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO
import javax.swing.JComponent
import javax.swing.SwingUtilities

/** HTML `<img>` tag of Canvas and JComponent.
  *
  * @author Haifeng Li
  */
object Html {
  /** Returns the HTML img tag of the canvas encoded by BASE64. */
  def canvas(canvas: Canvas, width: Int = 600, height: Int = 600): String = {
    val bi = canvas.toBufferedImage(width, height)

    val os = new ByteArrayOutputStream
    ImageIO.write(bi, "png", os)
    val base64 = Base64.getEncoder.encodeToString(os.toByteArray)

    s"""<img src="data:image/png;base64,$base64">"""
  }

  /** Returns the HTML img tag of the swing component encoded by BASE64. */
  def of(canvas: JComponent, width: Int = 600, height: Int = 600): String = {
    val headless = new Headless(canvas, width, height)
    headless.pack()
    headless.setVisible(true)
    SwingUtilities.invokeAndWait(() => {})

    val bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val g2d = bi.createGraphics
    canvas.print(g2d)

    val os = new ByteArrayOutputStream
    ImageIO.write(bi, "png", os)
    val base64 = Base64.getEncoder.encodeToString(os.toByteArray)

    s"""<img src="data:image/png;base64,$base64">"""
  }
}
