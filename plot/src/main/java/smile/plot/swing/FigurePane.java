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
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
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
 * Interactive pane for mathematical plots. For both 2D and 3D plot,
 * the user can zoom in/out by mouse wheel. For 2D plot, the user can
 * shift the coordinates by moving mouse after double click. The user
 * can also select an area by mouse for detailed view. For 3D plot,
 * the user can rotate the view by dragging mouse.
 *
 * @author Haifeng Li
 */
public class FigurePane extends JPanel {
    /**
     * The plot figure.
     */
    private final Figure figure;
    /**
     * The Swing component to draw the figure.
     */
    private final Canvas canvas;
    /**
     * Optional toolbar to control plots.
     */
    private final JToolBar toolbar = new JToolBar();
    /**
     * Right-click popup menu.
     */
    private final JPopupMenu popup = new JPopupMenu();;
    /**
     * Property table.
     */
    private JTable propertyTable;

    /**
     * Constructor
     * @param figure The plot figure.
     */
    public FigurePane(Figure figure) {
        super(new BorderLayout());
        this.figure = figure;
        this.canvas = new Canvas(figure, popup);
        add(canvas, BorderLayout.CENTER);
        initContextMenauAndToolBar();
        initComponentListener();
    }

    /**
     * Returns a toolbar to control the plot.
     * @return a toolbar to control the plot.
     */
    public JComponent getToolbar() {
        return toolbar;
    }

    /**
     * Toolbar button actions.
     */
    private transient final Action saveAction = new SaveAction();
    private transient final Action printAction = new PrintAction();
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
    private void initContextMenauAndToolBar() {
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

    /**
     * Listens to component events.
     */
    private void initComponentListener() {
        FigurePane pane = this;
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                pane.reset();
                pane.repaint();
            }

            @Override
            public void componentShown(ComponentEvent e) {
                pane.reset();
                pane.repaint();
            }
        });
    }

    private class SaveAction extends AbstractAction {

        public SaveAction() {
            super("Save", new ImageIcon(Objects.requireNonNull(FigurePane.class.getResource("images/save16.png"))));
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
            super("Print", new ImageIcon(Objects.requireNonNull(FigurePane.class.getResource("images/print16.png"))));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            print();
        }
    }

    private class ZoomInAction extends AbstractAction {

        public ZoomInAction() {
            super("Zoom In", new ImageIcon(Objects.requireNonNull(FigurePane.class.getResource("images/zoom-in16.png"))));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            zoom(true);
        }
    }

    private class ZoomOutAction extends AbstractAction {

        public ZoomOutAction() {
            super("Zoom Out", new ImageIcon(Objects.requireNonNull(FigurePane.class.getResource("images/zoom-out16.png"))));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            zoom(false);
        }
    }

    private class ResetAction extends AbstractAction {

        public ResetAction() {
            super("Reset", new ImageIcon(Objects.requireNonNull(FigurePane.class.getResource("images/refresh16.png"))));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            reset();
        }
    }

    private class EnlargePlotAreaAction extends AbstractAction {

        public EnlargePlotAreaAction() {
            super("Enlarge", new ImageIcon(Objects.requireNonNull(FigurePane.class.getResource("images/resize-larger16.png"))));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (figure.margin > 0.05) {
                figure.margin -= 0.05;
                figure.graphics.projection.reset();
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
            super("Shrink", new ImageIcon(Objects.requireNonNull(FigurePane.class.getResource("images/resize-smaller16.png"))));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (figure.margin < 0.3) {
                figure.margin += 0.05;
                figure.graphics.projection.reset();
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
            super("Increase Width", new ImageIcon(Objects.requireNonNull(FigurePane.class.getResource("images/increase-width16.png"))));
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
            super("Increase Height", new ImageIcon(Objects.requireNonNull(FigurePane.class.getResource("images/increase-height16.png"))));
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
            super("Decrease Width", new ImageIcon(Objects.requireNonNull(FigurePane.class.getResource("images/decrease-width16.png"))));
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
            super("Decrease Height", new ImageIcon(Objects.requireNonNull(FigurePane.class.getResource("images/decrease-height16.png"))));
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
            super("Properties", new ImageIcon(Objects.requireNonNull(FigurePane.class.getResource("images/property16.png"))));
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
            figure.graphics.projection.reset();
            figure.resetAxis();

            dialog.setVisible(false);
            canvas.repaint();
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
        BufferedImage bi = figure.toBufferedImage(canvas.getWidth(), canvas.getHeight());
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
        Base base = figure.base;

        for (int i = 0; i < base.dimension; i++) {
            int s = figure.axis[i].slices();
            double r = inout ? -1.0 / s : 1.0 / s;
            double d = (base.upperBound[i] - base.lowerBound[i]) * r;
            base.lowerBound[i] -= d;
            base.upperBound[i] += d;
        }

        for (int i = 0; i < base.dimension; i++) {
            base.setPrecisionUnit(i);
        }

        base.initBaseCoord();
        figure.graphics.projection.reset();
        figure.resetAxis();

        canvas.repaint();
    }

    /**
     * Resets the plot.
     */
    public void reset() {
        Base base = figure.base;
        Graphics graphics = figure.graphics;

        base.reset();
        graphics.projection.reset();
        figure.resetAxis();

        if (graphics.projection instanceof Projection3D p3d) {
            p3d.setDefaultView();
        }

        canvas.repaint();
    }

    /** The number of created windows, as the default window title. */
    static final AtomicInteger WindowCount = new AtomicInteger();

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
