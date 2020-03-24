/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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
 *******************************************************************************/

package smile.plot.swing;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.event.SwingPropertyChangeSupport;

/**
 * Canvas for mathematical plots.
 *
 * @author Haifeng Li
 */
public class Canvas {

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
     * The coordinate grid plot.
     */
    BaseGrid baseGrid;
    /**
     * The shapes in the canvas, e.g. label, plots, etc.
     */
    List<Shape> shapes = new ArrayList<>();
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
     * Notify Swing listeners when a property changes.
     */
    private SwingPropertyChangeSupport pcs = new SwingPropertyChangeSupport(this, true);

    /**
     * Constructor
     */
    public Canvas(double[] lowerBound, double[] upperBound) {
        initBase(lowerBound, upperBound);
        initGraphics();
    }

    /**
     * Constructor
     */
    public Canvas(double[] lowerBound, double[] upperBound, boolean extendBound) {
        initBase(lowerBound, upperBound, extendBound);
        initGraphics();
    }

    /**
     * Constructor
     */
    public Canvas(double[] lowerBound, double[] upperBound, String[] axisLabels) {
        initBase(lowerBound, upperBound, axisLabels);
        initGraphics();
    }

    /**
     * Constructor
     */
    public Canvas(double[] lowerBound, double[] upperBound, String[] axisLabels, boolean extendBound) {
        initBase(lowerBound, upperBound, axisLabels, extendBound);
        initGraphics();
    }

    /**
     * Add a PropertyChangeListener to the listener list. The listener
     * is registered for all properties. The same listener object may
     * be added more than once, and will be called as many times as it
     * is added. If listener is null, no exception is thrown and no
     * action is taken.
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    /**
     * Remove a PropertyChangeListener from the listener list. This
     * removes a PropertyChangeListener that was registered for all
     * properties. If listener was added more than once to the same
     * event source, it will be notified one less time after being
     * removed. If listener is null, or was never added, no exception
     * is thrown and no action is taken.
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    /**
     * Returns an array of all the listeners that were added to
     * the PropertyChangeSupport object with addPropertyChangeListener().
     */
    public PropertyChangeListener[] getPropertyChangeListeners() {
        return pcs.getPropertyChangeListeners();
    }

    /**
     * Exports the plot to an image.
     *
     * @param width  the width of image.
     * @param height the height of image.
     */
    public BufferedImage toBufferedImage(int width, int height) {
        BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = bi.createGraphics();
        paint(g2d, width, height);
        return bi;
    }

    /**
     * Zooms in/out the plot.
     *
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

        PropertyChangeEvent event = new PropertyChangeEvent(this, "base", base, base);
        pcs.firePropertyChange(event);
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

        PropertyChangeEvent event = new PropertyChangeEvent(this, "canvas", this, this);
        pcs.firePropertyChange(event);
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
        baseGrid = new BaseGrid(base);
    }

    /**
     * Initialize a coordinate base.
     */
    private void initBase(double[] lowerBound, double[] upperBound, boolean extendBound) {
        base = new Base(lowerBound, upperBound, extendBound);
        baseGrid = new BaseGrid(base);
    }

    /**
     * Initialize a coordinate base.
     */
    private void initBase(double[] lowerBound, double[] upperBound, String[] axisLabels) {
        base = new Base(lowerBound, upperBound);
        baseGrid = new BaseGrid(base, axisLabels);
    }

    /**
     * Initialize a coordinate base.
     */
    private void initBase(double[] lowerBound, double[] upperBound, String[] axisLabels, boolean extendBound) {
        base = new Base(lowerBound, upperBound, extendBound);
        baseGrid = new BaseGrid(base, axisLabels);
    }

    /**
     * Returns the size of margin, which is not used as plot area.
     * Currently, all four sides have the same margin size.
     *
     * @return the size of margin.
     */
    public double getMargin() {
        return margin;
    }

    /**
     * Sets the size of margin in [0.0, 0.3] on each side. Currently, all four
     * sides have the same margin size.
     *
     * @param margin the size of margin.
     */
    public Canvas setMargin(double margin) {
        if (margin < 0.0 || margin >= 0.3) {
            throw new IllegalArgumentException("Invalid margin: " + margin);
        }

        PropertyChangeEvent event = new PropertyChangeEvent(this, "margin", this.margin, margin);
        this.margin = margin;
        pcs.firePropertyChange(event);
        return this;
    }

    /**
     * Returns the coordinate base.
     *
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
    public Canvas setTitle(String title) {
        PropertyChangeEvent event = new PropertyChangeEvent(this, "title", this.title, title);
        this.title = title;
        pcs.firePropertyChange(event);
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
    public Canvas setTitleFont(Font font) {
        PropertyChangeEvent event = new PropertyChangeEvent(this, "titleFont", this.titleFont, font);
        this.titleFont = font;
        pcs.firePropertyChange(event);
        return this;
    }

    /**
     * Set the color for title.
     */
    public Canvas setTitleColor(Color color) {
        PropertyChangeEvent event = new PropertyChangeEvent(this, "titleColor", this.titleColor, color);
        this.titleColor = color;
        pcs.firePropertyChange(event);
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
    public Canvas setAxisLabels(String... labels) {
        PropertyChangeEvent event = new PropertyChangeEvent(this, "axisLabels", baseGrid.getAxisLabel(), labels);
        baseGrid.setAxisLabel(labels);
        pcs.firePropertyChange(event);
        return this;
    }

    /**
     * Sets the label/legend of an axis.
     */
    public Canvas setAxisLabel(int axis, String label) {
        PropertyChangeEvent event = new PropertyChangeEvent(this, "axisLabel", baseGrid.getAxisLabel(axis), label);
        baseGrid.setAxisLabel(axis, label);
        pcs.firePropertyChange(event);
        return this;
    }

    /**
     * Returns the list of shapes in the canvas.
     *
     * @return the list of shapes in the canvas.
     */
    public List<Shape> getShapes() {
        return shapes;
    }

    /**
     * Add a graphical shape to the canvas.
     */
    public void add(Shape p) {
        PropertyChangeEvent event = new PropertyChangeEvent(this, "addShape", this, p);
        shapes.add(p);
        pcs.firePropertyChange(event);
    }

    /**
     * Remove a graphical shape from the canvas.
     */
    public void remove(Shape p) {
        PropertyChangeEvent event = new PropertyChangeEvent(this, "removeShape", this, p);
        shapes.remove(p);
        pcs.firePropertyChange(event);
    }

    /**
     * Add a graphical shape to the canvas.
     */
    public void add(Plot p) {
        PropertyChangeEvent event = new PropertyChangeEvent(this, "addPlot", this, p);
        shapes.add(p);
        extendBound(p.getLowerBound(), p.getUpperBound());
        pcs.firePropertyChange(event);
    }

    /**
     * Remove a graphical shape from the canvas.
     */
    public void remove(Plot p) {
        PropertyChangeEvent event = new PropertyChangeEvent(this, "removePlot", this, p);
        shapes.remove(p);
        pcs.firePropertyChange(event);
    }

    /**
     * Remove all graphic plots from the canvas.
     */
    public void clear() {
        PropertyChangeEvent event = new PropertyChangeEvent(this, "clear", this, null);
        shapes.clear();
        pcs.firePropertyChange(event);
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
        PropertyChangeEvent event = new PropertyChangeEvent(this, "extendLowerBound", this, bound);
        base.extendLowerBound(bound);
        baseGrid.setBase(base);
        pcs.firePropertyChange(event);
    }

    /**
     * Extend upper bounds.
     */
    public void extendUpperBound(double[] bound) {
        PropertyChangeEvent event = new PropertyChangeEvent(this, "extendUpperBound", this, bound);
        base.extendUpperBound(bound);
        baseGrid.setBase(base);
        pcs.firePropertyChange(event);
    }

    /**
     * Extend lower and upper bounds.
     */
    public void extendBound(double[] lowerBound, double[] upperBound) {
        PropertyChangeEvent event = new PropertyChangeEvent(this, "extendBound", this, new double[][]{lowerBound, upperBound});
        base.extendBound(lowerBound, upperBound);
        baseGrid.setBase(base);
        pcs.firePropertyChange(event);
    }

    /**
     * Paints the canvas.
     */
    public void paint(java.awt.Graphics2D g2d, int width, int height) {
        graphics.setGraphics(g2d, width, height);

        Color color = g2d.getColor();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);
        g2d.setColor(color);
        baseGrid.paint(graphics);

        // draw plot
        graphics.clip();
        // with for-each loop, we will get a ConcurrentModificationException.
        // Use for loop instead.
        for (int i = 0; i < shapes.size(); i++) {
            Shape shape = shapes.get(i);
            graphics.setColor(shape.color);
            shape.paint(graphics);
        }
        graphics.clearClip();

        // draw legends
        Font font = g2d.getFont();
        int x = (int) (width * (1 - margin) + 20);
        int y = (int) (height * margin + 50);
        int fontWidth = font.getSize();
        int fontHeight = font.getSize();

        for (int i = 0; i < shapes.size(); i++) {
            Shape s = shapes.get(i);
            if (s instanceof Plot) {
                Plot p = (Plot) s;
                if (p.legends().isPresent()) {
                    for (Legend legend : p.legends().get()) {
                        g2d.fillRect(x, y, fontWidth, fontHeight);
                        g2d.drawRect(x, y, fontWidth, fontHeight);
                        g2d.setColor(legend.color);
                        g2d.drawString(legend.text, x + 2 * fontWidth, y + fontHeight);
                        y += 2 * fontWidth;
                    }
                }
            }
        }

        if (title != null) {
            g2d.setFont(titleFont);
            g2d.setColor(titleColor);
            FontMetrics fm = g2d.getFontMetrics();
            x = (width - fm.stringWidth(title)) / 2;
            y = (int) (height * margin) / 2;
            g2d.drawString(title, x, y);
        }

        g2d.setColor(color);
    }

    /**
     * Returns a Swing JPanel of the canvas.
     */
    public PlotPanel panel() {
        return new PlotPanel(this);
    }

    /**
     * Shows the plot in a window.
     *
     * @return a new JFrame that contains the plot.
     */
    public JFrame window() throws InterruptedException, InvocationTargetException {
        return panel().window();
    }
}
