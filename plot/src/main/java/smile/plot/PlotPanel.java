/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile.plot;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.io.File;
import java.io.IOException;
import java.net.URL;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JToolBar;
import javax.swing.RepaintManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import smile.swing.FileChooser;
import smile.swing.Printer;

/**
 * PlotPanel is a simple JPanel to contain PlotCanvas objects. The PlotPanel
 * will organize the plots in a grid layout.
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class PlotPanel extends JPanel implements ActionListener, Printable {
    private static final Logger logger = LoggerFactory.getLogger(PlotPanel.class);

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
     * @param plot the plot to add into the frame.
     */
    public PlotPanel(PlotCanvas plot) {
        init();
        contentPane.add(plot);
        organize();
    }

    /**
     * Constructor.
     * @param plots the plots to add into the frame.
     */
    public PlotPanel(PlotCanvas... plots) {
        init();
        for (PlotCanvas plot : plots) {
            contentPane.add(plot);
        }
        organize();
    }

    /**
     * Initialization.
     */
    private void init() {
        setLayout(new BorderLayout());
        initToolBar();

        contentPane = new JPanel();
        contentPane.setLayout(new GridLayout(0, 1, 0, 0));
        contentPane.setBackground(Color.WHITE);
        add(contentPane, BorderLayout.CENTER);
    }
    
    /**
     * Reorganize the plots in the frame. Basically, it reset the surface layout
     * based on the number of plots in the frame.
     */
    private void organize() {
        int m = (int) Math.sqrt(contentPane.getComponentCount());
        if (m <= 0) m = 1;
        contentPane.setLayout(new GridLayout(m, 0, 0, 0));
    }

    /**
     * Add a plot into the frame.
     */
    public void add(PlotCanvas plot) {
        contentPane.add(plot);
        organize();
    }

    /**
     * Remove a plot from the frame.
     */
    public void remove(PlotCanvas plot) {
        contentPane.remove(plot);
        organize();
    }
    
    /**
     * Initialize toolbar.
     */
    private void initToolBar() {
        toolbar = new JToolBar(JToolBar.VERTICAL);
        toolbar.setFloatable(false);
        add(toolbar, BorderLayout.WEST);
        
        JButton button = makeButton("save", SAVE, "Save", "Save");
        toolbar.add(button);

        button = makeButton("print", PRINT, "Print", "Print");
        toolbar.add(button);
    }
    
    /**
     * Creates a button for toolbar.
     */
    private JButton makeButton(String imageName, String actionCommand, String toolTipText, String altText) {
        //Look for the image.
        String imgLocation = "images/" + imageName + "16.png";
        URL imageURL = PlotCanvas.class.getResource(imgLocation);

        //Create and initialize the button.
        JButton button = new JButton();
        button.setActionCommand(actionCommand);
        button.setToolTipText(toolTipText);
        button.addActionListener(this);

        if (imageURL != null) {                      //image found
            button.setIcon(new ImageIcon(imageURL, altText));
        } else {                                     //no image found
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
}