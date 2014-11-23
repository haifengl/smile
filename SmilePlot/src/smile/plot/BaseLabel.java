/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/
package smile.plot;

import java.awt.Color;
import java.awt.Font;

/**
 * This is specialized label for axis labels. Coordinates used here are are
 * proportional to the base coordinates.
 *
 * @author Haifeng Li
 */
class BaseLabel extends Label {

    /**
     * Constructor.
     */
    public BaseLabel(String text, double horizontalReference, double verticalReference, double... coord) {
        super(text, horizontalReference, verticalReference, 0.0, coord);
    }

    /**
     * Constructor.
     */
    public BaseLabel(String text, double horizontalReference, double verticalReference, double rotation, double... coord) {
        super(text, horizontalReference, verticalReference, rotation, coord);
    }

    @Override
    public void paint(Graphics g) {
        Font f = g.getFont();
        if (font != null) {
            g.setFont(font);
        }

        Color c = g.getColor();
        g.setColor(getColor());

        g.drawTextBaseRatio(text, horizontalReference, verticalReference, rotation, coord);

        g.setColor(c);
        if (font != null) {
            g.setFont(f);
        }
    }
}