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
