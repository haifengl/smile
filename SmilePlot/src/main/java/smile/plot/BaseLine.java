/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.plot;

import java.awt.Color;

/**
 * This is specialized line for axis lines. Coordinates used here are are
 * proportional to the base coordinates.
 * @author Haifeng Li
 */
class BaseLine extends Line {

    /**
     * Constructor.
     * @param start the start point of the line.
     * @param end   the end point of the line.
     */
    public BaseLine(double[] start, double[] end) {
        super(start, end);
    }

    @Override
    public void paint(Graphics g) {
        Color c = g.getColor();
        g.setColor(getColor());
        g.drawLineBaseRatio(points);
        g.setColor(c);
    }
}
