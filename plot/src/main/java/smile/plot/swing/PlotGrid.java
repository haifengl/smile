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

package smile.plot.swing;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.LayoutManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.Arrays;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JToolBar;
import javax.swing.RepaintManager;

import smile.data.DataFrame;
import smile.swing.FileChooser;
import smile.swing.Printer;

/**
 * PlotGrid organizes multiple plots in a grid layout.
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class PlotGrid extends JPanel implements ActionListener, Printable {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(PlotGrid.class);

    /**
     * Toolbar command.
     */
    private static final String SAVE = "save";
    /**
     * Toolbar command.
     */
    private static final String PRINT = "print";
    /**
     * The content panel to hold plots.
     */
    private JPanel contentPane;
    /**
     * Optional tool bar to control plots.
     */
    private JToolBar toolbar;

    /**
     * Constructor.
     * @param nrow the number of rows.
     * @param ncol the number of columns.
     */
    public PlotGrid(int nrow, int ncol) {
        init(layout(nrow, ncol));
    }

    /**
     * Constructor.
     * @param plots the plots to add into the frame.
     */
    public PlotGrid(PlotPanel... plots) {
        init(layout(plots.length));
        for (PlotPanel plot : plots) {
            contentPane.add(plot);
        }
    }

    /**
     * Initialization.
     */
    private void init(LayoutManager layout) {
        setLayout(new BorderLayout());
        initToolBar();

        contentPane = new JPanel();
        contentPane.setLayout(layout);
        contentPane.setBackground(Color.WHITE);
        add(contentPane, BorderLayout.CENTER);
    }
    
    /**
     * Returns a layout manager for content pane.
     * @param size the number of plots.
     */
    private LayoutManager layout(int size) {
        int n = (int) Math.ceil(Math.sqrt(size));
        if (n < 1) n = 1;
        return layout(n, n);
    }

    /**
     * Returns a layout manager for content pane.
     * @param nrow the number of rows.
     * @param ncol the number of columns.
     */
    private LayoutManager layout(int nrow, int ncol) {
        return new GridLayout(nrow, ncol, 0, 0);
    }

    /**
     * Add a plot into the frame.
     */
    public void add(PlotPanel plot) {
        contentPane.add(plot);
        contentPane.setLayout(layout(contentPane.getComponentCount()));
    }

    /**
     * Remove a plot from the frame.
     */
    public void remove(PlotPanel plot) {
        contentPane.remove(plot);
        contentPane.setLayout(layout(contentPane.getComponentCount()));
    }
    
    /**
     * Initialize toolbar.
     */
    private void initToolBar() {
        toolbar = new JToolBar();

        JButton button = makeButton("save", SAVE, "Save", "Save");
        toolbar.add(button);

        button = makeButton("print", PRINT, "Print", "Print");
        toolbar.add(button);
    }
    
    /**
     * Creates a button for toolbar.
     */
    private JButton makeButton(String imageName, String actionCommand, String toolTipText, String altText) {
        // Look for the image.
        String imgLocation = "images/" + imageName + "16.png";
        URL imageURL = PlotGrid.class.getResource(imgLocation);

        // Create and initialize the button.
        JButton button = new JButton();
        button.setActionCommand(actionCommand);
        button.setToolTipText(toolTipText);
        button.addActionListener(this);

        if (imageURL != null) {   // image found
            button.setIcon(new ImageIcon(imageURL, altText));
        } else {                  // no image found
            button.setText(altText);
            logger.error("Resource not found: {}", imgLocation);
        }

        return button;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String cmd = e.getActionCommand();
        if (SAVE.equals(cmd)) {
            try {
                save();
            } catch (IOException ex) {
                logger.error("Failed to save the screenshot", ex);
                JOptionPane.showMessageDialog(null, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }

        } else if (PRINT.equals(cmd)) {
            print();
        }
    }

    @Override
    public int print(java.awt.Graphics g, PageFormat pf, int page) throws PrinterException {
        if (page > 0) {
            // We have only one page, and 'page' is zero-based
            return NO_SUCH_PAGE;
        }

        Graphics2D g2d = (Graphics2D) g;
        
        // User (0,0) is typically outside the imageable area, so we must
        // translate by the X and Y values in the PageFormat to avoid clipping
        g2d.translate(pf.getImageableX(), pf.getImageableY());

        // Scale plots to paper size.
        double scaleX = pf.getImageableWidth() / contentPane.getWidth();
        double scaleY = pf.getImageableHeight() / contentPane.getHeight();
        g2d.scale(scaleX, scaleY);

        // Disable double buffering
        RepaintManager currentManager = RepaintManager.currentManager(this);
        currentManager.setDoubleBufferingEnabled(false);

        // Now we perform our rendering
        contentPane.printAll(g);

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
        BufferedImage bi = new BufferedImage(contentPane.getWidth(), contentPane.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = bi.createGraphics();
        contentPane.printAll(g2d);

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
        String title = String.format("Smile Plot %d", PlotPanel.WindowCount.addAndGet(1));
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
    public static PlotGrid splom(DataFrame data, char mark, Color color) {
        String[] columns = data.names();
        int p = columns.length;
        PlotGrid grid = new PlotGrid(p, p);
        for (int i = p; i-- > 0;) {
            for (int j = 0; j < p; j++) {
                Canvas canvas = ScatterPlot.of(data, columns[j], columns[i], mark, color).canvas();
                canvas.setAxisLabels(columns[j], columns[i]);
                grid.add(canvas.panel());
            }
        }

        return grid;
    }

    /**
     * Scatterplot Matrix (SPLOM).
     * @param data the data frame.
     * @param category the category column for coloring.
     */
    public static PlotGrid splom(DataFrame data, char mark, String category) {
        int clazz = data.indexOf(category);
        String[] columns = data.names();
        int p = columns.length;
        PlotGrid grid = new PlotGrid(p, p);
        for (int i = p; i-- > 0;) {
            if (i == clazz) continue;
            for (int j = 0; j < p; j++) {
                if (j == clazz) continue;
                Canvas canvas = ScatterPlot.of(data, columns[j], columns[i], category, mark).canvas();
                canvas.setLegendVisible(false);
                canvas.setAxisLabels(columns[j], columns[i]);
                grid.add(canvas.panel());
            }
        }

        return grid;
    }
}
