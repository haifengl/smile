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
import smile.math.MathEx;

/**
 * Label is a single line text.
 *
 * @author Haifeng Li
 */
public class Label extends Shape {
    /** The default JLabel font may vary on different systems. */
    private static final Font DefaultFont = new javax.swing.JLabel().getFont();

    /**
     * The text of label.
     */
    final String text;
    /**
     * The coordinates of label.
     */
    final double[] coordinates;
    /**
     * The reference position of coordinates respected to dimension of text.
     * (0.5, 0.5) is center, (0, 0) is lower left, (0, 1) is upper left, etc.
     */
    final double horizontalReference;
    /**
     * The reference position of coordinates respected to dimension of text.
     * (0.5, 0.5) is center, (0, 0) is lower left, (0, 1) is upper left, etc.
     */
    final double verticalReference;
    /**
     * The rotation angel of text.
     */
    final double rotation;
    /**
     * The font for rendering the text. Use the system default font if this is
     * null.
     */
    final Font font;

    /**
     * Constructor.
     * @param text the label text.
     * @param coordinates the label location.
     * @param horizontalReference the horizontal reference position of coordinates respected to dimension of text.
     * @param verticalReference the vertical reference position of coordinates respected to dimension of text.
     * @param rotation the rotation angel of text.
     * @param font the label font.
     * @param color the label color.
     */
    public Label(String text, double[] coordinates, double horizontalReference, double verticalReference, double rotation, Font font, Color color) {
        super(color);
        this.text = text;
        this.coordinates = coordinates;
        this.horizontalReference = horizontalReference;
        this.verticalReference = verticalReference;
        this.rotation = rotation;
        this.font = font;
    }

    @Override
    public void paint(Renderer g) {
        Font f = g.getFont();
        if (font != null) g.setFont(font);

        Color c = g.getColor();
        g.setColor(color);

        g.drawText(text, coordinates, horizontalReference, verticalReference, rotation);

        g.setColor(c);
        if (font != null) g.setFont(f);
    }

    /**
     * Convert coordinate to a string.
     */
    private static String coordinatesToString(double... coord) {
        StringBuilder builder = new StringBuilder("(");
        for (double v : coord) {
            builder.append(MathEx.round(v, 2)).append(",");
        }

        if (coord.length > 0) {
            builder.setCharAt(builder.length(), ')');
        } else {
            builder.append(")");
        }

        return builder.toString();
    }

    /**
     * Creates a black label centered at the coordinates.
     * @param text the label text.
     * @param coordinates the label coordinates.
     * @return the label.
     */
    public static Label of(String text, double[] coordinates) {
        return Label.of(text, coordinates, 0.5, 0.5, 0.0);
    }

    /**
     * Creates a black label with coordinates as text.
     * @param coordinates the label coordinates.
     * @return the label.
     */
    public static Label of(double... coordinates) {
        return Label.of(coordinatesToString(coordinates), coordinates, 0.5, 0.5, 0.0);
    }

    /**
     * Creates a black label with system default font.
     * @param text the label text.
     * @param coordinates the label coordinates.
     * @param horizontalReference the horizontal reference position of coordinates respected to dimension of text.
     * @param verticalReference the vertical reference position of coordinates respected to dimension of text.
     * @param rotation the rotation angel of text.
     * @return the label.
     */
    public static Label of(String text, double[] coordinates, double horizontalReference, double verticalReference, double rotation) {
        return new Label(text, coordinates, horizontalReference, verticalReference, rotation, DefaultFont, Color.BLACK);
    }
}
