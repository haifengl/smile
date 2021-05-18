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

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
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
public class PlotPanel extends JPanel {
    /**
     * The canvas of plots.
     */
    private Canvas canvas;
    /**
     * The Swing component to draw the canvas.
     */
    private JCanvas contentPane;
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
     * The Swing component to draw the canvas.
     */
    private class JCanvas extends JComponent implements Printable, ComponentListener, MouseListener, MouseMotionListener, MouseWheelListener, ActionListener {
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
         * The coordinate base when the user start dragging the mouse.
         */
        private Base backupBase;

        /**
         * Constructor.
         */
        JCanvas() {
            setBackground(Color.white);
            setDoubleBuffered(true);
            addComponentListener(this);
            addMouseListener(this);
            addMouseMotionListener(this);
            addMouseWheelListener(this);
        }

        @Override
        public void paintComponent(java.awt.Graphics g) {
            canvas.paint((Graphics2D) g, getWidth(), getHeight());

            if (mouseDraggingX >= 0 && mouseDraggingY >= 0) {
                g.setColor(Color.BLACK);
                g.drawRect(
                        Math.min(mouseClickX, mouseDraggingX),
                        Math.min(mouseClickY, mouseDraggingY),
                        Math.abs(mouseClickX - mouseDraggingX),
                        Math.abs(mouseClickY - mouseDraggingY)
                );
            }
        }

        @Override
        public int print(java.awt.Graphics g, PageFormat pf, int page) throws PrinterException {
            if (page > 0) {
                // We have only one page, and 'page' is zero-based
                return NO_SUCH_PAGE;
            }

            Graphics2D g2d = (Graphics2D) g;

            // User (0,0) is typically outside the printable area, so we must
            // translate by the X and Y values in the PageFormat to avoid clipping
            g2d.translate(pf.getImageableX(), pf.getImageableY());

            // Scale plots to paper size.
            double scaleX = pf.getImageableWidth() / getWidth();
            double scaleY = pf.getImageableHeight() / getHeight();
            g2d.scale(scaleX, scaleY);

            // Now we perform our rendering
            canvas.paint(g2d, getWidth(), getHeight());

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
            if (canvas.base.dimension == 2) {
                mouseDraggingX = e.getX();
                mouseDraggingY = e.getY();
            } else if (canvas.base.dimension == 3) {
                canvas.graphics.rotate(e.getX() - mouseClickX, e.getY() - mouseClickY);
                mouseClickX = e.getX();
                mouseClickY = e.getY();
            }

            repaint();
            e.consume();
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            Base base = canvas.base;
            Graphics graphics = canvas.graphics;

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
                                    && Math.max(sc1[0], sc2[0]) > canvas.base.lowerBound[0]
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
                                canvas.resetAxis();
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
                backupBase = canvas.base;
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
            Base base = canvas.base;
            Graphics graphics = canvas.graphics;

            if (base.dimension == 2) {
                if (mouseDoubleClicked) {
                    double x = mouseClickX - e.getX();
                    if (Math.abs(x) > 20) {
                        int s = canvas.axis[0].slices();
                        x = x > 0 ? 1.0 / s : -1.0 / s;
                        x *= (backupBase.upperBound[0] - backupBase.lowerBound[0]);
                        base.lowerBound[0] = backupBase.lowerBound[0] + x;
                        base.upperBound[0] = backupBase.upperBound[0] + x;
                        mouseClickX = e.getX();
                    }

                    double y = mouseClickY - e.getY();
                    if (Math.abs(y) > 20) {
                        int s = canvas.axis[1].slices();
                        y = y > 0 ? -1.0 / s : 1.0 / s;
                        y *= (backupBase.upperBound[1] - backupBase.lowerBound[1]);
                        base.lowerBound[1] = backupBase.lowerBound[1] + y;
                        base.upperBound[1] = backupBase.upperBound[1] + y;
                        mouseClickY = e.getY();
                    }

                    base.initBaseCoord();
                    graphics.projection.reset();
                    canvas.resetAxis();
                    repaint();
                } else {
                    String tooltip = null;
                    double[] sc = ((Projection2D) (graphics.projection)).inverseProjection(e.getX(), e.getY());

                    String firstid = null;
                    for (Shape shape : canvas.shapes) {
                        if (shape instanceof Plot) {
                            Plot plot = (Plot) shape;
                            Optional<String> s = plot.tooltip(sc);
                            if (s.isPresent()) {
                                if (tooltip == null) {
                                    tooltip = s.get();
                                    firstid = null;//plot.name();
                                } else {
                                    if (firstid != null) {
                                        tooltip = "<b>" + firstid + ":</b><br>" + tooltip;
                                        firstid = null;
                                    }

                                    String id = null;//plot.name();
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

            Base base = canvas.base;
            Graphics graphics = canvas.graphics;

            for (int i = 0; i < base.dimension; i++) {
                int s = canvas.axis[i].slices();
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
            canvas.resetAxis();

            repaint();
            e.consume();
        }

        @Override
        public void componentResized(ComponentEvent e) {
            Base base = canvas.base;
            Graphics graphics = canvas.graphics;

            if (graphics != null) {
                base.initBaseCoord();
                graphics.projection.reset();
                canvas.resetAxis();
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
    public PlotPanel(Canvas canvas) {
        this.canvas = canvas;
        contentPane = new JCanvas();

        setLayout(new BorderLayout());
        add(contentPane, BorderLayout.CENTER);
        initContextMenauAndToolBar();
    }

    /**
     * Returns a tool bar to control the plot.
     * @return a tool bar to control the plot.
     */
    public JComponent getToolbar() {
        return toolbar;
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
            super("Save", new ImageIcon(PlotPanel.class.getResource("images/save16.png")));
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
            super("Print", new ImageIcon(PlotPanel.class.getResource("images/print16.png")));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            print();
        }
    }

    private class ZoomInAction extends AbstractAction {

        public ZoomInAction() {
            super("Zoom In", new ImageIcon(PlotPanel.class.getResource("images/zoom-in16.png")));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            zoom(true);
        }
    }

    private class ZoomOutAction extends AbstractAction {

        public ZoomOutAction() {
            super("Zoom Out", new ImageIcon(PlotPanel.class.getResource("images/zoom-out16.png")));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            zoom(false);
        }
    }

    private class ResetAction extends AbstractAction {

        public ResetAction() {
            super("Reset", new ImageIcon(PlotPanel.class.getResource("images/refresh16.png")));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            reset();
        }
    }

    private class EnlargePlotAreaAction extends AbstractAction {

        public EnlargePlotAreaAction() {
            super("Enlarge", new ImageIcon(PlotPanel.class.getResource("images/resize-larger16.png")));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (canvas.margin > 0.05) {
                canvas.margin -= 0.05;
                canvas.graphics.projection.reset();
                repaint();
            }

            if (canvas.margin <= 0.05) {
                setEnabled(false);
            }

            if (!shrinkPlotAreaAction.isEnabled()) {
                shrinkPlotAreaAction.setEnabled(true);
            }
        }
    }

    private class ShrinkPlotAreaAction extends AbstractAction {

        public ShrinkPlotAreaAction() {
            super("Shrink", new ImageIcon(PlotPanel.class.getResource("images/resize-smaller16.png")));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (canvas.margin < 0.3) {
                canvas.margin += 0.05;
                canvas.graphics.projection.reset();
                repaint();
            }

            if (canvas.margin >= 0.3) {
                setEnabled(false);
            }

            if (!enlargePlotAreaAction.isEnabled()) {
                enlargePlotAreaAction.setEnabled(true);
            }
        }
    }

    private class IncreaseWidthAction extends AbstractAction {

        public IncreaseWidthAction() {
            super("Increase Width", new ImageIcon(PlotPanel.class.getResource("images/increase-width16.png")));
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
            super("Increase Height", new ImageIcon(PlotPanel.class.getResource("images/increase-height16.png")));
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
            super("Decrease Width", new ImageIcon(PlotPanel.class.getResource("images/decrease-width16.png")));
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
            super("Decrease Height", new ImageIcon(PlotPanel.class.getResource("images/decrease-height16.png")));
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
            super("Properties", new ImageIcon(PlotPanel.class.getResource("images/property16.png")));
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
        Base base = canvas.base;

        if (canvas.base.dimension == 2) {
            Object[][] data = {
                    {"Title", canvas.getTitle()},
                    {"Title Font", canvas.getTitleFont()},
                    {"Title Color", canvas.getTitleColor()},
                    {"X Axis Title", canvas.getAxis(0).getLabel()},
                    {"X Axis Range", new double[]{base.getLowerBounds()[0], base.getUpperBounds()[0]}},
                    {"Y Axis Title", canvas.getAxis(1).getLabel()},
                    {"Y Axis Range", new double[]{base.getLowerBounds()[1], base.getUpperBounds()[1]}}
            };

            propertyTable = new Table(data, columnNames);
        } else {
            Object[][] data = {
                    {"Title", canvas.getTitle()},
                    {"Title Font", canvas.getTitleFont()},
                    {"Title Color", canvas.getTitleColor()},
                    {"X Axis Title", canvas.getAxis(0).getLabel()},
                    {"X Axis Range", new double[]{base.getLowerBounds()[0], base.getUpperBounds()[0]}},
                    {"Y Axis Title", canvas.getAxis(1).getLabel()},
                    {"Y Axis Range", new double[]{base.getLowerBounds()[1], base.getUpperBounds()[1]}},
                    {"Z Axis Title", canvas.getAxis(2).getLabel()},
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
            canvas.setTitle((String) propertyTable.getValueAt(0, 1));
            canvas.setTitleFont((Font) propertyTable.getValueAt(1, 1));
            canvas.setTitleColor((Color) propertyTable.getValueAt(2, 1));

            canvas.getAxis(0).setLabel((String) propertyTable.getValueAt(3, 1));
            double[] xbound = (double[]) propertyTable.getValueAt(4, 1);
            canvas.base.lowerBound[0] = xbound[0];
            canvas.base.upperBound[0] = xbound[1];

            canvas.getAxis(1).setLabel((String) propertyTable.getValueAt(5, 1));
            double[] ybound = (double[]) propertyTable.getValueAt(6, 1);
            canvas.base.lowerBound[1] = ybound[0];
            canvas.base.upperBound[1] = ybound[1];

            if (canvas.base.dimension > 2) {
                canvas.getAxis(2).setLabel((String) propertyTable.getValueAt(7, 1));
                double[] zbound = (double[]) propertyTable.getValueAt(8, 1);
                canvas.base.lowerBound[2] = zbound[0];
                canvas.base.upperBound[2] = zbound[1];
            }

            for (int i = 0; i < canvas.base.dimension; i++) {
                canvas.base.setPrecisionUnit(i);
            }

            canvas.base.initBaseCoord();
            canvas.graphics.projection.reset();
            canvas.resetAxis();

            dialog.setVisible(false);
            contentPane.repaint();
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
        BufferedImage bi = canvas.toBufferedImage(contentPane.getWidth(), contentPane.getHeight());
        ImageIO.write(bi, FileChooser.getExtension(file), file);
    }

    /**
     * Prints the plot.
     */
    public void print() {
        Printer.getPrinter().print(contentPane);
    }

    /**
     * Zooms in/out the plot.
     * @param inout true if zoom in. Otherwise, zoom out.
     */
    public void zoom(boolean inout) {
        Base base = canvas.base;

        for (int i = 0; i < base.dimension; i++) {
            int s = canvas.axis[i].slices();
            double r = inout ? -1.0 / s : 1.0 / s;
            double d = (base.upperBound[i] - base.lowerBound[i]) * r;
            base.lowerBound[i] -= d;
            base.upperBound[i] += d;
        }

        for (int i = 0; i < base.dimension; i++) {
            base.setPrecisionUnit(i);
        }

        base.initBaseCoord();
        canvas.graphics.projection.reset();
        canvas.resetAxis();

        contentPane.repaint();
    }

    /**
     * Resets the plot.
     */
    public void reset() {
        Base base = canvas.base;
        Graphics graphics = canvas.graphics;

        base.reset();
        graphics.projection.reset();
        canvas.resetAxis();

        if (graphics.projection instanceof Projection3D) {
            ((Projection3D) graphics.projection).setDefaultView();
        }

        contentPane.repaint();
    }

    /** The number of created windows, as the default window title. */
    static AtomicInteger WindowCount = new AtomicInteger();

    /**
     * Shows the plot in a window.
     * @return a new JFrame that contains the plot.
     */
    public JFrame window() throws InterruptedException, InvocationTargetException  {
        JFrame frame = new JFrame();
        String title = canvas.getTitle();
        if (title != null) {
            title = String.format("Smile Plot %d", WindowCount.addAndGet(1));
        }
        frame.setTitle(title);

        add(toolbar, BorderLayout.NORTH);
        frame.getContentPane().add(this);

        frame.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        frame.setSize(new java.awt.Dimension(1280, 800));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        javax.swing.SwingUtilities.invokeAndWait(() -> {
            reset();
            repaint();
            frame.toFront();
        });

        return frame;
    }
}
