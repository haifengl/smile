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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
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
 * A figure serves as the canvas on which plots and other elements are drawn.
 * It can be conceptualized as the top-level container holding all plot
 * elements, including axes, titles, legends, and annotations.
 *
 * @author Haifeng Li
 */
public class Figure {
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
     * The portion of the canvas used for margin outside axes.
     */
    double margin = DEFAULT_MARGIN;
    /**
     * The axis objects.
     */
    Axis[] axes;
    /**
     * The shapes in the canvas, e.g. label, plots, etc.
     */
    final List<Shape> shapes = new ArrayList<>();
    /**
     * Show legends if true.
     */
    private boolean isLegendVisible = true;
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
    private final SwingPropertyChangeSupport pcs = new SwingPropertyChangeSupport(this, true);

    /**
     * Constructor.
     * @param lowerBound the lower bound of base.
     * @param upperBound the upper bound of base.
     */
    public Figure(double[] lowerBound, double[] upperBound) {
        this(lowerBound, upperBound, true);
    }

    /**
     * Constructor.
     * @param lowerBound the lower bound of base.
     * @param upperBound the upper bound of base.
     * @param extendBound true if extending the bounds for padding.
     */
    public Figure(double[] lowerBound, double[] upperBound, boolean extendBound) {
        initBase(lowerBound, upperBound, extendBound);
    }

    /**
     * Add a PropertyChangeListener to the listener list. The listener
     * is registered for all properties. The same listener object may
     * be added more than once, and will be called as many times as it
     * is added. If listener is null, no exception is thrown and no
     * action is taken.
     * @param listener a property change listener.
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
     * @param listener a property change listener.
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    /**
     * Returns an array of all the listeners that were added to
     * the PropertyChangeSupport object with addPropertyChangeListener().
     * @return the registered property change listeners.
     */
    public PropertyChangeListener[] getPropertyChangeListeners() {
        return pcs.getPropertyChangeListeners();
    }

    /**
     * Returns a projection of the figure.
     * @param width  the canvas width.
     * @param height the canvas height.
     * @return a projection of the figure.
     */
    public Projection projection(int width, int height) {
        Projection projection;
        if (base.dimension == 2) {
            projection = new Projection2D(this);
        } else {
            projection = new Projection3D(this);
        }
        projection.setSize(width, height);
        return projection;
    }

    /**
     * Exports the figure to an image.
     *
     * @param width  the width of image.
     * @param height the height of image.
     * @return the image of figure.
     */
    public BufferedImage toBufferedImage(int width, int height) {
        Renderer renderer = new Renderer(projection(width, height));
        BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = bi.createGraphics();
        renderer.setGraphics(g2d);
        paint(renderer);
        return bi;
    }

    /**
     * Initialize a coordinate base.
     */
    private void initBase(double[] lowerBound, double[] upperBound, boolean extendBound) {
        base = new Base(lowerBound, upperBound, extendBound);
        axes = new Axis[base.getDimension()];
        for (int i = 0; i < base.getDimension(); i++) {
            axes[i] = new Axis(base, i);
        }
    }

    /**
     * Resets the grid (when the base changes).
     */
    void resetAxis() {
        for (var ax : axes) {
            ax.reset();
        }
    }

    /**
     * Returns true if legends are visible.
     * @return true if legends are visible.
     */
    public boolean isLegendVisible() {
        return isLegendVisible;
    }

    /**
     * Sets if legends are visible.
     * @param visible the flag if legends are visible.
     * @return this object.
     */
    public Figure setLegendVisible(boolean visible) {
        isLegendVisible = visible;
        return this;
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
     * @return this object.
     */
    public Figure setMargin(double margin) {
        if (margin < 0.0 || margin >= 0.3) {
            throw new IllegalArgumentException("Invalid margin: " + margin);
        }

        PropertyChangeEvent event = new PropertyChangeEvent(this, "margin", this.margin, margin);
        this.margin = margin;
        pcs.firePropertyChange(event);
        return this;
    }

    /**
     * Returns the main title of figure.
     * @return the main title of figure.
     */
    public String getTitle() {
        return title;
    }

    /**
     * Sets the main title of figure.
     * @param title the main title of figure.
     * @return this object.
     */
    public Figure setTitle(String title) {
        PropertyChangeEvent event = new PropertyChangeEvent(this, "title", this.title, title);
        this.title = title;
        pcs.firePropertyChange(event);
        return this;
    }

    /**
     * Returns the font for title.
     * @return the font for title.
     */
    public Font getTitleFont() {
        return titleFont;
    }

    /**
     * Sets the font for title.
     * @param font the font for title.
     * @return this object.
     */
    public Figure setTitleFont(Font font) {
        PropertyChangeEvent event = new PropertyChangeEvent(this, "titleFont", this.titleFont, font);
        this.titleFont = font;
        pcs.firePropertyChange(event);
        return this;
    }

    /**
     * Returns the color for title.
     * @return the color for title.
     */
    public Color getTitleColor() {
        return titleColor;
    }

    /**
     * Sets the color for title.
     * @param color the color for title.
     * @return this object.
     */
    public Figure setTitleColor(Color color) {
        PropertyChangeEvent event = new PropertyChangeEvent(this, "titleColor", this.titleColor, color);
        this.titleColor = color;
        pcs.firePropertyChange(event);
        return this;
    }

    /**
     * Returns the i-<i>th</i> axis.
     * @param i the index of axis.
     * @return the i-<i>th</i> axis.
     */
    public Axis getAxis(int i) {
        return axes[i];
    }

    /**
     * Returns the labels/legends of axes.
     * @return the labels/legends of axes.
     */
    public String[] getAxisLabels() {
        String[] labels = new String[base.dimension];
        for (int i = 0; i < base.dimension; i++) {
            labels[i] = axes[i].getLabel();
        }
        return labels;
    }

    /**
     * Returns the label/legend of an axis.
     * @param i the index of axis.
     * @return the label/legend of an axis.
     */
    public String getAxisLabel(int i) {
        return axes[i].getLabel();
    }

    /**
     * Sets the labels/legends of axes.
     * @param labels the labels/legends of axes.
     * @return this object.
     */
    public Figure setAxisLabels(String... labels) {
        PropertyChangeEvent event = new PropertyChangeEvent(this, "axisLabels", getAxisLabels(), labels);
        for (int i = 0; i < labels.length; i++) {
            axes[i].setLabel(labels[i]);
        }
        pcs.firePropertyChange(event);
        return this;
    }

    /**
     * Sets the label/legend of an axis.
     * @param i the index of axis.
     * @param label the label/legend of axes.
     * @return this object.
     */
    public Figure setAxisLabel(int i, String label) {
        PropertyChangeEvent event = new PropertyChangeEvent(this, "axisLabel", axes[i].getLabel(), label);
        axes[i].setLabel(label);
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
     * Adds a graphical shape to the canvas.
     * @param p the shape.
     */
    public void add(Shape p) {
        PropertyChangeEvent event = new PropertyChangeEvent(this, "addShape", this, p);
        shapes.add(p);
        pcs.firePropertyChange(event);
    }

    /**
     * Removes a graphical shape from the canvas.
     * @param p the shape.
     */
    public void remove(Shape p) {
        PropertyChangeEvent event = new PropertyChangeEvent(this, "removeShape", this, p);
        shapes.remove(p);
        pcs.firePropertyChange(event);
    }

    /**
     * Adds a graphical shape to the canvas.
     * @param p the plot.
     */
    public void add(Plot p) {
        PropertyChangeEvent event = new PropertyChangeEvent(this, "addPlot", this, p);
        shapes.add(p);
        extendBound(p.getLowerBound(), p.getUpperBound());
        pcs.firePropertyChange(event);
    }

    /**
     * Removes a graphical shape from the canvas.
     * @param p the plot.
     */
    public void remove(Plot p) {
        PropertyChangeEvent event = new PropertyChangeEvent(this, "removePlot", this, p);
        shapes.remove(p);
        pcs.firePropertyChange(event);
    }

    /**
     * Removes all graphic plots from the canvas.
     */
    public void clear() {
        PropertyChangeEvent event = new PropertyChangeEvent(this, "clear", this, null);
        shapes.clear();
        pcs.firePropertyChange(event);
    }

    /**
     * Returns the lower bounds.
     * @return the lower bounds.
     */
    public double[] getLowerBounds() {
        return base.lowerBound;
    }

    /**
     * Returns the upper bounds.
     * @return the upper bounds.
     */
    public double[] getUpperBounds() {
        return base.upperBound;
    }

    /**
     * Extends the lower bounds.
     * @param bound the new lower bounds.
     */
    public void extendLowerBound(double[] bound) {
        PropertyChangeEvent event = new PropertyChangeEvent(this, "extendLowerBound", this, bound);
        base.extendLowerBound(bound);
        resetAxis();
        pcs.firePropertyChange(event);
    }

    /**
     * Extends the upper bounds.
     * @param bound the new upper bounds.
     */
    public void extendUpperBound(double[] bound) {
        PropertyChangeEvent event = new PropertyChangeEvent(this, "extendUpperBound", this, bound);
        base.extendUpperBound(bound);
        resetAxis();
        pcs.firePropertyChange(event);
    }

    /**
     * Extends the lower and upper bounds.
     * @param lowerBound the new lower bounds.
     * @param upperBound the new upper bounds.
     */
    public void extendBound(double[] lowerBound, double[] upperBound) {
        PropertyChangeEvent event = new PropertyChangeEvent(this, "extendBound", this, new double[][]{lowerBound, upperBound});
        base.extendBound(lowerBound, upperBound);
        resetAxis();
        pcs.firePropertyChange(event);
    }

    /**
     * Sets the lower and upper bounds.
     * @param lowerBound the new lower bounds.
     * @param upperBound the new upper bounds.
     */
    public void setBound(double[] lowerBound, double[] upperBound) {
        PropertyChangeEvent event = new PropertyChangeEvent(this, "setBound", this, new double[][]{lowerBound, upperBound});
        base.setBound(lowerBound, upperBound);
        resetAxis();
        pcs.firePropertyChange(event);
    }

    /**
     * Paints the figure.
     * @param renderer the renderer.
     */
    public void paint(Renderer renderer) {
        Graphics2D g2d = renderer.getGraphics();
        int width = renderer.projection().width();
        int height = renderer.projection().height();

        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);

        for (var axis : axes) {
            axis.paint(renderer);
        }

        // draw plot
        renderer.clip();
        // with for-each loop, we will get a ConcurrentModificationException.
        // Use for loop instead.
        for (var shape : shapes) {
            renderer.setColor(shape.color);
            shape.paint(renderer);
        }
        renderer.clearClip();

        // draw legends
        if (isLegendVisible) {
            Font font = g2d.getFont();
            int x = (int) (width * (1 - margin) + 20);
            int y = (int) (height * margin + 50);
            int fontWidth = font.getSize();
            int fontHeight = font.getSize();

            for (var shape : shapes) {
                if (shape instanceof Plot p) {
                    if (p.legends().isPresent()) {
                        for (Legend legend : p.legends().get()) {
                            g2d.setColor(legend.color);
                            g2d.fillRect(x, y, fontWidth, fontHeight);
                            g2d.drawRect(x, y, fontWidth, fontHeight);
                            g2d.drawString(legend.text, x + 2 * fontWidth, y + fontHeight);
                            y += 2 * fontWidth;
                        }
                    }
                }
            }
        }

        if (title != null) {
            g2d.setFont(titleFont);
            g2d.setColor(titleColor);
            FontMetrics fm = g2d.getFontMetrics();
            int x = (width - fm.stringWidth(title)) / 2;
            int y = (int) (height * margin) / 2;
            g2d.drawString(title, x, y);
        }
    }

    /**
     * Shows the plot in a window.
     * @return a new JFrame that contains the plot.
     * @throws InterruptedException if we're interrupted while waiting for the event dispatching thread to finish executing.
     * @throws InvocationTargetException if an exception is thrown while showing the frame.
     */
    public JFrame show() throws InterruptedException, InvocationTargetException {
        var pane = new FigurePane(this);
        return pane.window();
    }
}
