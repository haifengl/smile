/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.plot.swing;

import java.util.ArrayList;
import java.util.List;

/**
 * Contour contains a list of segments.
 */
public class Isoline extends Shape {

    /**
     * The level value of contour line.
     */
    final double level;
    /**
     * The coordinates of points along the contour line.
     */
    private List<double[]> points = new ArrayList<>();
    /**
     * The label of contour line.
     */
    private Label label;
    /**
     * Show the value of isoline.
     */
    private boolean isLevelVisible;

    /**
     * Constructor.
     */
    public Isoline(double level, boolean isLevelVisible) {
        this.level = level;
        this.isLevelVisible = isLevelVisible;
    }

    /**
     * Returns true if the isoline doesn't have any points.
     */
    public boolean isEmpty() {
        return points.isEmpty();
    }

    /**
     * Add a point to the contour line.
     */
    public void add(double... point) {
        points.add(point);
    }

    /**
     * Paint the contour line. If the color attribute is null, the level
     * value of contour line will be shown along the line.
     */
    @Override
    public void paint(Graphics g) {
        double angle = 0.0;
        double horizontalReference = 0.0;
        double verticalReference = 0.0;
        double[] coord = null;

        if (points.size() > 1) {
            double[] x1 = points.get(0);
            for (int i = 1; i < points.size(); i++) {
                double[] x2 = points.get(i);
                g.drawLine(x1, x2);
                x1 = x2;

                if (label == null) {
                    if (i == points.size() / 2) {
                        coord = x1;

                        int k = i + 1;
                        if (k >= points.size()) {
                            k = i;
                        }

                        // compute label angle
                        angle = Math.PI / 2;

                        double dxx = points.get(k)[0] - points.get(i)[0];
                        double dyy = points.get(k)[1] - points.get(i)[1];
                        if (dyy < 0.0) {
                            angle = -Math.PI / 2;
                        }

                        if (dxx != 0.0) {
                            angle = Math.atan(dyy / dxx) + Math.PI / 2;
                        }
                    }
                }
            }
        } else if (points.size() == 1) {
            double[] x1 = points.get(0);
            g.drawPoint('@', x1);
            coord = x1;
            horizontalReference = 0.0;
        }

        if (isLevelVisible && label == null) {
            double[] lb = g.getLowerBound();
            double[] ub = g.getUpperBound();
            double xrange = ub[0] - lb[0];
            double yrange = ub[1] - lb[1];

            if (ub[0] - coord[0] < xrange / 10) {
                horizontalReference = 1.0;
            }

            if (ub[1] - coord[1] < yrange / 10) {
                horizontalReference = 1.0;
            }

            if (coord[1] - lb[1] < yrange / 10) {
                horizontalReference = 0.0;
            }

            label = Label.of(String.format("%.2G", level), coord, horizontalReference, verticalReference, angle);
        }

        if (label != null) {
            label.paint(g);
        }
    }
}