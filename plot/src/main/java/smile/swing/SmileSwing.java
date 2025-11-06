/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.swing;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import smile.plot.swing.Figure;
import smile.plot.swing.FigurePane;
import smile.plot.swing.MultiFigurePane;


/**
 * A collection of utility methods primarily for performing common GUI-related tasks.
 *
 * @author Haifeng Li
 */
public interface SmileSwing {
    /**
     * Scales an image icon to desired size.
     * @param icon the input image icon.
     * @param size the desired icon size.
     * @return the scaled image icon.
     */
    static ImageIcon scale(ImageIcon icon, int size) {
        Image image = icon.getImage();
        BufferedImage scaledImage = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = scaledImage.createGraphics();

        try {
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2d.drawImage(image, 0, 0, size, size, null);
        } finally {
            g2d.dispose();
        }
        return new ImageIcon(scaledImage);
    }

    /**
     * Shows the figure in a window.
     * @param figure the figure to display.
     * @return a new JFrame that contains the figure.
     * @throws InterruptedException if we're interrupted while waiting for the event dispatching thread to finish executing.
     * @throws InvocationTargetException if an exception is thrown while showing the frame.
     */
    static JFrame show(Figure figure) throws InterruptedException, InvocationTargetException {
        var pane = new FigurePane(figure);
        return pane.window();
    }

    /**
     * Shows the figure in a window.
     * @param figure the figure to display.
     * @return a new JFrame that contains the figure.
     * @throws InterruptedException if we're interrupted while waiting for the event dispatching thread to finish executing.
     * @throws InvocationTargetException if an exception is thrown while showing the frame.
     */
    static JFrame show(MultiFigurePane figure) throws InterruptedException, InvocationTargetException {
        return figure.window();
    }
}
