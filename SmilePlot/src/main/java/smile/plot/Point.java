/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/
package smile.plot;

import java.awt.Color;

/**
 * A single point in the plot.
 *
 * @author Haifeng Li
 */
public class Point extends Shape {

    /**
     * The coordinate of point.
     */
    double[] point;
    /**
     * The legend of points.
     */
    private char legend;

    /**
     * Constructor.
     */
    public Point(double... point) {
        this('o', point);
    }

    /**
     * Constructor.
     */
    public Point(char legend, double... point) {
        this(legend, Color.BLACK, point);
    }

    /**
     * Constructor.
     */
    public Point(Color color, double... point) {
        this('o', color, point);
    }

    /**
     * Constructor.
     */
    public Point(char legend, Color color, double... point) {
        super(color);
        this.legend = legend;
        this.point = point;
    }

    /**
     * Returns the legend of point.
     */
    public char getLegend() {
        return legend;
    }

    /**
     * Set the legend of point.
     */
    public void setLegend(char legend) {
        this.legend = legend;
    }

    @Override
    public void paint(Graphics g) {
        Color c = g.getColor();
        g.setColor(getColor());

        g.drawPoint(legend, point);
        g.setColor(c);
    }
}
