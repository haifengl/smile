/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/
package smile.plot;

import java.awt.Color;
import java.awt.Font;
import smile.math.Math;

/**
 * Label is a single line text.
 *
 * @author Haifeng Li
 */
public class Label extends Shape {

    /**
     * The text of label.
     */
    String text;
    /**
     * The coordinates of label.
     */
    double[] coord;
    /**
     *  The reference position of coordinates respected to dimension of text.
     * (0.5, 0.5) is center, (0, 0) is lower left, (0, 1) is upper left, etc.
     */
    double horizontalReference;
    /**
     *  The reference position of coordinates respected to dimension of text.
     * (0.5, 0.5) is center, (0, 0) is lower left, (0, 1) is upper left, etc.
     */
    double verticalReference;
    /**
     * The rotation angel of text.
     */
    double rotation = 0.0;
    /**
     * The font for rendering the text. Use the system default font if this is
     * null.
     */
    Font font;

    /**
     * Constructor.
     */
    public Label(String text, double[] coord) {
        this(text, 0.5, 0.5, coord);
    }

    /**
     * Constructor.
     */
    public Label(String text, double rotation, double[] coord) {
        this(text, 0.5, 0.5, rotation, coord);
    }

    /**
     * Constructor.
     */
    public Label(String text, double horizontalReference, double verticalReference, double[] coord) {
        this(text, horizontalReference, verticalReference, 0.0, coord);
    }

    /**
     * Constructor. The reference position of coordinates respected to dimension
     * of text. (0.5, 0.5) is center, (0, 0) is lower left, (0, 1) is upper
     * left, etc.
     */
    public Label(String text, double horizontalReference, double verticalReference, double rotation, double[] coord) {
        this.text = text;
        this.horizontalReference = horizontalReference;
        this.verticalReference = verticalReference;
        this.rotation = rotation;
        this.coord = coord;
    }

    /**
     * Constructor. Use coordinates as text.
     */
    public Label(double... coord) {
        this(coordToString(coord), coord);
    }

    /**
     * Set the text of label.
     */
    public void setText(String text) {
        this.text = text;
    }

    /**
     * Returns the text of label.
     */
    public String getText() {
        return text;
    }

    /**
     * Set the coordinate of label.
     */
    public void setCoordinate(double... coord) {
        this.coord = coord;
    }

    /**
     * Returns the coordinates of label.
     */
    public double[] getCoordinate() {
        return coord;
    }

    /**
     * Set the rotation angel of label.
     */
    public void setRotation(double angle) {
        this.rotation = angle;
    }

    /**
     * Returns the rotation angel of label.
     */
    public double getRotation() {
        return rotation;
    }

    /**
     * Set the font of label.
     */
    public void setFont(Font font) {
        this.font = font;
    }

    /**
     * Returns the font of label.
     */
    public Font getFont() {
        return font;
    }

    @Override
    public void paint(Graphics g) {
        Font f = g.getFont();
        if (font != null) {
            g.setFont(font);
        }

        Color c = g.getColor();
        g.setColor(getColor());

        g.drawText(text, horizontalReference, verticalReference, rotation, coord);

        g.setColor(c);
        if (font != null) {
            g.setFont(f);
        }
    }

    /**
     * Convert coordinate to a string.
     */
    public static String coordToString(double... c) {
        StringBuilder builder = new StringBuilder("(");
        for (int i = 0; i < c.length; i++) {
            builder.append(Math.round(c[i], 2)).append(",");
        }

        if (c.length > 0) {
            builder.setCharAt(builder.length(), ')');
        } else {
            builder.append(")");
        }

        return builder.toString();
    }
}
