/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/
package smile.plot;

import java.awt.Color;

/**
 * Abstract rendering object in a PlotCanvas.
 *
 * @author Haifeng Li
 */
public abstract class Shape {

    /**
     * The color of the shape. By default, it is black.
     */
    private Color color = Color.BLACK;

    /**
     * Constructor.
     */
    public Shape() {
    }

    /**
     * Constructor.
     */
    public Shape(Color color) {
        this.color = color;
    }

    /**
     * Set the color of component.
     */
    public void setColor(Color color) {
        this.color = color;
    }

    /**
     * Returns the color of component.
     */
    public Color getColor() {
        return color;
    }
    
    /**
     * Draw the component with given graphics object.
     * @param painter
     */
    public abstract void paint(Graphics painter);
}
