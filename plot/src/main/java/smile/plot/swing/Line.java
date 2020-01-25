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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Stroke;

/**
 * This class respesents a poly line in the plot.
 *
 * @author Haifeng Li
 */
public class Line extends Shape {
    /**
     * The supported styles of lines.
     */
    public enum Style {
        SOLID,
        DOT,
        DASH,
        DOT_DASH,
        LONG_DASH
    };
    /**
     * The stroke for solid line.
     */
    private static final BasicStroke SOLID_STROKE = new BasicStroke(1F, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);
    /**
     * The stroke for dotted line.
     */
    private static final BasicStroke DOT_STROKE = new BasicStroke(1F, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1F, new float[]{2F}, 0F);
    /**
     * The stroke for dash line.
     */
    private static final BasicStroke DASH_STROKE = new BasicStroke(1F, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1F, new float[]{10F}, 0F);
    /**
     * The stroke for dot-dash line.
     */
    private static final BasicStroke DOT_DASH_STROKE = new BasicStroke(1F, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1F, new float[]{10F, 2F, 2F, 2F}, 0F);
    /**
     * The stroke for long dash line.
     */
    private static final BasicStroke LONG_DASH_STROKE = new BasicStroke(1F, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1F, new float[]{20F}, 0F);
    /**
     * The stroke object to rendering the line.
     */
    private Stroke stroke;
    /**
     * The end points of the line.
     */
    double[][] points;

    /**
     * Constructor.
     */
    public Line(double[]... points) {
        this.points = points;
        stroke = SOLID_STROKE;
    }

    /**
     * Constructor.
     */
    public Line(Style style, double[]... points) {
        switch (style) {
            case SOLID:
                stroke = SOLID_STROKE;
                break;
            case DOT:
                stroke = DOT_STROKE;
                break;
            case DASH:
                stroke = DASH_STROKE;
                break;
            case DOT_DASH:
                stroke = DOT_DASH_STROKE;
                break;
            case LONG_DASH:
                stroke = LONG_DASH_STROKE;
                break;
            default:
                stroke = SOLID_STROKE;
                break;
        }

        this.points = points;
    }

    /**
     * Constructor.
     */
    public Line(Style style, Color color, double[]... points) {
        super(color);

        switch (style) {
            case SOLID:
                stroke = SOLID_STROKE;
                break;
            case DOT:
                stroke = DOT_STROKE;
                break;
            case DASH:
                stroke = DASH_STROKE;
                break;
            case DOT_DASH:
                stroke = DOT_DASH_STROKE;
                break;
            case LONG_DASH:
                stroke = LONG_DASH_STROKE;
                break;
        }

        this.points = points;
    }

    /**
     * Returns the line stroke style.
     */
    public Stroke getStroke() {
        return stroke;
    }

    /**
     * Set the line stroke style.
     */
    public Line setStroke(Stroke stroke) {
        this.stroke = stroke;
        return this;
    }

    @Override
    public void paint(Graphics g) {
        Color c = g.getColor();
        g.setColor(getColor());

        Stroke s = g.getStroke();
        if (stroke != null) {
            g.setStroke(stroke);
        }

        g.drawLine(points);

        if (stroke != null) {
            g.setStroke(s);
        }
        g.setColor(c);
    }
}
