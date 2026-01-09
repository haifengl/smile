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
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import javax.swing.*;
import smile.swing.FileChooser;
import smile.swing.Printer;
import static smile.swing.SmileUtilities.scaleImageIcon;

/**
 * Printable scene of mathematical plots.
 *
 * @author Haifeng Li
 */
public interface Scene extends Printable {
    /** The number of created windows, as the default window title. */
    AtomicInteger WindowCount = new AtomicInteger();

    /**
     * Returns the content component.
     * @return the content component.
     */
    JComponent content();

    /**
     * Returns a toolbar to control the plot.
     * @return a toolbar to control the plot.
     */
    JToolBar toolbar();

    /**
     * Returns the save action.
     * @return the save action.
     */
    default Action saveAction() {
        return new SaveAction(this);
    }

    /**
     * Returns the print action.
     * @return the print action.
     */
    default Action printAction() {
        return new PrintAction(this);
    }

    /**
     * Action to save the scene to an image file.
     */
    class SaveAction extends AbstractAction {
        static final ImageIcon icon = new ImageIcon(Objects.requireNonNull(Canvas.class.getResource("images/save.png")));
        static final ImageIcon icon16 = scaleImageIcon(icon, 16);
        static final ImageIcon icon24 = scaleImageIcon(icon, 24);
        /** The scene to save. */
        private final Scene scene;

        /**
         * Constructor.
         * @param scene the scene to save.
         */
        public SaveAction(Scene scene) {
            super("Save", icon16);
            putValue(LARGE_ICON_KEY, icon24);
            this.scene = scene;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            try {
                scene.save();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(null, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Action to print the scene.
     */
    class PrintAction extends AbstractAction {
        static final ImageIcon icon = new ImageIcon(Objects.requireNonNull(Canvas.class.getResource("images/print.png")));
        static final ImageIcon icon16 = scaleImageIcon(icon, 16);
        static final ImageIcon icon24 = scaleImageIcon(icon, 24);
        /** The scene to print. */
        private final Scene scene;

        /**
         * Constructor.
         * @param scene the scene to print.
         */
        public PrintAction(Scene scene) {
            super("Print", icon16);
            putValue(LARGE_ICON_KEY, icon24);
            this.scene = scene;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            scene.print();
        }
    }

    @Override
    default int print(Graphics g, PageFormat pf, int page) {
        if (page > 0) {
            // We have only one page, and 'page' is zero-based
            return NO_SUCH_PAGE;
        }

        Graphics2D g2d = (Graphics2D) g;
        JComponent c = content();

        // User (0,0) is typically outside the imageable area, so we must
        // translate by the X and Y values in the PageFormat to avoid clipping
        g2d.translate(pf.getImageableX(), pf.getImageableY());

        // Scale plots to paper size.
        double scaleX = pf.getImageableWidth() / c.getWidth();
        double scaleY = pf.getImageableHeight() / c.getHeight();
        g2d.scale(scaleX, scaleY);

        // Disable double buffering
        RepaintManager currentManager = RepaintManager.currentManager(c);
        currentManager.setDoubleBufferingEnabled(false);

        // Now we perform our rendering
        c.printAll(g);

        // Enable double buffering
        currentManager.setDoubleBufferingEnabled(true);

        // tell the caller that this page is part of the printed document
        return PAGE_EXISTS;
    }

    /**
     * Shows a file chooser and exports the scene to the selected image file.
     * @throws IOException if an error occurs during writing.
     */
    default void save() throws IOException {
        JFileChooser fc = FileChooser.getInstance();
        fc.setFileFilter(FileChooser.SimpleFileFilter.getWritableImageFilter());
        fc.setAcceptAllFileFilterUsed(false);
        fc.setSelectedFiles(new File[0]);

        int returnVal = fc.showSaveDialog(content());
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            save(file);
        }
    }

    /**
     * Exports the scene to an image file.
     * @param file the destination file.
     * @throws IOException if an error occurs during writing.
     */
    default void save(File file) throws IOException {
        JComponent c = content();
        BufferedImage bi = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = bi.createGraphics();
        c.printAll(g2d);
        ImageIO.write(bi, FileChooser.getExtension(file), file);
    }

    /**
     * Exports the scene to an image.
     * @return the image of scene.
     */
    default Image toImage() {
        JComponent c = content();
        BufferedImage image = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        c.printAll(g2d);
        return image;
    }

    /**
     * Prints the scene.
     */
    default void print() {
        Printer.getPrinter().print(this);
    }

    /**
     * Shows the scene in a window.
     * @return a new JFrame that contains the scene.
     */
    default JFrame window() {
        String figureTitle = null;
        if (content() instanceof Canvas canvas) {
            figureTitle = canvas.figure().getTitle();
        }

        JFrame frame = new JFrame();
        String title = figureTitle != null ? figureTitle :
                String.format("Smile Plot %d", WindowCount.addAndGet(1));
        javax.swing.SwingUtilities.invokeLater(() -> {
            JScrollPane scrollPane = new JScrollPane(content());
            JPanel contentPane = new JPanel(new BorderLayout());
            contentPane.add(scrollPane, BorderLayout.CENTER);
            contentPane.add(toolbar(), BorderLayout.NORTH);

            frame.setTitle(title);
            frame.setContentPane(contentPane);
            frame.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
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
}
