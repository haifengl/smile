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
    public Point setLegend(char legend) {
        this.legend = legend;
        return this;
    }

    @Override
    public void paint(Graphics g) {
        Color c = g.getColor();
        g.setColor(getColor());

        g.drawPoint(legend, point);
        g.setColor(c);
    }
}
