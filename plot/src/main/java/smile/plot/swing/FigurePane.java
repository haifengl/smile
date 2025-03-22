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
package smile.plot.swing;

import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;

/**
 * Interactive pane for mathematical plots. For both 2D and 3D plot,
 * the user can zoom in/out by mouse wheel. For 2D plot, the user can
 * shift the coordinates by moving mouse after double click. The user
 * can also select an area by mouse for detailed view. For 3D plot,
 * the user can rotate the view by dragging mouse.
 *
 * @author Haifeng Li
 */
public class FigurePane extends JPanel {
    /** The number of created windows, as the default window title. */
    static final AtomicInteger WindowCount = new AtomicInteger();
    /** The plot figure. */
    private final Figure figure;
    /** The Swing component to draw the figure. */
    private final Canvas canvas;

    /**
     * Constructor
     * @param figure The plot figure.
     */
    public FigurePane(Figure figure) {
        super(new BorderLayout());
        this.figure = figure;
        this.canvas = new Canvas(figure);
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
     * @return a new JFrame that contains the figure pane.
     */
    public JFrame window() throws InterruptedException, InvocationTargetException  {
        JFrame frame = new JFrame();
        String title = figure.getTitle();
        if (title != null) {
            title = String.format("Smile Plot %d", WindowCount.addAndGet(1));
        }
        frame.setTitle(title);
        frame.getContentPane().add(this);

        frame.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        frame.setSize(new java.awt.Dimension(1280, 800));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        javax.swing.SwingUtilities.invokeAndWait(() -> {
            canvas.reset();
            repaint();
            frame.toFront();
        });

        return frame;
    }
}
