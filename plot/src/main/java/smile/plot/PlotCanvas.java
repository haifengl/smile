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
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

import smile.math.Math;
import smile.projection.PCA;
import smile.swing.Button;
import smile.swing.FileChooser;
import smile.swing.Printer;
import smile.swing.Table;

/**
 * Canvas for mathematical plots.
 * <p>
 * For both 2D and 3D plot, the user can zoom in/out by mouse wheel. For 2D plot,
 * the user can shift the coordinates by moving mouse after double click. The
 * user can also select an area by mouse for detailed view. For 3D plot, the user
 * can rotate the view by dragging mouse.
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class PlotCanvas extends JPanel {

    /**
     * The default (one side) margin portion.
     */
    private static final double DEFAULT_MARGIN = 0.15;
    /**
     * The default font for rendering the title.
     */
    private static final Font DEFAULT_TITLE_FONT = new Font("Arial", Font.BOLD, 16);
    /**
     * The default color for rendering the title.
     */
    private static final Color DEFAULT_TITLE_COLOR = Color.BLACK;
    /**
     * The current coordinate base.
     */
    Base base;
    /**
     * The graphics object associated with this canvas.
     */
    Graphics graphics;
    /**
     * The portion of the canvas used for margin.
     */
    double margin = DEFAULT_MARGIN;
    /**
     * The main title of plot.
     */
    private String title;
    /**
     * The font for rendering the title.
     */
    private Font titleFont = DEFAULT_TITLE_FONT;
    /**
     * The color for rendering the title.
     */
    private Color titleColor = DEFAULT_TITLE_COLOR;
    /**
     * The coordinate base when the user start dragging the mouse.
     */
    private Base backupBase;
    /**
     * The coordinate grid plot.
     */
    private BaseGrid baseGrid;
    /**
     * The shapes in the canvas, e.g. label, plots, etc.
     */
    private List<Shape> shapes = new ArrayList<>();
    /**
     * The real canvas for plots.
     */
    private MathCanvas canvas;
    /**
     * Optional tool bar to control plots.
     */
    private JToolBar toolbar;
    /**
     * Right-click popup menu.
     */
    private JPopupMenu popup;
    /**
     * Property table.
     */
    private JTable propertyTable;

    /**
     * The real canvas for plotting.
     */
    private class MathCanvas extends JComponent implements Printable, ComponentListener, MouseListener, MouseMotionListener, MouseWheelListener, ActionListener {
        /**
         * If the mouse double clicked.
         */
        private boolean mouseDoubleClicked = false;
        /**
         * The x coordinate (in Java2D coordinate space) of mouse click.
         */
        private int mouseClickX = -1;
        /**
         * The y coordinate (in Java2D coordinate space) of mouse click.
         */
        private int mouseClickY = -1;
        /**
         * The x coordinate (in Java2D coordinate space) of mouse dragging.
         */
        private int mouseDraggingX = -1;
        /**
         * The y coordinate (in Java2D coordinate space) of mouse dragging.
         */
        private int mouseDraggingY = -1;
        
        /**
         * Constructor.
         */
        MathCanvas() {
            init();
        }

        /**
         * Initialize the canvas.
         */
        private void init() {
            setBackground(Color.white);
            setDoubleBuffered(true);
            addComponentListener(this);
            addMouseListener(this);
            addMouseMotionListener(this);
            addMouseWheelListener(this); 
        }
        
        @Override
        public void paintComponent(java.awt.Graphics g) {
            Graphics2D g2d = (Graphics2D) g;
            graphics.setGraphics(g2d);

            Color color = g2d.getColor();
            g2d.setColor(getBackground());
            g2d.fillRect(0, 0, getSize().width, getSize().height);
            g2d.setColor(color);
            baseGrid.paint(graphics);

            // draw plot
            graphics.clip();
            int k = 0;
            // with for-each loop, we will get a ConcurrentModificationException.
            // Use for loop instead.
            for (int i = 0; i < shapes.size(); i++) {
                Shape s = shapes.get(i);
                s.paint(graphics);
                if (s instanceof Plot) {
                    Plot p = (Plot) s;
                    if (p.getID() != null) {
                        k++;
                    }
                }
            }
            graphics.clearClip();

            if (k > 1) {
                Font font = g2d.getFont();
                int x = (int) (getWidth() * (1 - margin) + 20);
                int y = (int) (getHeight() * margin + 50);
                int width = font.getSize();
                int height = font.getSize();

                for (int i = 0; i < shapes.size(); i++) {
                    Shape s = shapes.get(i);
                    if (s instanceof Plot) {
                        Plot p = (Plot) s;
                        if (p.getID() != null) {
                            g2d.setColor(p.getColor());
                            g2d.fillRect(x, y, width, height);
                            g2d.drawRect(x, y, width, height);
                            g2d.drawString(p.getID(), x + 2 * width, y + height);
                            y += 2 * width;
                        }
                    }
                }
            }

            if (title != null) {
                g2d.setFont(titleFont);
                g2d.setColor(titleColor);
                FontMetrics fm = g.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(title)) / 2;
                int y = (int) (getHeight() * margin) / 2;
                g2d.drawString(title, x, y);
            }

            if (mouseDraggingX >= 0 && mouseDraggingY >= 0) {
                g.drawRect(Math.min(mouseClickX, mouseDraggingX),
                        Math.min(mouseClickY, mouseDraggingY),
                        Math.abs(mouseClickX - mouseDraggingX),
                        Math.abs(mouseClickY - mouseDraggingY));
            }

            g2d.setColor(color);
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
            double scaleX = pf.getImageableWidth() / canvas.getWidth();
            double scaleY = pf.getImageableHeight() / canvas.getHeight();
            g2d.scale(scaleX, scaleY);

            // Disable double buffering
            canvas.setDoubleBuffered(false);

            // Now we perform our rendering
            canvas.print(g);

            // Enable double buffering
            canvas.setDoubleBuffered(true);

            // tell the caller that this page is part of the printed document
            return PAGE_EXISTS;
        }

        @Override
        public void mousePressed(MouseEvent e) {
            if (e.isPopupTrigger()) {
                popup.show(e.getComponent(), e.getX(), e.getY());
                e.consume();
            } else {
                mouseClickX = e.getX();
                mouseClickY = e.getY();
                e.consume();
            }
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (base.dimension == 2) {
                mouseDraggingX = e.getX();
                mouseDraggingY = e.getY();
                repaint();

            } else if (base.dimension == 3) {
                graphics.rotate(e.getX() - mouseClickX, e.getY() - mouseClickY);
                mouseClickX = e.getX();
                mouseClickY = e.getY();
                repaint();
            }

            e.consume();
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (e.isPopupTrigger()) {
                popup.show(e.getComponent(), e.getX(), e.getY());
                e.consume();
            } else {
                if (mouseDraggingX != -1 && mouseDraggingY != -1) {
                    mouseDraggingX = -1;
                    mouseDraggingY = -1;

                    if (base.dimension == 2) {
                        if (Math.abs(e.getX() - mouseClickX) > 20 && Math.abs(e.getY() - mouseClickY) > 20) {
                            double[] sc1 = ((Projection2D) (graphics.projection)).inverseProjection(mouseClickX, mouseClickY);
                            double[] sc2 = ((Projection2D) (graphics.projection)).inverseProjection(e.getX(), e.getY());

                            if (Math.min(sc1[0], sc2[0]) < base.upperBound[0]
                                    && Math.max(sc1[0], sc2[0]) > base.lowerBound[0]
                                    && Math.min(sc1[1], sc2[1]) < base.upperBound[1]
                                    && Math.max(sc1[1], sc2[1]) > base.lowerBound[1]) {

                                base.lowerBound[0] = Math.max(base.lowerBound[0], Math.min(sc1[0], sc2[0]));
                                base.upperBound[0] = Math.min(base.upperBound[0], Math.max(sc1[0], sc2[0]));
                                base.lowerBound[1] = Math.max(base.lowerBound[1], Math.min(sc1[1], sc2[1]));
                                base.upperBound[1] = Math.min(base.upperBound[1], Math.max(sc1[1], sc2[1]));

                                for (int i = 0; i < base.dimension; i++) {
                                    base.setPrecisionUnit(i);
                                }
                                base.initBaseCoord();
                                graphics.projection.reset();
                                baseGrid.setBase(base);
                            }
                        }
                    }

                    repaint();
                    e.consume();
                }
            }
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2) {
                mouseDoubleClicked = true;
                backupBase = base;
            } else {
                mouseDoubleClicked = false;
            }

            mouseClickX = e.getX();
            mouseClickY = e.getY();

            e.consume();
        }

        @Override
        public void mouseEntered(MouseEvent e) {
        }

        @Override
        public void mouseExited(MouseEvent e) {
        }

        @Override
        public void actionPerformed(ActionEvent e) {
        }
        
        @Override
        public void mouseMoved(MouseEvent e) {
            if (base.dimension == 2) {
                if (mouseDoubleClicked) {
                    double x = mouseClickX - e.getX();
                    if (Math.abs(x) > 20) {
                        int s = baseGrid.getAxis(0).getLinearSlices();
                        x = x > 0 ? 1.0 / s : -1.0 / s;
                        x *= (backupBase.upperBound[0] - backupBase.lowerBound[0]);
                        base.lowerBound[0] = backupBase.lowerBound[0] + x;
                        base.upperBound[0] = backupBase.upperBound[0] + x;
                        mouseClickX = e.getX();
                    }

                    double y = mouseClickY - e.getY();
                    if (Math.abs(y) > 20) {
                        int s = baseGrid.getAxis(1).getLinearSlices();
                        y = y > 0 ? -1.0 / s : 1.0 / s;
                        y *= (backupBase.upperBound[1] - backupBase.lowerBound[1]);
                        base.lowerBound[1] = backupBase.lowerBound[1] + y;
                        base.upperBound[1] = backupBase.upperBound[1] + y;
                        mouseClickY = e.getY();
                    }

                    base.initBaseCoord();
                    graphics.projection.reset();
                    baseGrid.setBase(base);
                    repaint();
                } else {
                    String tooltip = null;
                    double[] sc = ((Projection2D) (graphics.projection)).inverseProjection(e.getX(), e.getY());

                    String firstid = null;
                    for (Shape shape : shapes) {
                        if (shape instanceof Plot) {
                            Plot plot = (Plot) shape;
                            String s = plot.getToolTip(sc);
                            if (s != null) {
                                if (tooltip == null) {
                                    tooltip = s;
                                    firstid = plot.getID();
                                } else {
                                    if (firstid != null) {
                                        tooltip = "<b>" + firstid + ":</b><br>" + tooltip;
                                        firstid = null;
                                    }

                                    String id = plot.getID();
                                    if (id != null) {
                                        tooltip += "<br><b>" + id + ":</b><br>" + s;
                                    } else {
                                        tooltip += "<br>----------<br>" + s;
                                    }
                                }
                            }
                        }
                    }

                    if (tooltip != null) {
                        setToolTipText(String.format("<html>%s</html>", tooltip));
                    } else {
                        setToolTipText(null);
                    }
                }
            }

            e.consume();
        }

        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
            if (e.getWheelRotation() == 0) {
                return;
            }

            for (int i = 0; i < base.dimension; i++) {
                int s = baseGrid.getAxis(i).getLinearSlices();
                double r = e.getWheelRotation() > 0 ? 1.0 / s : -1.0 / s;
                if (r > -0.5) {
                    double d = (base.upperBound[i] - base.lowerBound[i]) * r;
                    base.lowerBound[i] -= d;
                    base.upperBound[i] += d;
                }
            }

            for (int i = 0; i < base.dimension; i++) {
                base.setPrecisionUnit(i);
            }
            
            base.initBaseCoord();
            graphics.projection.reset();
            baseGrid.setBase(base);
            
            repaint();
            e.consume();
        }

        @Override
        public void componentResized(ComponentEvent e) {
            if (graphics != null) {
                base.initBaseCoord();
                graphics.projection.reset();
                baseGrid.setBase(base);
            }

            repaint();
        }

        @Override
        public void componentHidden(ComponentEvent e) {
        }

        @Override
        public void componentMoved(ComponentEvent e) {
        }

        @Override
        public void componentShown(ComponentEvent e) {
        }
    }

    /**
     * Constructor
     */
    public PlotCanvas(double[] lowerBound, double[] upperBound) {
        initCanvas();
        initBase(lowerBound, upperBound);
        initGraphics();
    }

    /**
     * Constructor
     */
    public PlotCanvas(double[] lowerBound, double[] upperBound, boolean extendBound) {
        initCanvas();
        initBase(lowerBound, upperBound, extendBound);
        initGraphics();
    }

    /**
     * Constructor
     */
    public PlotCanvas(double[] lowerBound, double[] upperBound, String[] axisLabels) {
        initCanvas();
        initBase(lowerBound, upperBound, axisLabels);
        initGraphics();
    }

    /**
     * Constructor
     */
    public PlotCanvas(double[] lowerBound, double[] upperBound, String[] axisLabels, boolean extendBound) {
        initCanvas();
        initBase(lowerBound, upperBound, axisLabels, extendBound);
        initGraphics();
    }
    
    /**
     * Returns a tool bar to control the plot.
     * @return a tool bar to control the plot.
     */
    public JComponent getToolbar() {
        return toolbar;
    }

    /**
     * Initialize the canvas.
     */
    private void initCanvas() {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        if (ge.isHeadless()) {
            setPreferredSize(new Dimension(1600,1200));

        }

        setLayout(new BorderLayout());
        
        canvas = new MathCanvas();
        add(canvas, BorderLayout.CENTER);

        initContextMenauAndToolBar();
        
        // set a new dismiss delay to a really big value, default is 4 sec.
        //ToolTipManager.sharedInstance().setDismissDelay(Integer.MAX_VALUE);
    }
    
    /**
     * Toolbar button actions.
     */
    private transient Action saveAction = new SaveAction();
    private transient Action printAction = new PrintAction();
    private transient Action zoomInAction = new ZoomInAction();
    private transient Action zoomOutAction = new ZoomOutAction();
    private transient Action resetAction = new ResetAction();
    private transient Action enlargePlotAreaAction = new EnlargePlotAreaAction();
    private transient Action shrinkPlotAreaAction = new ShrinkPlotAreaAction();
    private transient Action propertyAction = new PropertyAction();
    private transient Action increaseHeightAction = new IncreaseHeightAction();
    private transient Action increaseWidthAction = new IncreaseWidthAction();
    private transient Action decreaseHeightAction = new DecreaseHeightAction();
    private transient Action decreaseWidthAction = new DecreaseWidthAction();
    private JScrollPane scrollPane;

    /**
     * Initialize context menu and toolbar.
     */
    private void initContextMenauAndToolBar() {
        toolbar = new JToolBar();
        toolbar.add(new Button(saveAction));
        toolbar.add(new Button(printAction));
        toolbar.addSeparator();
        toolbar.add(new Button(zoomInAction));
        toolbar.add(new Button(zoomOutAction));
        toolbar.add(new Button(resetAction));
        toolbar.addSeparator();
        toolbar.add(new Button(enlargePlotAreaAction));
        toolbar.add(new Button(shrinkPlotAreaAction));
        toolbar.add(new Button(increaseHeightAction));
        toolbar.add(new Button(decreaseHeightAction));
        toolbar.add(new Button(increaseWidthAction));
        toolbar.add(new Button(decreaseWidthAction));
        toolbar.addSeparator();
        toolbar.add(new Button(propertyAction));
    
        decreaseHeightAction.setEnabled(false);
        decreaseWidthAction.setEnabled(false);
        
        //Initialize popup menu.
        popup = new JPopupMenu();        
        popup.add(new JMenuItem(saveAction));
        popup.add(new JMenuItem(printAction));
        popup.addSeparator();
        popup.add(new JMenuItem(zoomInAction));
        popup.add(new JMenuItem(zoomOutAction));
        popup.add(new JMenuItem(resetAction));
        popup.addSeparator();
        popup.add(new JMenuItem(enlargePlotAreaAction));
        popup.add(new JMenuItem(shrinkPlotAreaAction));
        popup.add(new JMenuItem(increaseHeightAction));
        popup.add(new JMenuItem(decreaseHeightAction));
        popup.add(new JMenuItem(increaseWidthAction));
        popup.add(new JMenuItem(decreaseWidthAction));
        popup.addSeparator();
        popup.add(new JMenuItem(propertyAction));
        
        AncestorListener ancestorListener = new AncestorListener() {

            @Override
            public void ancestorAdded(AncestorEvent ae) {
                boolean inScrollPane = false;
                Container parent = getParent();
                while (parent != null) {
                    if (parent instanceof JScrollPane) {
                        inScrollPane = true;
                        scrollPane = (JScrollPane) parent;
                        break;
                    }
                    
                    parent = parent.getParent();
                }
                
                increaseHeightAction.setEnabled(inScrollPane);
                increaseWidthAction.setEnabled(inScrollPane);
            }

            @Override
            public void ancestorRemoved(AncestorEvent ae) {
            }

            @Override
            public void ancestorMoved(AncestorEvent ae) {
            }
        };
        
        addAncestorListener(ancestorListener);
    }
    
    private class SaveAction extends AbstractAction {

        public SaveAction() {
            super("Save", new ImageIcon(PlotCanvas.class.getResource("images/save16.png")));
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
            super("Print", new ImageIcon(PlotCanvas.class.getResource("images/print16.png")));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            print();
        }
    }

    private class ZoomInAction extends AbstractAction {

        public ZoomInAction() {
            super("Zoom In", new ImageIcon(PlotCanvas.class.getResource("images/zoom-in16.png")));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            zoom(true);
        }
    }

    private class ZoomOutAction extends AbstractAction {

        public ZoomOutAction() {
            super("Zoom Out", new ImageIcon(PlotCanvas.class.getResource("images/zoom-out16.png")));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            zoom(false);
        }
    }

    private class ResetAction extends AbstractAction {

        public ResetAction() {
            super("Reset", new ImageIcon(PlotCanvas.class.getResource("images/refresh16.png")));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            reset();
        }
    }

    private class EnlargePlotAreaAction extends AbstractAction {

        public EnlargePlotAreaAction() {
            super("Enlarge", new ImageIcon(PlotCanvas.class.getResource("images/resize-larger16.png")));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (margin > 0.05) {
                margin -= 0.05;
                graphics.projection.reset();
                repaint();
            }
            
            if (margin <= 0.05) {
                setEnabled(false);
            }
            
            if (!shrinkPlotAreaAction.isEnabled()) {
                shrinkPlotAreaAction.setEnabled(true);
            }
        }
    }

    private class ShrinkPlotAreaAction extends AbstractAction {

        public ShrinkPlotAreaAction() {
            super("Shrink", new ImageIcon(PlotCanvas.class.getResource("images/resize-smaller16.png")));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (margin < 0.3) {
                margin += 0.05;
                graphics.projection.reset();
                repaint();
            }
            
            if (margin >= 0.3) {
                setEnabled(false);
            }
            
            if (!enlargePlotAreaAction.isEnabled()) {
                enlargePlotAreaAction.setEnabled(true);
            }
        }
    }
    
    private class IncreaseWidthAction extends AbstractAction {

        public IncreaseWidthAction() {
            super("Increase Width", new ImageIcon(PlotCanvas.class.getResource("images/increase-width16.png")));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            Dimension d = getSize();
            d.width += 100;
            setPreferredSize(d);
            invalidate();
            scrollPane.getParent().validate();
            
            if (!decreaseWidthAction.isEnabled()) {
                decreaseWidthAction.setEnabled(true);
            }
        }
    }
    
    private class IncreaseHeightAction extends AbstractAction {

        public IncreaseHeightAction() {
            super("Increase Height", new ImageIcon(PlotCanvas.class.getResource("images/increase-height16.png")));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            Dimension d = getSize();
            d.height += 100;
            setPreferredSize(d);
            invalidate();
            scrollPane.getParent().validate();
            
            if (!decreaseHeightAction.isEnabled()) {
                decreaseHeightAction.setEnabled(true);
            }            
        }
    }
    
    private class DecreaseWidthAction extends AbstractAction {

        public DecreaseWidthAction() {
            super("Decrease Width", new ImageIcon(PlotCanvas.class.getResource("images/decrease-width16.png")));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            Dimension d = getSize();
            d.width -= 100;
            
            Dimension vd = scrollPane.getViewport().getSize();
            if (d.width <= vd.width) {
                d.width = vd.width;
                decreaseWidthAction.setEnabled(false);
            }
            
            setPreferredSize(d);
            invalidate();
            scrollPane.getParent().validate();
        }
    }
    
    private class DecreaseHeightAction extends AbstractAction {

        public DecreaseHeightAction() {
            super("Decrease Height", new ImageIcon(PlotCanvas.class.getResource("images/decrease-height16.png")));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            Dimension d = getSize();
            d.height -= 100;
            
            Dimension vd = scrollPane.getViewport().getSize();
            if (d.height <= vd.height) {
                d.height = vd.height;
                decreaseHeightAction.setEnabled(false);
            }
            
            setPreferredSize(d);
            invalidate();
            scrollPane.getParent().validate();
        }
    }
    
    private class PropertyAction extends AbstractAction {

        public PropertyAction() {
            super("Properties", new ImageIcon(PlotCanvas.class.getResource("images/property16.png")));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JDialog dialog = createPropertyDialog();
            dialog.addWindowListener(new WindowAdapter() {

                @Override
                public void windowClosing(WindowEvent e) {
                }
            });

            dialog.setVisible(true);
            dialog.dispose();
            dialog = null;
        }
    }
    
    /**
     * Creates the property dialog.
     * @return the property dialog.
     */
    private JDialog createPropertyDialog() {
        Frame frame = (Frame) SwingUtilities.getAncestorOfClass(Frame.class, this);
        JDialog dialog = new JDialog(frame, "Plot Properties", true);

        Action okAction = new PropertyDialogOKAction(dialog);
        Action cancelAction = new PropertyDialogCancelAction(dialog);

        JButton okButton = new JButton(okAction);
        JButton cancelButton = new JButton(cancelAction);

        JPanel buttonsPanel = new JPanel();
        buttonsPanel.setLayout(new FlowLayout(FlowLayout.TRAILING));
        buttonsPanel.add(okButton);
        buttonsPanel.add(cancelButton);
        buttonsPanel.setBorder(BorderFactory.createEmptyBorder(25, 0, 10, 10));

        ActionMap actionMap = buttonsPanel.getActionMap();
        actionMap.put(cancelAction.getValue(Action.DEFAULT), cancelAction);
        actionMap.put(okAction.getValue(Action.DEFAULT), okAction);
        InputMap inputMap = buttonsPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        inputMap.put(KeyStroke.getKeyStroke("ESCAPE"), cancelAction.getValue(Action.DEFAULT));
        inputMap.put(KeyStroke.getKeyStroke("ENTER"), okAction.getValue(Action.DEFAULT));

        String[] columnNames = {"Property", "Value"};
        
        if (base.dimension == 2) {
            Object[][] data = {
                {"Title", title},
                {"Title Font", titleFont},
                {"Title Color", titleColor},
                {"X Axis Title", getAxis(0).getAxisLabel()},
                {"X Axis Range", new double[]{base.getLowerBounds()[0], base.getUpperBounds()[0]}},
                {"Y Axis Title", getAxis(1).getAxisLabel()},
                {"Y Axis Range", new double[]{base.getLowerBounds()[1], base.getUpperBounds()[1]}}
            };

            propertyTable = new Table(data, columnNames);
        } else {
            Object[][] data = {
                {"Title", title},
                {"Title Font", titleFont},
                {"Title Color", titleColor},
                {"X Axis Title", getAxis(0).getAxisLabel()},
                {"X Axis Range", new double[]{base.getLowerBounds()[0], base.getUpperBounds()[0]}},
                {"Y Axis Title", getAxis(1).getAxisLabel()},
                {"Y Axis Range", new double[]{base.getLowerBounds()[1], base.getUpperBounds()[1]}},
                {"Z Axis Title", getAxis(2).getAxisLabel()},
                {"Z Axis Range", new double[]{base.getLowerBounds()[2], base.getUpperBounds()[2]}}
            };

            propertyTable = new Table(data, columnNames);
        }

        // There is a known issue with JTables whereby the changes made in a
        // cell editor are not committed when focus is lost.
        // This can result in the table staying in 'edit mode' with the stale
        // value in the cell being edited still showing, although the change
        // has not actually been committed to the model.
        //
        // In fact what should happen is for the method stopCellEditing()
        // on CellEditor to be called when focus is lost.
        // So the editor can choose whether to accept the new value and stop
        // editing, or have the editing cancelled without committing.
        // There is a magic property which you have to set on the JTable
        // instance to turn this feature on.
        propertyTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        
        propertyTable.setFillsViewportHeight(true); 
        JScrollPane tablePanel = new JScrollPane(propertyTable);

        dialog.getContentPane().add(tablePanel, BorderLayout.CENTER);
        dialog.getContentPane().add(buttonsPanel, BorderLayout.SOUTH);
        
        dialog.pack();
        dialog.setLocationRelativeTo(frame);
        return dialog;
    }

    private class PropertyDialogOKAction extends AbstractAction {

        protected static final String ACTION_NAME = "OK";
        private JDialog dialog;

        protected PropertyDialogOKAction(JDialog dialog) {
            this.dialog = dialog;
            putValue(Action.DEFAULT, ACTION_NAME);
            putValue(Action.ACTION_COMMAND_KEY, ACTION_NAME);
            putValue(Action.NAME, ACTION_NAME);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            setTitle((String) propertyTable.getValueAt(0, 1));
            setTitleFont((Font) propertyTable.getValueAt(1, 1));
            setTitleColor((Color) propertyTable.getValueAt(2, 1));
            
            getAxis(0).setAxisLabel((String) propertyTable.getValueAt(3, 1));
            double[] xbound = (double[]) propertyTable.getValueAt(4, 1);
            base.lowerBound[0] = xbound[0];
            base.upperBound[0] = xbound[1];
            
            getAxis(1).setAxisLabel((String) propertyTable.getValueAt(5, 1));
            double[] ybound = (double[]) propertyTable.getValueAt(6, 1);
            base.lowerBound[1] = ybound[0];
            base.upperBound[1] = ybound[1];
            
            if (base.dimension > 2) {
                getAxis(2).setAxisLabel((String) propertyTable.getValueAt(7, 1));
                double[] zbound = (double[]) propertyTable.getValueAt(8, 1);
                base.lowerBound[2] = zbound[0];
                base.upperBound[2] = zbound[1];
            }
            
            for (int i = 0; i < base.dimension; i++) {
                base.setPrecisionUnit(i);
            }

            base.initBaseCoord();
            graphics.projection.reset();
            baseGrid.setBase(base);

            dialog.setVisible(false);
            canvas.repaint();
        }
    }

    private class PropertyDialogCancelAction extends AbstractAction {

        protected static final String ACTION_NAME = "Cancel";
        private JDialog dialog;

        protected PropertyDialogCancelAction(JDialog dialog) {
            this.dialog = dialog;
            putValue(Action.DEFAULT, ACTION_NAME);
            putValue(Action.ACTION_COMMAND_KEY, ACTION_NAME);
            putValue(Action.NAME, ACTION_NAME);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            dialog.setVisible(false);
        }
    }

    /**
     * Shows a file chooser and exports the plot to the selected image file.
     * @throws IOException if an error occurs during writing.
     */
    public void save() throws IOException {
        FileChooser.SimpleFileFilter filter = FileChooser.SimpleFileFilter.getWritableImageFIlter();
        JFileChooser chooser = FileChooser.getInstance();
        chooser.setFileFilter(filter);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setSelectedFiles(new File[0]);
        int returnVal = chooser.showSaveDialog(null);

        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (!filter.accept(file)) {
                file = new File(file.getParentFile(), file.getName() + ".png");
            }
            save(file);
        }
    }
    
    /**
     * Exports the plot to an image file.
     * @param file the destination file.
     * @throws IOException if an error occurs during writing.
     */
    public void save(File file) throws IOException {
        BufferedImage bi = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = bi.createGraphics();
        canvas.print(g2d);

        ImageIO.write(bi, FileChooser.getExtension(file), file);
    }
    
    /**
     * Prints the plot.
     */
    public void print() {
        Printer.getPrinter().print(canvas);
    }
    
    /**
     * Zooms in/out the plot.
     * @param inout true if zoom in. Otherwise, zoom out.
     */
    public void zoom(boolean inout) {
        for (int i = 0; i < base.dimension; i++) {
            int s = baseGrid.getAxis(i).getLinearSlices();
            double r = inout ? -1.0 / s : 1.0 / s;
            double d = (base.upperBound[i] - base.lowerBound[i]) * r;
            base.lowerBound[i] -= d;
            base.upperBound[i] += d;
        }

        for (int i = 0; i < base.dimension; i++) {
            base.setPrecisionUnit(i);
        }

        base.initBaseCoord();
        graphics.projection.reset();
        baseGrid.setBase(base);

        canvas.repaint();
    }
    
    /**
     * Resets the plot.
     */
    public void reset() {
        base.reset();
        graphics.projection.reset();
        baseGrid.setBase(base);

        if (graphics.projection instanceof Projection3D) {
            ((Projection3D) graphics.projection).setDefaultView();
        }

        canvas.repaint();    
    }
    
    /**
     * Initialize the Graphics object.
     */
    private void initGraphics() {
        if (base.dimension == 2) {
            graphics = new Graphics(new Projection2D(this));
        } else {
            graphics = new Graphics(new Projection3D(this));
        }
    }

    /**
     * Initialize a coordinate base.
     */
    private void initBase(double[] lowerBound, double[] upperBound) {
        base = new Base(lowerBound, upperBound);
        backupBase = base;
        baseGrid = new BaseGrid(base);
    }

    /**
     * Initialize a coordinate base.
     */
    private void initBase(double[] lowerBound, double[] upperBound, boolean extendBound) {
        base = new Base(lowerBound, upperBound, extendBound);
        backupBase = base;
        baseGrid = new BaseGrid(base);
    }

    /**
     * Initialize a coordinate base.
     */
    private void initBase(double[] lowerBound, double[] upperBound, String[] axisLabels) {
        base = new Base(lowerBound, upperBound);
        backupBase = base;
        baseGrid = new BaseGrid(base, axisLabels);
    }

    /**
     * Initialize a coordinate base.
     */
    private void initBase(double[] lowerBound, double[] upperBound, String[] axisLabels, boolean extendBound) {
        base = new Base(lowerBound, upperBound, extendBound);
        backupBase = base;
        baseGrid = new BaseGrid(base, axisLabels);
    }
    
    /**
     * Returns the size of margin, which is not used as plot area.
     * Currently, all four sides have the same margin size.
     * @return the size of margin.
     */
    public double getMargin() {
        return margin;
    }
    
    /**
     * Sets the size of margin in [0.0, 0.3] on each side. Currently, all four
     * sides have the same margin size.
     * @param margin the size of margin.
     */
    public PlotCanvas setMargin(double margin) {
        if (margin < 0.0 || margin >= 0.3) {
            throw new IllegalArgumentException("Invalid margin: " + margin);
        }
        
        this.margin = margin;
        repaint();
        return this;
    }

    /**
     * Returns the coordinate base.
     * @return the coordinate base.
     */
    public Base getBase() {
        return base;
    }
    
    /**
     * Returns the main title of canvas.
     */
    public String getTitle() {
        return title;
    }

    /**
     * Set the main title of canvas.
     */
    public PlotCanvas setTitle(String title) {
        this.title = title;
        repaint();
        return this;
    }

    /**
     * Returns the font for title.
     */
    public Font getTitleFont() {
        return titleFont;
    }

    /**
     * Returns the color for title.
     */
    public Color getTitleColor() {
        return titleColor;
    }

    /**
     * Set the font for title.
     */
    public PlotCanvas setTitleFont(Font font) {
        this.titleFont = font;
        repaint();
        return this;
    }

    /**
     * Set the color for title.
     */
    public PlotCanvas setTitleColor(Color color) {
        this.titleColor = color;
        repaint();
        return this;
    }

    /**
     * Returns the i-<i>th</i> axis.
     */
    public Axis getAxis(int i) {
        return baseGrid.getAxis(i);
    }

    /**
     * Returns the labels/legends of axes.
     */
    public String[] getAxisLabels() {
        String[] labels = new String[base.dimension];
        for (int i = 0; i < base.dimension; i++) {
            labels[i] = baseGrid.getAxis(i).getAxisLabel();
        }
        return labels;
    }

    /**
     * Returns the label/legend of an axis.
     */
    public String getAxisLabel(int axis) {
        return baseGrid.getAxis(axis).getAxisLabel();
    }

    /**
     * Sets the labels/legends of axes.
     */
    public PlotCanvas setAxisLabels(String... labels) {
        baseGrid.setAxisLabel(labels);
        repaint();
        return this;
    }

    /**
     * Sets the label/legend of an axis.
     */
    public PlotCanvas setAxisLabel(int axis, String label) {
        baseGrid.setAxisLabel(axis, label);
        repaint();
        return this;
    }

    /**
     * Returns the list of shapes in the canvas.
     * @return the list of shapes in the canvas. 
     */
    public List<Shape> getShapes() {
        return shapes;
    }
    
    /**
     * Add a graphical shape to the canvas.
     */
    public void add(Shape p) {
        shapes.add(p);
        repaint();
    }

    /**
     * Remove a graphical shape from the canvas.
     */
    public void remove(Shape p) {
        shapes.remove(p);
        repaint();
    }

    /**
     * Add a graphical shape to the canvas.
     */
    public void add(Plot p) {
        shapes.add(p);

        JComponent[] tb = p.getToolBar();
        if (tb != null) {
            toolbar.addSeparator();
            for (JComponent comp : tb) {
                toolbar.add(comp);
            }
        }

        repaint();
    }

    /**
     * Remove a graphical shape from the canvas.
     */
    public void remove(Plot p) {
        shapes.remove(p);

        JComponent[] tb = p.getToolBar();
        if (tb != null) {
            for (JComponent comp : tb) {
                toolbar.remove(comp);
            }
        }

        repaint();
    }

    /**
     * Remove all graphic plots from the canvas.
     */
    public void clear() {
        shapes.clear();
        repaint();
    }

    /**
     * Returns the lower bounds.
     */
    public double[] getLowerBounds() {
        return base.lowerBound;
    }

    /**
     * Returns the upper bounds.
     */
    public double[] getUpperBounds() {
        return base.upperBound;
    }

    /**
     * Extend lower bounds.
     */
    public void extendLowerBound(double[] bound) {
        base.extendLowerBound(bound);
        baseGrid.setBase(base);
        repaint();
    }

    /**
     * Extend upper bounds.
     */
    public void extendUpperBound(double[] bound) {
        base.extendUpperBound(bound);
        baseGrid.setBase(base);
        repaint();
    }

    /**
     * Extend lower and upper bounds.
     */
    public void extendBound(double[] lowerBound, double[] upperBound) {
        base.extendBound(lowerBound, upperBound);
        baseGrid.setBase(base);
        repaint();
    }

    /**
     * Adds a label to this canvas.
     */
    public void label(String text, double... coord) {
        add(new Label(text, coord));
    }

    /**
     * Adds a label to this canvas.
     */
    public void label(String text, Font font, double... coord) {
        Label label = new Label(text, coord);
        label.setFont(font);
        add(label);
    }

    /**
     * Adds a label to this canvas.
     */
    public void label(String text, Color color, double... coord) {
        Label label = new Label(text, coord);
        label.setColor(color);
        add(label);
    }

    /**
     * Adds a label to this canvas.
     */
    public void label(String text, Font font, Color color, double... coord) {
        Label label = new Label(text, coord);
        label.setFont(font);
        label.setColor(color);
        add(label);
    }

    /**
     * Adds a point to this canvas.
     */
    public void point(double... coord) {
        add(new Point(coord));
    }

    /**
     * Adds a point to this canvas.
     */
    public void point(char legend, double... coord) {
        add(new Point(legend, coord));
    }

    /**
     * Adds a point to this canvas.
     */
    public void point(Color color, double... coord) {
        add(new Point(color, coord));
    }

    /**
     * Adds a point to this canvas.
     */
    public void point(char legend, Color color, double... coord) {
        add(new Point(legend, color, coord));
    }

    /**
     * Adds a scatter plot to this canvas.
     * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
     * @return the scatter plot for the given points.
     */
    public ScatterPlot points(double[]... data) {
        return points(null, data);
    }

    /**
     * Adds a scatter plot to this canvas.
     * @param id the id of the plot.
     * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
     * @return the scatter plot for the given points.
     */
    public ScatterPlot points(String id, double[]... data) {
        if (data[0].length != base.dimension) {
            throw new IllegalArgumentException("Invalid data dimension: " + data[0].length);
        }

        double[] lowerBound = Math.colMin(data);
        double[] upperBound = Math.colMax(data);
        extendBound(lowerBound, upperBound);

        ScatterPlot plot = new ScatterPlot(data);
        plot.setID(id);
        add(plot);

        return plot;
    }

    /**
     * Adds a scatter plot to this canvas.
     * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
     * @param labels labels of points.
     * @return the scatter plot for the given points.
     */
    public ScatterPlot points(double[][] data, String[] labels) {
        return points(null, data, labels);
    }

    /**
     * Adds a scatter plot to this canvas.
     * @param id the id of the plot.
     * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
     * @param labels labels of points.
     * @return the scatter plot for the given points.
     */
    public ScatterPlot points(String id, double[][] data, String[] labels) {
        if (data[0].length != base.dimension) {
            throw new IllegalArgumentException("Invalid data dimension: " + data[0].length);
        }

        if (data.length != labels.length) {
            throw new IllegalArgumentException("The number of points and that of labels are not same.");
        }

        double[] lowerBound = Math.colMin(data);
        double[] upperBound = Math.colMax(data);
        extendBound(lowerBound, upperBound);

        ScatterPlot plot = new ScatterPlot(data, labels);
        plot.setID(id);
        add(plot);

        return plot;
    }

    /**
     * Adds a scatter plot to this canvas.
     * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
     * @param legend the legend used to draw points.
     * <ul>
     * <li> . : dot
     * <li> + : +
     * <li> - : -
     * <li> | : |
     * <li> * : star
     * <li> x : x
     * <li> o : circle
     * <li> O : large circle
     * <li> @ : solid circle
     * <li> # : large solid circle
     * <li> s : square
     * <li> S : large square
     * <li> q : solid square
     * <li> Q : large solid square
     * <li> others : dot
     * </ul>
     * @return the scatter plot for the given points.
     */
    public ScatterPlot points(double[][] data, char legend) {
        return points(null, data, legend);
    }

    /**
     * Adds a scatter plot to this canvas.
     * @param id the id of the plot.
     * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
     * @param legend the legend used to draw points.
     * <ul>
     * <li> . : dot
     * <li> + : +
     * <li> - : -
     * <li> | : |
     * <li> * : star
     * <li> x : x
     * <li> o : circle
     * <li> O : large circle
     * <li> @ : solid circle
     * <li> # : large solid circle
     * <li> s : square
     * <li> S : large square
     * <li> q : solid square
     * <li> Q : large solid square
     * <li> others : dot
     * </ul>
     * @return the scatter plot for the given points.
     */
    public ScatterPlot points(String id, double[][] data, char legend) {
        if (data[0].length != base.dimension) {
            throw new IllegalArgumentException("Invalid data dimension: " + data[0].length);
        }

        double[] lowerBound = Math.colMin(data);
        double[] upperBound = Math.colMax(data);
        extendBound(lowerBound, upperBound);

        ScatterPlot plot = new ScatterPlot(data, legend);
        plot.setID(id);
        add(plot);

        return plot;
    }

    /**
     * Adds a scatter plot to this canvas.
     * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
     * @param color the color used to draw points.
     * @return the scatter plot for the given points.
     */
    public ScatterPlot points(double[][] data, Color color) {
        return points(null, data, color);
    }

    /**
     * Adds a scatter plot to this canvas.
     * @param id the id of the plot.
     * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
     * @param color the color used to draw points.
     * @return the scatter plot for the given points.
     */
    public ScatterPlot points(String id, double[][] data, Color color) {
        if (data[0].length != base.dimension) {
            throw new IllegalArgumentException("Invalid data dimension: " + data[0].length);
        }

        double[] lowerBound = Math.colMin(data);
        double[] upperBound = Math.colMax(data);
        extendBound(lowerBound, upperBound);

        ScatterPlot plot = new ScatterPlot(data);
        plot.setID(id);
        plot.setColor(color);
        add(plot);

        return plot;
    }

    /**
     * Adds a scatter plot to this canvas.
     * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
     * @param color the color used to draw points.
     * @param legend the legend used to draw points.
     * <ul>
     * <li> . : dot
     * <li> + : +
     * <li> - : -
     * <li> | : |
     * <li> * : star
     * <li> x : x
     * <li> o : circle
     * <li> O : large circle
     * <li> @ : solid circle
     * <li> # : large solid circle
     * <li> s : square
     * <li> S : large square
     * <li> q : solid square
     * <li> Q : large solid square
     * <li> others : dot
     * </ul>
     * @return the scatter plot for the given points.
     */
    public ScatterPlot points(double[][] data, char legend, Color color) {
        return points(null, data, legend, color);
    }

    /**
     * Adds a scatter plot to this canvas.
     * @param id the id of the plot.
     * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
     * @param color the color used to draw points.
     * @param legend the legend used to draw points.
     * <ul>
     * <li> . : dot
     * <li> + : +
     * <li> - : -
     * <li> | : |
     * <li> * : star
     * <li> x : x
     * <li> o : circle
     * <li> O : large circle
     * <li> @ : solid circle
     * <li> # : large solid circle
     * <li> s : square
     * <li> S : large square
     * <li> q : solid square
     * <li> Q : large solid square
     * <li> others : dot
     * </ul>
     * @return the scatter plot for the given points.
     */
    public ScatterPlot points(String id, double[][] data, char legend, Color color) {
        if (data[0].length != base.dimension) {
            throw new IllegalArgumentException("Invalid data dimension: " + data[0].length);
        }

        double[] lowerBound = Math.colMin(data);
        double[] upperBound = Math.colMax(data);
        extendBound(lowerBound, upperBound);

        ScatterPlot plot = new ScatterPlot(data, legend);
        plot.setID(id);
        plot.setColor(color);
        add(plot);

        return plot;
    }

    /**
     * Adds a poly line plot to this canvas.
     * @param y a data vector that describes y coordinates of points. The x
     * coordinates will be [0, n), where n is the length of y.
     */
    public LinePlot line(double[] y) {
        return line(null, y);
    }

    /**
     * Adds a poly line plot to this canvas.
     * @param id the id of the plot.
     * @param y a data vector that describes y coordinates of points. The x
     * coordinates will be [0, n), where n is the length of y.
     */
    public LinePlot line(String id, double[] y) {
        double[] lowerBound = {0, Math.min(y)};
        double[] upperBound = {y.length, Math.max(y)};
        extendBound(lowerBound, upperBound);

        double[][] data = new double[y.length][2];
        for (int i = 0; i < data.length; i++) {
            data[i][0] = i;
            data[i][1] = y[i];
        }

        LinePlot plot = new LinePlot(data);
        plot.setID(id);
        add(plot);

        return plot;
    }

    /**
     * Adds a poly line plot to this canvas.
     * @param y a data vector that describes y coordinates of points. The x
     * coordinates will be [0, n), where n is the length of y.
     * @param color the color of line.
     */
    public LinePlot line(double[] y, Color color) {
        return line(null, y, color);

    }

    /**
     * Adds a poly line plot to this canvas.
     * @param id the id of the plot.
     * @param y a data vector that describes y coordinates of points. The x
     * coordinates will be [0, n), where n is the length of y.
     * @param color the color of line.
     */
    public LinePlot line(String id, double[] y, Color color) {
        double[] lowerBound = {0, Math.min(y)};
        double[] upperBound = {y.length, Math.max(y)};
        extendBound(lowerBound, upperBound);

        double[][] data = new double[y.length][2];
        for (int i = 0; i < data.length; i++) {
            data[i][0] = i;
            data[i][1] = y[i];
        }

        LinePlot plot = new LinePlot(data);
        plot.setID(id);
        plot.setColor(color);
        add(plot);

        return plot;
    }

    /**
     * Add a poly line plot of given data into the current canvas.
     * @param y a data vector that describes y coordinates of points. The x
     * coordinates will be [0, n), where n is the length of y.
     * @param style the stroke style of line.
     */
    public LinePlot line(double[] y, Line.Style style) {
        return line(null, y, style);
    }

    /**
     * Adds a poly line plot to this canvas.
     * @param id the id of the plot.
     * @param y a data vector that describes y coordinates of points. The x
     * coordinates will be [0, n), where n is the length of y.
     * @param style the stroke style of line.
     */
    public LinePlot line(String id, double[] y, Line.Style style) {
        double[] lowerBound = {0, Math.min(y)};
        double[] upperBound = {y.length, Math.max(y)};
        extendBound(lowerBound, upperBound);

        double[][] data = new double[y.length][2];
        for (int i = 0; i < data.length; i++) {
            data[i][0] = i;
            data[i][1] = y[i];
        }

        LinePlot plot = new LinePlot(data, style);
        plot.setID(id);
        add(plot);

        return plot;
    }

    /**
     * Adds a poly line plot to this canvas.
     * @param y a data vector that describes y coordinates of points. The x
     * coordinates will be [0, n), where n is the length of y.
     * @param style the stroke style of line.
     * @param color the color of line.
     */
    public LinePlot line(double[] y, Line.Style style, Color color) {
        return line(null, y, style, color);
    }

    /**
     * Add a poly line plot of given data into the current canvas.
     * @param id the id of the plot.
     * @param y a data vector that describes y coordinates of points. The x
     * coordinates will be [0, n), where n is the length of y.
     * @param style the stroke style of line.
     * @param color the color of line.
     */
    public LinePlot line(String id, double[] y, Line.Style style, Color color) {
        double[] lowerBound = {0, Math.min(y)};
        double[] upperBound = {y.length, Math.max(y)};
        extendBound(lowerBound, upperBound);

        double[][] data = new double[y.length][2];
        for (int i = 0; i < data.length; i++) {
            data[i][0] = i;
            data[i][1] = y[i];
        }

        LinePlot plot = new LinePlot(data, style);
        plot.setID(id);
        plot.setColor(color);
        add(plot);

        return plot;
    }

    /**
     * Adds a poly line plot to this canvas.
     * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
     */
    public LinePlot line(double[]... data) {
        return line(null, data);
    }

    /**
     * Adds a poly line plot to this canvas.
     * @param id the id of the plot.
     * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
     */
    public LinePlot line(String id, double[]... data) {
        if (data[0].length != base.dimension) {
            throw new IllegalArgumentException("Invalid data dimension: " + data[0].length);
        }

        double[] lowerBound = Math.colMin(data);
        double[] upperBound = Math.colMax(data);
        extendBound(lowerBound, upperBound);

        LinePlot plot = new LinePlot(data);
        plot.setID(id);
        add(plot);

        return plot;
    }

    /**
     * Adds a poly line plot to this canvas.
     * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
     * @param color the color of line.
     */
    public LinePlot line(double[][] data, Color color) {
        return line(null, data, color);

    }

    /**
     * Adds a poly line plot to this canvas.
     * @param id the id of the plot.
     * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
     * @param color the color of line.
     */
    public LinePlot line(String id, double[][] data, Color color) {
        if (data[0].length != base.dimension) {
            throw new IllegalArgumentException("Invalid data dimension: " + data[0].length);
        }

        double[] lowerBound = Math.colMin(data);
        double[] upperBound = Math.colMax(data);
        extendBound(lowerBound, upperBound);

        LinePlot plot = new LinePlot(data);
        plot.setID(id);
        plot.setColor(color);
        add(plot);

        return plot;
    }

    /**
     * Adds a poly line plot to this canvas.
     * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
     * @param style the stroke style of line.
     */
    public LinePlot line(double[][] data, Line.Style style) {
        return line(null, data, style);
    }

    /**
     * Adds a poly line plot to this canvas.
     * @param id the id of the plot.
     * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
     * @param style the stroke style of line.
     */
    public LinePlot line(String id, double[][] data, Line.Style style) {
        if (data[0].length != base.dimension) {
            throw new IllegalArgumentException("Invalid data dimension: " + data[0].length);
        }

        double[] lowerBound = Math.colMin(data);
        double[] upperBound = Math.colMax(data);
        extendBound(lowerBound, upperBound);

        LinePlot plot = new LinePlot(data, style);
        plot.setID(id);
        add(plot);

        return plot;
    }

    /**
     * Adds a poly line plot to this canvas.
     * @param data a n x 2 or n x 3 matrix that describes coordinates of points.
     * @param style the stroke style of line.
     * @param color the color of line.
     */
    public LinePlot line(double[][] data, Line.Style style, Color color) {
        return line(null, data, style, color);
    }

    /**
     * Adds a poly line plot to this canvas.
     * @param id the id of the plot.
     * @param data a n x 2 or n x 3 matrix that describes coordinates of points.
     * @param style the stroke style of line.
     * @param color the color of line.
     */
    public LinePlot line(String id, double[][] data, Line.Style style, Color color) {
        if (data[0].length != base.dimension) {
            throw new IllegalArgumentException("Invalid data dimension: " + data[0].length);
        }

        double[] lowerBound = Math.colMin(data);
        double[] upperBound = Math.colMax(data);
        extendBound(lowerBound, upperBound);

        LinePlot plot = new LinePlot(data, style);
        plot.setID(id);
        plot.setColor(color);
        add(plot);

        return plot;
    }

    /**
     * Adds a staircase line plot to this canvas.
     * @param data a n x 2 or n x 3 matrix that describes coordinates of points.
     * @param color the color of line.
     */
    public StaircasePlot staircase(double[][] data, Color color) {
        return staircase(null, data, color);
    }

    /**
     * Adds a staircase line plot to this canvas.
     * @param id the id of the plot.
     * @param data a n x 2 or n x 3 matrix that describes coordinates of points.
     * @param color the color of line.
     */
    public StaircasePlot staircase(String id, double[][] data, Color color) {
        if (data[0].length != 2 && data[0].length != 3) {
            throw new IllegalArgumentException("Invalid data dimension: " + data[0].length);
        }

        StaircasePlot plot = new StaircasePlot(data);
        plot.setID(id);
        plot.setColor(color);
        add(plot);
        return plot;
    }

    /**
     * Adds a histogram to this canvas.
     * @param data a sample set.
     */
    public Histogram histogram(double[] data, Color color) {
        return histogram((String) null, data, color);
    }

    /**
     * Adds a histogram to this canvas.
     * @param id the id of the plot.
     * @param data a sample set.
     */
    public Histogram histogram(String id, double[] data, Color color) {
        if (base.dimension != 2) {
            throw new IllegalArgumentException("Histogram can be only painted in a 2D canvas.");
        }

        Histogram histogram = new Histogram(data);
        histogram.setID(id);
        histogram.setColor(color);

        double[] lowerBound = {Math.min(data), 0};
        double[] upperBound = {Math.max(data), 0};
        double[][] freq = histogram.getHistogram();
        for (int i = 0; i < freq.length; i++) {
            if (freq[i][1] > upperBound[1]) {
                upperBound[1] = freq[i][1];
            }
        }

        extendBound(lowerBound, upperBound);
        add(histogram);

        return histogram;
    }

    /**
     * Adds a histogram to this canvas.
     * @param data a sample set.
     * @param k the number of bins.
     */
    public Histogram histogram(double[] data, int k, Color color) {
        return histogram(null, data, k, color);
    }

    /**
     * Adds a histogram to this canvas.
     * @param id the id of the plot.
     * @param data a sample set.
     * @param k the number of bins.
     */
    public Histogram histogram(String id, double[] data, int k, Color color) {
        if (base.dimension != 2) {
            throw new IllegalArgumentException("Histogram can be only painted in a 2D canvas.");
        }

        Histogram histogram = new Histogram(data, k);
        histogram.setID(id);
        histogram.setColor(color);

        double[] lowerBound = {Math.min(data), 0};
        double[] upperBound = {Math.max(data), 0};
        double[][] freq = histogram.getHistogram();
        for (int i = 0; i < freq.length; i++) {
            if (freq[i][1] > upperBound[1]) {
                upperBound[1] = freq[i][1];
            }
        }

        extendBound(lowerBound, upperBound);
        add(histogram);

        return histogram;
    }

    /**
     * Adds a histogram to this canvas.
     * @param data a sample set.
     * @param breaks an array of size k+1 giving the breakpoints between
     * histogram cells. Must be in ascending order.
     */
    public Histogram histogram(double[] data, double[] breaks, Color color) {
        return histogram(null, data, breaks, color);
    }

    /**
     * Adds a histogram to this canvas.
     * @param id the id of the plot.
     * @param data a sample set.
     * @param breaks an array of size k+1 giving the breakpoints between
     * histogram cells. Must be in ascending order.
     */
    public Histogram histogram(String id, double[] data, double[] breaks, Color color) {
        if (base.dimension != 2) {
            throw new IllegalArgumentException("Histogram can be only painted in a 2D canvas.");
        }

        Histogram histogram = new Histogram(data, breaks);
        histogram.setID(id);
        histogram.setColor(color);

        double[] lowerBound = {Math.min(data), 0};
        double[] upperBound = {Math.max(data), 0};
        double[][] freq = histogram.getHistogram();
        for (int i = 0; i < freq.length; i++) {
            if (freq[i][1] > upperBound[1]) {
                upperBound[1] = freq[i][1];
            }
        }

        extendBound(lowerBound, upperBound);
        add(histogram);

        return histogram;
    }

    /**
     * Adds a histogram to this canvas.
     * @param data a sample set.
     */
    public Histogram histogram(int[] data, Color color) {
        return histogram((String) null, data, color);
    }

    /**
     * Adds a histogram to this canvas.
     * @param id the id of the plot.
     * @param data a sample set.
     */
    public Histogram histogram(String id, int[] data, Color color) {
        if (base.dimension != 2) {
            throw new IllegalArgumentException("Histogram can be only painted in a 2D canvas.");
        }

        Histogram histogram = new Histogram(data);
        histogram.setID(id);
        histogram.setColor(color);

        double[] lowerBound = {Math.min(data) - 0.5, 0};
        double[] upperBound = {Math.max(data) + 0.5, 0};
        double[][] freq = histogram.getHistogram();
        for (int i = 0; i < freq.length; i++) {
            if (freq[i][1] > upperBound[1]) {
                upperBound[1] = freq[i][1];
            }
        }

        extendBound(lowerBound, upperBound);
        add(histogram);

        return histogram;
    }

    /**
     * Adds a histogram to this canvas.
     * @param data a sample set.
     * @param k the number of bins.
     */
    public Histogram histogram(int[] data, int k, Color color) {
        return histogram(null, data, k, color);
    }

    /**
     * Adds a histogram to this canvas.
     * @param id the id of the plot.
     * @param data a sample set.
     * @param k the number of bins.
     */
    public Histogram histogram(String id, int[] data, int k, Color color) {
        if (base.dimension != 2) {
            throw new IllegalArgumentException("Histogram can be only painted in a 2D canvas.");
        }

        Histogram histogram = new Histogram(data, k);
        histogram.setID(id);
        histogram.setColor(color);

        double[] lowerBound = {Math.min(data) - 0.5, 0};
        double[] upperBound = {Math.max(data) + 0.5, 0};
        double[][] freq = histogram.getHistogram();
        for (int i = 0; i < freq.length; i++) {
            if (freq[i][1] > upperBound[1]) {
                upperBound[1] = freq[i][1];
            }
        }

        extendBound(lowerBound, upperBound);
        add(histogram);

        return histogram;
    }

    /**
     * Adds a histogram to this canvas.
     * @param data a sample set.
     * @param breaks an array of size k+1 giving the breakpoints between
     * histogram cells. Must be in ascending order.
     */
    public Histogram histogram(int[] data, double[] breaks, Color color) {
        return histogram(null, data, breaks, color);
    }

    /**
     * Adds a histogram to this canvas.
     * @param id the id of the plot.
     * @param data a sample set.
     * @param breaks an array of size k+1 giving the breakpoints between
     * histogram cells. Must be in ascending order.
     */
    public Histogram histogram(String id, int[] data, double[] breaks, Color color) {
        if (base.dimension != 2) {
            throw new IllegalArgumentException("Histogram can be only painted in a 2D canvas.");
        }

        Histogram histogram = new Histogram(data, breaks);
        histogram.setID(id);
        histogram.setColor(color);

        double[] lowerBound = {Math.min(data) - 0.5, 0};
        double[] upperBound = {Math.max(data) + 0.5, 0};
        double[][] freq = histogram.getHistogram();
        for (int i = 0; i < freq.length; i++) {
            if (freq[i][1] > upperBound[1]) {
                upperBound[1] = freq[i][1];
            }
        }

        extendBound(lowerBound, upperBound);
        add(histogram);

        return histogram;
    }

    /**
     * Adds a 2D grid plot to the canvas.
     * @param data an m x n x 2 array which are coordinates of m x n grid.
     */
    public Grid grid(double[][][] data) {
        if (base.dimension != 2) {
            throw new IllegalArgumentException("Grid can be only painted in a 2D canvas. Try surface() for 3D grid.");
        }

        Grid grid = new Grid(data);
        add(grid);
        return grid;
    }

    /**
     * Create a scree plot for PCA.
     * @param pca principal component analysis object.
     */
    public static PlotCanvas screeplot(PCA pca) {
        int n = pca.getVarianceProportion().length;

        double[] lowerBound = {0, 0.0};
        double[] upperBound = {n + 1, 1.0};

        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound, false);
        canvas.setAxisLabels("Principal Component", "Proportion of Variance");

        String[] labels = new String[n];
        double[] x = new double[n];
        double[][] data = new double[n][2];
        double[][] data2 = new double[n][2];
        for (int i = 0; i < n; i++) {
            labels[i] = "PC" + (i + 1);
            x[i] = i + 1;
            data[i][0] = x[i];
            data[i][1] = pca.getVarianceProportion()[i];
            data2[i][0] = x[i];
            data2[i][1] = pca.getCumulativeVarianceProportion()[i];
        }

        LinePlot plot = new LinePlot(data);
        plot.setID("Variance");
        plot.setColor(Color.RED);
        plot.setLegend('@');
        canvas.add(plot);
        canvas.getAxis(0).addLabel(labels, x);

        LinePlot plot2 = new LinePlot(data2);
        plot2.setID("Cumulative Variance");
        plot2.setColor(Color.BLUE);
        plot2.setLegend('@');
        canvas.add(plot2);

        return canvas;
    }
}
