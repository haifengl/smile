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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.LayoutManager;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import javax.imageio.ImageIO;
import javax.swing.*;
import smile.data.DataFrame;
import smile.swing.Button;
import smile.swing.FileChooser;
import smile.swing.Printer;

/**
 * Interactive pane for multiple mathematical plots.
 *
 * @author Haifeng Li
 */
public class MultiFigurePane extends JPanel implements Printable {
    /**
     * Optional toolbar to control plots.
     */
    private final JToolBar toolbar = new JToolBar();

    /**
     * Constructor.
     * @param layout the layout manager of plot content pane.
     */
    public MultiFigurePane(LayoutManager layout) {
        super(layout, true);
        setBackground(Color.WHITE);
        initToolBar();
    }

    /**
     * Constructor with GridLayout.
     * @param nrow the number of rows.
     * @param ncol the number of columns.
     */
    public MultiFigurePane(int nrow, int ncol) {
        this(grid(nrow, ncol));
    }

    /**
     * Constructor with GridLayout.
     * @param plots the plots to add into the frame.
     */
    public MultiFigurePane(Canvas... plots) {
        this(grid(plots.length));
        for (var plot : plots) {
            add(plot);
        }
    }

    /**
     * Returns a grid layout manager.
     * @param size the number of plots.
     */
    private static LayoutManager grid(int size) {
        int n = (int) Math.ceil(Math.sqrt(size));
        if (n < 1) n = 1;
        return grid(n, n);
    }

    /**
     * Returns a grid layout manager.
     * @param nrow the number of rows.
     * @param ncol the number of columns.
     */
    private static LayoutManager grid(int nrow, int ncol) {
        return new GridLayout(nrow, ncol, 0, 0);
    }

    /**
     * Returns a toolbar to control the plot.
     * @return a toolbar to control the plot.
     */
    public JToolBar toolbar() {
        return toolbar;
    }

    /**
     * Toolbar button actions.
     */
    private transient final Action saveAction = new SaveAction();
    private transient final Action printAction = new PrintAction();

    /**
     * Initialize toolbar.
     */
    private void initToolBar() {
        toolbar.add(new Button(saveAction));
        toolbar.add(new Button(printAction));
    }

    private class SaveAction extends AbstractAction {

        public SaveAction() {
            super("Save", new ImageIcon(Objects.requireNonNull(MultiFigurePane.class.getResource("images/save16.png"))));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            try {
                save();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(null, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private class PrintAction extends AbstractAction {

        public PrintAction() {
            super("Print", new ImageIcon(Objects.requireNonNull(MultiFigurePane.class.getResource("images/print16.png"))));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            print();
        }
    }

    @Override
    public int print(java.awt.Graphics g, PageFormat pf, int page) {
        if (page > 0) {
            // We have only one page, and 'page' is zero-based
            return NO_SUCH_PAGE;
        }

        Graphics2D g2d = (Graphics2D) g;
        
        // User (0,0) is typically outside the imageable area, so we must
        // translate by the X and Y values in the PageFormat to avoid clipping
        g2d.translate(pf.getImageableX(), pf.getImageableY());

        // Scale plots to paper size.
        double scaleX = pf.getImageableWidth() / getWidth();
        double scaleY = pf.getImageableHeight() / getHeight();
        g2d.scale(scaleX, scaleY);

        // Disable double buffering
        RepaintManager currentManager = RepaintManager.currentManager(this);
        currentManager.setDoubleBufferingEnabled(false);

        // Now we perform our rendering
        printAll(g);

        // Enable double buffering
        currentManager.setDoubleBufferingEnabled(true);

        // tell the caller that this page is part of the printed document
        return PAGE_EXISTS;
    }        
    
    /**
     * Shows a file chooser and exports the plot to the selected image file.
     * @throws IOException if an error occurs during writing.
     */
    public void save() throws IOException {
        JFileChooser fc = FileChooser.getInstance();
        fc.setFileFilter(FileChooser.SimpleFileFilter.getWritableImageFIlter());
        fc.setAcceptAllFileFilterUsed(false);
        fc.setSelectedFiles(new File[0]);
        int returnVal = fc.showSaveDialog(this);

        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            save(file);
        }        
    }
    
    /**
     * Exports the plot to an image file.
     * @param file the destination file.
     * @throws IOException if an error occurs during writing.
     */
    public void save(File file) throws IOException {
        BufferedImage bi = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = bi.createGraphics();
        printAll(g2d);

        ImageIO.write(bi, FileChooser.getExtension(file), file);
    }

    /**
     * Prints the plot.
     */
    public void print() {
        Printer.getPrinter().print(this);
    }

    /**
     * Shows the plot group in a window.
     * @return a new JFrame that contains the plot group.
     */
    public JFrame window() throws InterruptedException, InvocationTargetException {
        JFrame frame = new JFrame();
        String title = String.format("Smile Plot %d", FigurePane.WindowCount.addAndGet(1));
        frame.setTitle(title);

        JPanel pane = new JPanel(new BorderLayout());
        pane.add(this, BorderLayout.CENTER);
        pane.add(toolbar, BorderLayout.NORTH);

        frame.getContentPane().add(pane);
        frame.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        frame.setSize(new java.awt.Dimension(1280, 1000));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        javax.swing.SwingUtilities.invokeAndWait(() -> {
            frame.toFront();
            frame.repaint();
        });

        return frame;
    }

    /**
     * Scatterplot Matrix (SPLOM).
     * @param data the data frame.
     */
    public static MultiFigurePane splom(DataFrame data, char mark, Color color) {
        String[] columns = data.names();
        int p = columns.length;
        MultiFigurePane grid = new MultiFigurePane(p, p);
        for (int i = p; i-- > 0;) {
            for (String column : columns) {
                Figure figure = ScatterPlot.of(data, column, columns[i], mark, color).canvas();
                figure.setAxisLabels(column, columns[i]);
                grid.add(new Canvas(figure));
            }
        }

        return grid;
    }

    /**
     * Scatterplot Matrix (SPLOM).
     * @param data the data frame.
     * @param category the category column for coloring.
     */
    public static MultiFigurePane splom(DataFrame data, char mark, String category) {
        int clazz = data.schema().indexOf(category);
        String[] columns = data.names();
        int p = columns.length;
        MultiFigurePane grid = new MultiFigurePane(p, p);
        for (int i = p; i-- > 0;) {
            if (i == clazz) continue;
            for (int j = 0; j < p; j++) {
                if (j == clazz) continue;
                Figure figure = ScatterPlot.of(data, columns[j], columns[i], category, mark).canvas();
                figure.setLegendVisible(false);
                figure.setAxisLabels(columns[j], columns[i]);
                grid.add(new Canvas(figure));
            }
        }

        return grid;
    }
}
