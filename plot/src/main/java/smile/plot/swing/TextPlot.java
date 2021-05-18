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

import java.util.Arrays;

/**
 * The scatter plot of texts.
 *
 * @author Haifeng Li
 */
public class TextPlot extends Plot {

    /**
     * The coordinates of points.
     */
    final Label[] texts;

    /**
     * Constructor.
     */
    public TextPlot(Label... texts) {
        this.texts = texts;
    }

    @Override
    public void paint(Graphics g) {
        for (Label text : texts) {
            text.paint(g);
        }
    }

    @Override
    public double[] getLowerBound() {
        double[] bound = new double[texts[0].coordinates.length];
        Arrays.fill(bound, Double.POSITIVE_INFINITY);
        for (Label text : texts) {
            double[] x = text.coordinates;
            for (int i = 0; i < x.length; i++) {
                if (bound[i] > x[i]) {
                    bound[i] = x[i];
                }
            }
        }

        return bound;
    }

    @Override
    public double[] getUpperBound() {
        double[] bound = new double[texts[0].coordinates.length];
        Arrays.fill(bound, Double.NEGATIVE_INFINITY);
        for (Label text : texts) {
            double[] x = text.coordinates;
            for (int i = 0; i < x.length; i++) {
                if (bound[i] < x[i]) {
                    bound[i] = x[i];
                }
            }
        }

        return bound;
    }

    /**
     * Create a text plot.
     * @param texts the texts.
     * @param coordinates a n-by-2 or n-by-3 matrix that are the coordinates of texts.
     */
    public static TextPlot of(String[] texts, double[][] coordinates) {
        if (texts.length != coordinates.length) {
            throw new IllegalArgumentException("The number of texts and that of coordinates are not the same.");
        }

        int n = texts.length;
        Label[] labels = new Label[n];
        for (int i = 0; i < n; i++) {
            labels[i] = Label.of(texts[i], coordinates[i]);
        }
        return new TextPlot(labels);
    }
}
