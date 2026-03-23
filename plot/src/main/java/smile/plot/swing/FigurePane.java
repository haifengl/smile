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
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.plot.swing;

import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.lang.reflect.InvocationTargetException;
import javax.swing.*;

/**
 * The Swing container of a figure with toolbar.
 *
 * @author Haifeng Li
 */
public class FigurePane extends JPanel {
    /** The Swing component to draw the figure. */
    private final Canvas canvas;

    /**
     * Constructor.
     * @param figure The plot figure.
     */
    public FigurePane(Figure figure) {
        super(new BorderLayout());
        canvas = new Canvas(figure);
        add(canvas, BorderLayout.CENTER);
        add(canvas.toolbar(), BorderLayout.NORTH);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                canvas.reset();
                repaint();
            }

            @Override
            public void componentShown(ComponentEvent e) {
                canvas.reset();
                repaint();
            }
        });
    }

    /**
     * Shows the figure in a window.
     * @return a new JFrame that contains the figure.
     */
    public JFrame window() {
        return canvas.window();
    }
}
