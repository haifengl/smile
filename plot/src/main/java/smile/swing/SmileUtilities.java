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
package smile.swing;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.List;
import smile.data.DataFrame;
import smile.plot.swing.*;
import smile.swing.table.DataFrameTableModel;
import smile.swing.table.MatrixTableModel;
import smile.tensor.Matrix;
import smile.tensor.SparseMatrix;


/**
 * A collection of utility methods primarily for performing common GUI-related tasks.
 *
 * @author Haifeng Li
 */
public interface SmileUtilities {
    /** The icon size Swing uses for menu items and small toolbar buttons. */
    int SMALL_ICON_SIZE = 16;
    /** The icon size Swing uses for large toolbar buttons. */
    int LARGE_ICON_SIZE = 24;
    /**
     * Standard display scale factors from 100% (1×) to 400% (4×), covering
     * Windows fractional High-DPI settings (125%, 150%, 175%, 225%, 250%) as
     * well as 2×, 3×, and 4× Retina/4K displays.
     */
    double[] DPI_SCALES = {1.0, 1.25, 1.5, 1.75, 2.0, 2.25, 2.5, 3.0, 4.0};
    /**
     * The icon sizes a window frame or dialog asks for, from the title bar (16)
     * across standard DPI scale factors up to the task switcher and dock (256).
     */
    List<Integer> FRAME_ICON_SIZES = List.of(
            16, 20, 24, 28, 32, 36, 40, 48, 56, 64, 72, 80, 96, 128, 256);

    /**
     * Returns the line number given a text offset.
     * @param editor the text component.
     * @param offset the text offset.
     * @return the line number.
     */
    static int getLineOfOffset(JTextComponent editor, int offset) {
        return editor.getDocument().getDefaultRootElement().getElementIndex(offset);
    }

    /**
     * Returns the start offset of a line.
     * @param editor the text component.
     * @param line the line number.
     * @return the start offset.
     */
    static int getOffsetOfLine(JTextComponent editor, int line) {
        return editor.getDocument().getDefaultRootElement().getElement(line).getStartOffset();
    }

    /**
     * Returns the word ending at the text offset.
     * @param editor the text component.
     * @param offset the text offset.
     * @return the word ending at the text offset.
     * @throws BadLocationException if the offset is invalid.
     */
    static String getWordAt(JTextComponent editor, int offset) throws BadLocationException {
        int line = getLineOfOffset(editor, offset);
        int start = getOffsetOfLine(editor, line);
        String text = editor.getText(start, offset - start);
        String[] words = text.trim().split("[.\\s]+");
        if (words.length > 0) {
            return words[words.length - 1];
        }
        return "";
    }

    /**
     * Scales an image icon to fit within the desired size, preserving the source
     * aspect ratio. A non-square source keeps its proportions (for example a
     * 64×32 source scaled to 16 becomes 16×8) instead of being stretched to a
     * square.
     *
     * <p>Downscaling is done in progressive halving steps. A single bicubic step
     * from a large source (for example a 512px PNG) straight to 16px aliases
     * badly and makes thin strokes shimmer; halving until the image is within
     * twice the target, then taking the final step, keeps the detail.
     *
     * <p>The returned icon carries resolution variants for {@link #DPI_SCALES}
     * (1×, 1.25×, 1.5×, 1.75×, 2×, 2.25×, 2.5×, 3×, 4×), so Swing picks an
     * exact physical-pixel bitmap on fractional High-DPI and Retina displays
     * instead of resampling at paint time.
     *
     * @param icon the input image icon.
     * @param size the desired icon size in logical pixels.
     * @return the scaled image icon.
     * @throws IllegalArgumentException if {@code size} is not positive.
     */
    static ImageIcon scaleImageIcon(ImageIcon icon, int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive, got: " + size);
        }

        Image source = icon.getImage();
        int width = source.getWidth(null);
        int height = source.getHeight(null);
        if (width <= 0 || height <= 0) {
            // The image is not (yet) decoded; nothing to scale.
            return icon;
        }

        Image[] variants = new Image[DPI_SCALES.length];
        for (int i = 0; i < DPI_SCALES.length; i++) {
            double dpiSize = size * DPI_SCALES[i];
            double scale = Math.min(dpiSize / width, dpiSize / height);
            int targetWidth = Math.max(1, (int) Math.round(width * scale));
            int targetHeight = Math.max(1, (int) Math.round(height * scale));
            variants[i] = scale(source, targetWidth, targetHeight);
        }
        return new ImageIcon(new BaseMultiResolutionImage(variants));
    }

    /**
     * Scales an image to the given pixel dimensions, halving progressively when
     * downscaling to avoid the aliasing of a single large reduction.
     *
     * @param source the source image.
     * @param width the target width in pixels.
     * @param height the target height in pixels.
     * @return the scaled image.
     */
    private static BufferedImage scale(Image source, int width, int height) {
        int sourceWidth = source.getWidth(null);
        int sourceHeight = source.getHeight(null);

        Image current = source;
        int currentWidth = sourceWidth;
        int currentHeight = sourceHeight;
        // Halve while the next step would still be a reduction of more than 2×.
        // A 2× step is safe in one pass, so stop before reaching the target to
        // avoid drawing the target size twice.
        while (currentWidth / 2 > width && currentHeight / 2 > height) {
            currentWidth /= 2;
            currentHeight /= 2;
            current = draw(current, currentWidth, currentHeight);
        }

        return draw(current, width, height);
    }

    /**
     * Draws an image into a new ARGB image of the given size.
     *
     * @param source the source image.
     * @param width the target width in pixels.
     * @param height the target height in pixels.
     * @return the new image.
     */
    private static BufferedImage draw(Image source, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2d.drawImage(source, 0, 0, width, height, null);
        } finally {
            g2d.dispose();
        }
        return image;
    }

    /**
     * Loads an icon from a classpath resource, scaled to the given size. A
     * missing or undecodable resource is logged and yields an empty icon rather
     * than an exception: a missing decoration must not take down the menu bar
     * that is being built.
     *
     * @param owner the class whose package the resource path is relative to.
     * @param resource the resource path, for example {@code "images/open.png"}.
     * @param size the desired icon size in logical pixels.
     * @return the icon, or an empty icon when the resource cannot be loaded.
     */
    static ImageIcon loadImageIcon(Class<?> owner, String resource, int size) {
        URL url = owner.getResource(resource);
        if (url == null) {
            System.getLogger(owner.getName()).log(System.Logger.Level.WARNING,
                    "Icon resource not found: {0}", resource);
            return new ImageIcon();
        }

        ImageIcon icon = new ImageIcon(url);
        if (icon.getIconWidth() <= 0 || icon.getIconHeight() <= 0) {
            System.getLogger(owner.getName()).log(System.Logger.Level.WARNING,
                    "Icon resource could not be decoded: {0}", resource);
            return new ImageIcon();
        }

        return scaleImageIcon(icon, size);
    }

    /**
     * Loads the small and large variants of an icon from a classpath resource.
     * The small variant is what Swing shows in menu items and small toolbar
     * buttons; the large one is what it shows in a large toolbar.
     *
     * @param owner the class whose package the resource path is relative to.
     * @param resource the resource path, for example {@code "images/open.png"}.
     * @return the two icon variants.
     */
    static ActionIcons loadActionIcons(Class<?> owner, String resource) {
        return new ActionIcons(
                loadImageIcon(owner, resource, SMALL_ICON_SIZE),
                loadImageIcon(owner, resource, LARGE_ICON_SIZE));
    }

    /**
     * Loads a window frame icon at every size the window system may ask for
     * ({@link #FRAME_ICON_SIZES}), wrapped in a {@link BaseMultiResolutionImage}
     * for {@link java.awt.Window#setIconImage(Image)}. Each variant is scaled
     * with progressive halving, so the small title-bar bitmap stays crisp on
     * fractional High-DPI and Retina displays instead of aliasing from a single
     * large reduction.
     *
     * <p>A missing or undecodable resource is logged and yields {@code null}
     * rather than an exception, so a missing decoration cannot stop the window
     * from being created.
     *
     * @param owner the class whose package the resource path is relative to.
     * @param resource the resource path, for example {@code "images/smile.png"}.
     * @return the multi-resolution frame icon image, or {@code null} when the
     *         resource cannot be loaded.
     */
    static BaseMultiResolutionImage loadFrameIcons(Class<?> owner, String resource) {
        URL url = owner.getResource(resource);
        if (url == null) {
            System.getLogger(owner.getName()).log(System.Logger.Level.WARNING,
                    "Frame icon resource not found: {0}", resource);
            return null;
        }

        ImageIcon icon = new ImageIcon(url);
        int width = icon.getIconWidth();
        int height = icon.getIconHeight();
        if (width <= 0 || height <= 0) {
            System.getLogger(owner.getName()).log(System.Logger.Level.WARNING,
                    "Frame icon resource could not be decoded: {0}", resource);
            return null;
        }

        Image source = icon.getImage();
        Image[] variants = new Image[FRAME_ICON_SIZES.size()];
        for (int i = 0; i < FRAME_ICON_SIZES.size(); i++) {
            int size = FRAME_ICON_SIZES.get(i);
            double scale = Math.min((double) size / width, (double) size / height);
            int targetWidth = Math.max(1, (int) Math.round(width * scale));
            int targetHeight = Math.max(1, (int) Math.round(height * scale));
            variants[i] = scale(source, targetWidth, targetHeight);
        }
        return new BaseMultiResolutionImage(variants);
    }

    /**
     * The small and large icon variants of an action, as Swing asks for them
     * through {@link javax.swing.Action#SMALL_ICON} and
     * {@link javax.swing.Action#LARGE_ICON_KEY}.
     *
     * @param small the small icon, for menu items and small toolbar buttons.
     * @param large the large icon, for a large toolbar.
     */
    record ActionIcons(ImageIcon small, ImageIcon large) {
        /**
         * Applies both variants to an action.
         *
         * @param action the action to decorate.
         */
        public void applyTo(javax.swing.Action action) {
            action.putValue(javax.swing.Action.SMALL_ICON, small);
            action.putValue(javax.swing.Action.LARGE_ICON_KEY, large);
        }
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
     * @return a new JFrame that displays the data frame in a table.
     */
    static JFrame show(DataFrame df) {
        return show(df, "DataFrame [" + df.nrow() + " × " + df.ncol() + "]");
    }

    /**
     * Shows the data frame in a window.
     * @param df the data frame to display.
     * @param title the title of the window.
     * @return a new JFrame that displays the data frame in a table.
     */
    static JFrame show(DataFrame df, String title) {
        JFrame frame = new JFrame(title);
        frame.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        javax.swing.SwingUtilities.invokeLater(() -> {
            DataFrameTableModel model = new DataFrameTableModel(df);
            Table table = new Table(model);
            JScrollPane scrollPane = new JScrollPane(table);
            scrollPane.setRowHeaderView(table.getRowHeader());
            JPanel contentPane = new JPanel(new BorderLayout());
            contentPane.add(model.getToolbar(), BorderLayout.NORTH);
            contentPane.add(scrollPane, BorderLayout.CENTER);

            frame.setContentPane(contentPane);
            frame.setSize(new java.awt.Dimension(1280, 1000));
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            // manipulating the extended state
            frame.setExtendedState(Frame.NORMAL);
            // temporarily setting setAlwaysOnTop(true) may help.
            frame.setAlwaysOnTop(true);
            frame.toFront();
            frame.requestFocus();
            frame.setAlwaysOnTop(false);
        });

        return frame;
    }

    /**
     * Shows the matrix in a window.
     * @param matrix the matrix to display.
     * @return a new JFrame that displays the matrix in a table.
     */
    static JFrame show(Matrix matrix) {
        return show(matrix, "Matrix [" + matrix.nrow() + " × " + matrix.ncol() + "]");
    }

    /**
     * Shows the matrix in a window.
     * @param matrix the matrix to display.
     * @param title the title of the window.
     * @return a new JFrame that displays the matrix in a table.
     */
    static JFrame show(Matrix matrix, String title) {
        JFrame frame = new JFrame(title);
        frame.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        javax.swing.SwingUtilities.invokeLater(() -> {
            MatrixTableModel model = new MatrixTableModel(matrix);
            Table table = new Table(model);
            JScrollPane scrollPane = new JScrollPane(table);
            scrollPane.setRowHeaderView(table.getRowHeader());
            JPanel contentPane = new JPanel(new BorderLayout());
            contentPane.add(model.getToolbar(), BorderLayout.NORTH);
            contentPane.add(scrollPane, BorderLayout.CENTER);

            frame.setContentPane(contentPane);
            frame.setSize(new java.awt.Dimension(1280, 1000));
            frame.setLocationRelativeTo(null);

            frame.setVisible(true);
            frame.toFront();
            frame.requestFocus();
        });

        return frame;
    }

    /**
     * Shows the sparse matrix structure in a figure window.
     * @param matrix the matrix to display.
     * @return a new JFrame that displays the sparse matrix structure.
     */
    static JFrame show(SparseMatrix matrix) {
        Figure figure = SparseMatrixPlot.of(matrix).figure();
        return show(figure);
    }
}
