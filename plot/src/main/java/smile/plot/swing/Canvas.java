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
import java.awt.event.*;
import java.util.Objects;
import java.util.Optional;
import javax.swing.*;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import smile.swing.Button;
import smile.swing.Table;

/**
 * Interactive view of a mathematical plot. For both 2D and 3D plot,
 * the user can zoom in/out by mouse wheel. For 2D plot, the user can
 * shift the coordinates by moving mouse after double click. The user
 * can also select an area by mouse for detailed view. For 3D plot,
 * the user can rotate the view by dragging mouse.
 */
public class Canvas extends JComponent implements ComponentListener,
        MouseListener, MouseMotionListener, MouseWheelListener, Scene {
    /**
     * The plot figure.
     */
    private final Figure figure;
    /**
     * The graphics object associated with this canvas.
     */
    private final Renderer renderer;
    /**
     * The toolbar to control plots.
     */
    private final JToolBar toolbar = new JToolBar();
    /**
     * The right-click popup menu.
     */
    private final JPopupMenu popup = new JPopupMenu();
    /**
     * Property table.
     */
    private JTable propertyTable;
    /**
     * If the mouse double-clicked.
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
    private Base baseClicked;

    /**
     * Constructor.
     * @param figure The plot figure.
     */
    public Canvas(Figure figure) {
        this.figure = figure;
        this.renderer = new Renderer(figure.projection(800, 800));

        setBackground(Color.WHITE);
        setDoubleBuffered(true);
        addComponentListener(this);
        addMouseListener(this);
        addMouseMotionListener(this);
        addMouseWheelListener(this);
        initContextMenuAndToolBar();
    }

    /**
     * Returns the figure in this canvas.
     * @return the figure in this canvas.
     */
    public Figure figure() {
        return figure;
    }

    @Override
    public JComponent content() {
        return this;
    }

    @Override
    public JToolBar toolbar() {
        return toolbar;
    }

    @Override
    public void paintComponent(Graphics g) {
        renderer.setGraphics((Graphics2D) g);
        renderer.projection().setSize(getWidth(), getHeight());
        figure.paint(renderer);

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
        if (figure.base.dimension == 2) {
            mouseDraggingX = e.getX();
            mouseDraggingY = e.getY();
        } else if (figure.base.dimension == 3) {
            renderer.rotate(e.getX() - mouseClickX, e.getY() - mouseClickY);
            mouseClickX = e.getX();
            mouseClickY = e.getY();
        }

        repaint();
        e.consume();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        Base base = figure.base;

        if (e.isPopupTrigger()) {
            popup.show(e.getComponent(), e.getX(), e.getY());
            e.consume();
        } else {
            if (mouseDraggingX != -1 && mouseDraggingY != -1) {
                mouseDraggingX = -1;
                mouseDraggingY = -1;

                if (base.dimension == 2) {
                    if (Math.abs(e.getX() - mouseClickX) > 20 && Math.abs(e.getY() - mouseClickY) > 20) {
                        double[] sc1 = ((Projection2D) (renderer.projection())).inverseProjection(mouseClickX, mouseClickY);
                        double[] sc2 = ((Projection2D) (renderer.projection())).inverseProjection(e.getX(), e.getY());

                        if (Math.min(sc1[0], sc2[0]) < base.upperBound[0]
                                && Math.max(sc1[0], sc2[0]) > figure.base.lowerBound[0]
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
                            renderer.projection().reset();
                            figure.resetAxis();
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
            baseClicked = figure.base;
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
    public void mouseMoved(MouseEvent e) {
        Base base = figure.base;

        if (base.dimension == 2) {
            if (mouseDoubleClicked) {
                double x = mouseClickX - e.getX();
                if (Math.abs(x) > 20) {
                    int s = figure.axes[0].slices();
                    x = x > 0 ? 1.0 / s : -1.0 / s;
                    x *= (baseClicked.upperBound[0] - baseClicked.lowerBound[0]);
                    base.lowerBound[0] = baseClicked.lowerBound[0] + x;
                    base.upperBound[0] = baseClicked.upperBound[0] + x;
                    mouseClickX = e.getX();
                }

                double y = mouseClickY - e.getY();
                if (Math.abs(y) > 20) {
                    int s = figure.axes[1].slices();
                    y = y > 0 ? -1.0 / s : 1.0 / s;
                    y *= (baseClicked.upperBound[1] - baseClicked.lowerBound[1]);
                    base.lowerBound[1] = baseClicked.lowerBound[1] + y;
                    base.upperBound[1] = baseClicked.upperBound[1] + y;
                    mouseClickY = e.getY();
                }

                base.initBaseCoord();
                renderer.projection().reset();
                figure.resetAxis();
                repaint();
            } else {
                StringBuilder tooltip = new StringBuilder();
                double[] sc = ((Projection2D) (renderer.projection())).inverseProjection(e.getX(), e.getY());

                for (Shape shape : figure.shapes) {
                    if (shape instanceof Plot plot) {
                        Optional<String> s = plot.tooltip(sc);
                        if (s.isPresent()) {
                            if (!tooltip.isEmpty()) {
                                tooltip.append("<br>");
                            }
                            tooltip.append(s);
                        }
                    }
                }

                if (!tooltip.isEmpty()) {
                    setToolTipText(String.format("<html>%s</html>", tooltip));
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

        Base base = figure.base;

        for (int i = 0; i < base.dimension; i++) {
            int s = figure.axes[i].slices();
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
        renderer.projection().reset();
        figure.resetAxis();

        repaint();
        e.consume();
    }

    @Override
    public void componentResized(ComponentEvent e) {
        Base base = figure.base;

        if (renderer != null) {
            base.initBaseCoord();
            renderer.projection().reset();
            figure.resetAxis();
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

    /**
     * Toolbar button actions.
     */
    private transient final Action saveAction = saveAction();
    private transient final Action printAction = printAction();
    private transient final Action zoomInAction = new ZoomInAction();
    private transient final Action zoomOutAction = new ZoomOutAction();
    private transient final Action resetAction = new ResetAction();
    private transient final Action enlargePlotAreaAction = new EnlargePlotAreaAction();
    private transient final Action shrinkPlotAreaAction = new ShrinkPlotAreaAction();
    private transient final Action propertyAction = new PropertyAction();
    private transient final Action increaseHeightAction = new IncreaseHeightAction();
    private transient final Action increaseWidthAction = new IncreaseWidthAction();
    private transient final Action decreaseHeightAction = new DecreaseHeightAction();
    private transient final Action decreaseWidthAction = new DecreaseWidthAction();
    private JScrollPane scrollPane;

    /**
     * Initialize context menu and toolbar.
     */
    private void initContextMenuAndToolBar() {
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
                    if (parent instanceof JScrollPane pane) {
                        inScrollPane = true;
                        scrollPane = pane;
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

    private class ZoomInAction extends AbstractAction {

        public ZoomInAction() {
            super("Zoom In", new ImageIcon(Objects.requireNonNull(Canvas.class.getResource("images/zoom-in16.png"))));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            zoom(true);
        }
    }

    private class ZoomOutAction extends AbstractAction {

        public ZoomOutAction() {
            super("Zoom Out", new ImageIcon(Objects.requireNonNull(Canvas.class.getResource("images/zoom-out16.png"))));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            zoom(false);
        }
    }

    private class ResetAction extends AbstractAction {

        public ResetAction() {
            super("Reset", new ImageIcon(Objects.requireNonNull(Canvas.class.getResource("images/refresh16.png"))));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            reset();
        }
    }

    private class EnlargePlotAreaAction extends AbstractAction {

        public EnlargePlotAreaAction() {
            super("Enlarge", new ImageIcon(Objects.requireNonNull(Canvas.class.getResource("images/resize-larger16.png"))));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (figure.margin > 0.05) {
                figure.margin -= 0.05;
                renderer.projection().reset();
                repaint();
            }

            if (figure.margin <= 0.05) {
                setEnabled(false);
            }

            if (!shrinkPlotAreaAction.isEnabled()) {
                shrinkPlotAreaAction.setEnabled(true);
            }
        }
    }

    private class ShrinkPlotAreaAction extends AbstractAction {

        public ShrinkPlotAreaAction() {
            super("Shrink", new ImageIcon(Objects.requireNonNull(Canvas.class.getResource("images/resize-smaller16.png"))));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (figure.margin < 0.3) {
                figure.margin += 0.05;
                renderer.projection().reset();
                repaint();
            }

            if (figure.margin >= 0.3) {
                setEnabled(false);
            }

            if (!enlargePlotAreaAction.isEnabled()) {
                enlargePlotAreaAction.setEnabled(true);
            }
        }
    }

    private class IncreaseWidthAction extends AbstractAction {

        public IncreaseWidthAction() {
            super("Increase Width", new ImageIcon(Objects.requireNonNull(Canvas.class.getResource("images/increase-width16.png"))));
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
            super("Increase Height", new ImageIcon(Objects.requireNonNull(Canvas.class.getResource("images/increase-height16.png"))));
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
            super("Decrease Width", new ImageIcon(Objects.requireNonNull(Canvas.class.getResource("images/decrease-width16.png"))));
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
            super("Decrease Height", new ImageIcon(Objects.requireNonNull(Canvas.class.getResource("images/decrease-height16.png"))));
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
            super("Properties", new ImageIcon(Objects.requireNonNull(Canvas.class.getResource("images/property16.png"))));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JDialog dialog = createPropertyDialog();
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
        Base base = figure.base;

        Object[][] data = figure.base.dimension == 2 ?
                new Object[][] {
                        {"Title", figure.getTitle()},
                        {"Title Font", figure.getTitleFont()},
                        {"Title Color", figure.getTitleColor()},
                        {"X Axis Title", figure.getAxis(0).getLabel()},
                        {"X Axis Range", new double[]{base.getLowerBounds()[0], base.getUpperBounds()[0]}},
                        {"Y Axis Title", figure.getAxis(1).getLabel()},
                        {"Y Axis Range", new double[]{base.getLowerBounds()[1], base.getUpperBounds()[1]}}
                } :
                new Object[][] {
                        {"Title", figure.getTitle()},
                        {"Title Font", figure.getTitleFont()},
                        {"Title Color", figure.getTitleColor()},
                        {"X Axis Title", figure.getAxis(0).getLabel()},
                        {"X Axis Range", new double[]{base.getLowerBounds()[0], base.getUpperBounds()[0]}},
                        {"Y Axis Title", figure.getAxis(1).getLabel()},
                        {"Y Axis Range", new double[]{base.getLowerBounds()[1], base.getUpperBounds()[1]}},
                        {"Z Axis Title", figure.getAxis(2).getLabel()},
                        {"Z Axis Range", new double[]{base.getLowerBounds()[2], base.getUpperBounds()[2]}}
                };

        propertyTable = new Table(data, columnNames);

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
        private final JDialog dialog;

        protected PropertyDialogOKAction(JDialog dialog) {
            this.dialog = dialog;
            putValue(Action.DEFAULT, ACTION_NAME);
            putValue(Action.ACTION_COMMAND_KEY, ACTION_NAME);
            putValue(Action.NAME, ACTION_NAME);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            figure.setTitle((String) propertyTable.getValueAt(0, 1));
            figure.setTitleFont((Font) propertyTable.getValueAt(1, 1));
            figure.setTitleColor((Color) propertyTable.getValueAt(2, 1));

            figure.getAxis(0).setLabel((String) propertyTable.getValueAt(3, 1));
            double[] xbound = (double[]) propertyTable.getValueAt(4, 1);
            figure.base.lowerBound[0] = xbound[0];
            figure.base.upperBound[0] = xbound[1];

            figure.getAxis(1).setLabel((String) propertyTable.getValueAt(5, 1));
            double[] ybound = (double[]) propertyTable.getValueAt(6, 1);
            figure.base.lowerBound[1] = ybound[0];
            figure.base.upperBound[1] = ybound[1];

            if (figure.base.dimension > 2) {
                figure.getAxis(2).setLabel((String) propertyTable.getValueAt(7, 1));
                double[] zbound = (double[]) propertyTable.getValueAt(8, 1);
                figure.base.lowerBound[2] = zbound[0];
                figure.base.upperBound[2] = zbound[1];
            }

            for (int i = 0; i < figure.base.dimension; i++) {
                figure.base.setPrecisionUnit(i);
            }

            figure.base.initBaseCoord();
            renderer.projection().reset();
            figure.resetAxis();

            dialog.setVisible(false);
            repaint();
        }
    }

    private static class PropertyDialogCancelAction extends AbstractAction {

        protected static final String ACTION_NAME = "Cancel";
        private final JDialog dialog;

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
     * Zooms in/out the plot.
     * @param inout true if zoom in. Otherwise, zoom out.
     */
    public void zoom(boolean inout) {
        Base base = figure.base;

        for (int i = 0; i < base.dimension; i++) {
            int s = figure.axes[i].slices();
            double r = inout ? -1.0 / s : 1.0 / s;
            double d = (base.upperBound[i] - base.lowerBound[i]) * r;
            base.lowerBound[i] -= d;
            base.upperBound[i] += d;
        }

        for (int i = 0; i < base.dimension; i++) {
            base.setPrecisionUnit(i);
        }

        base.initBaseCoord();
        renderer.projection().reset();
        figure.resetAxis();
        repaint();
    }

    /**
     * Resets the plot.
     */
    public void reset() {
        Base base = figure.base;

        base.reset();
        renderer.projection().reset();
        figure.resetAxis();

        if (renderer.projection() instanceof Projection3D p3d) {
            p3d.setDefaultView();
        }

        repaint();
    }
}
