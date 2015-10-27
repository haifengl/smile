/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
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
    public Label setText(String text) {
        this.text = text;
        return this;
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
    public Label setCoordinate(double... coord) {
        this.coord = coord;
        return this;
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
    public Label setRotation(double angle) {
        this.rotation = angle;
        return this;
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
    public Label setFont(Font font) {
        this.font = font;
        return this;
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
