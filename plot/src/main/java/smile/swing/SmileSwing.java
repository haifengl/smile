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
import smile.data.DataFrame;
import smile.plot.swing.*;
import smile.swing.table.DataFrameTableModel;
import smile.swing.table.MatrixTableModel;
import smile.tensor.Matrix;


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
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
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
     */
    static JFrame show(Figure figure) {
        var pane = new FigurePane(figure);
        return pane.window();
    }

    /**
     * Shows the figure in a window.
     * @param figure the figure to display.
     * @return a new JFrame that contains the figure.
     */
    static JFrame show(MultiFigurePane figure) {
        return figure.window();
    }

    /**
     * Shows the data frame in a window.
     * @param df the data frame to display.
     * @return a new JFrame that displays the matrix in a table.
     */
    static JFrame show(DataFrame df) {
        DataFrameTableModel model = new DataFrameTableModel(df);
        Table table = new Table(model);
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setRowHeaderView(table.getRowHeader());
        JPanel contentPane = new JPanel(new BorderLayout());
        contentPane.add(model.getToolbar(), BorderLayout.NORTH);
        contentPane.add(scrollPane, BorderLayout.CENTER);

        JFrame frame = new JFrame();
        frame.setContentPane(contentPane);
        frame.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        frame.setSize(new java.awt.Dimension(1280, 1000));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        javax.swing.SwingUtilities.invokeLater(() -> {
            frame.toFront();
            frame.repaint();
        });

        return frame;
    }

    /**
     * Shows the matrix in a window.
     * @param matrix the matrix to display.
     * @return a new JFrame that displays the matrix in a table.
     */
    static JFrame show(Matrix matrix) {
        MatrixTableModel model = new MatrixTableModel(matrix);
        Table table = new Table(model);
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setRowHeaderView(table.getRowHeader());
        JPanel contentPane = new JPanel(new BorderLayout());
        contentPane.add(model.getToolbar(), BorderLayout.NORTH);
        contentPane.add(scrollPane, BorderLayout.CENTER);

        JFrame frame = new JFrame();
        frame.setContentPane(contentPane);
        frame.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        frame.setSize(new java.awt.Dimension(1280, 1000));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        javax.swing.SwingUtilities.invokeLater(() -> {
            frame.toFront();
            frame.repaint();
        });

        return frame;
    }
}
