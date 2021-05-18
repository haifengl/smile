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

import java.awt.Color;
import java.util.Optional;

import smile.projection.PCA;

/**
 * In multivariate statistics, a scree plot is a line plot of the eigenvalues
 * of factors or principal components in an analysis. The scree plot is used
 * to determine the number of factors to retain in an exploratory factor
 * analysis (FA) or principal components to keep in a principal component
 * analysis (PCA). The procedure of finding statistically significant
 * factors or components using a scree plot is also known as a scree test.
 * <p>
 * A scree plot always displays the eigenvalues in a downward curve,
 * ordering the eigenvalues from largest to smallest. According to the
 * scree test, the "elbow" of the graph where the eigenvalues seem to
 * level off is found and factors or components to the left of this point
 * should be retained as significant. It is named after its resemblance
 * to scree after its elbow.
 *
 * @author Haifeng Li
 */
public class ScreePlot extends Plot {
    /** The line legends. */
    private static Legend[] legends = {
            new Legend("Variance", Color.RED),
            new Legend("Cumulative Variance", Color.BLUE)
    };

    /** The principal component analysis object. */
    private PCA pca;
    /** The mark of x-axis. */
    private double[] x;
    /** The label of x-axis. */
    private String[] labels;
    /** The variance & cumulative variance plot. */
    private Line[] lines;

    /**
     * Constructor.
     * @param pca principal component analysis object.
     */
    public ScreePlot(PCA pca) {
        this.pca = pca;
        int n = pca.varianceProportion().length;

        labels = new String[n];
        x = new double[n];
        double[][] var = new double[n][2];
        double[][] cumVar = new double[n][2];
        for (int i = 0; i < n; i++) {
            labels[i] = "PC" + (i + 1);
            x[i] = i + 1;
            var[i][0] = x[i];
            var[i][1] = pca.varianceProportion()[i];
            cumVar[i][0] = x[i];
            cumVar[i][1] = pca.cumulativeVarianceProportion()[i];
        }

        lines = new Line[] {
                new Line(var, Line.Style.SOLID, '@', Color.RED),
                new Line(cumVar, Line.Style.SOLID, '@', Color.BLUE)
        };
    }

    @Override
    public void paint(Graphics g) {
        for (Line line : lines) {
            line.paint(g);
        }
    }

    @Override
    public Optional<Legend[]> legends() {
        return Optional.of(legends);
    }

    @Override
    public Canvas canvas() {
        Canvas canvas = new Canvas(getLowerBound(), getUpperBound(), false);
        canvas.setAxisLabels("Principal Component", "Proportion of Variance");
        canvas.getAxis(0).setTicks(labels, x);
        canvas.add(this);
        return canvas;
    }

    @Override
    public double[] getLowerBound() {
        double[] bound = {1, 0.0};
        return bound;
    }

    @Override
    public double[] getUpperBound() {
        int n = pca.varianceProportion().length;
        double[] bound = {n, 1.0};
        return bound;
    }
}
