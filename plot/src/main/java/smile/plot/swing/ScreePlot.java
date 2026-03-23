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
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.plot.swing;

import java.awt.Color;
import java.util.Optional;

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
    private static final Legend[] legends = {
            new Legend("Variance", Color.RED),
            new Legend("Cumulative Variance", Color.BLUE)
    };

    /** The mark of x-axis. */
    private final double[] x;
    /** The label of x-axis. */
    private final String[] labels;
    /** The variance & cumulative variance plot. */
    private final Line[] lines;

    /**
     * Constructor.
     * @param varianceProportion The proportion of variance contained in each principal component.
     */
    public ScreePlot(double[] varianceProportion) {
        int n = varianceProportion.length;

        labels = new String[n];
        x = new double[n];
        double[][] variance = new double[n][2];
        double[][] cumVar = new double[n][2];
        for (int i = 0; i < n; i++) {
            labels[i] = "PC" + (i + 1);
            x[i] = i + 1;
            variance[i][0] = x[i];
            variance[i][1] = varianceProportion[i];
            cumVar[i][0] = x[i];
            cumVar[i][1] = i == 0 ? varianceProportion[0] : cumVar[i - 1][1] + varianceProportion[i];
        }

        lines = new Line[] {
                new Line(variance, Line.Style.SOLID, '@', Color.RED),
                new Line(cumVar, Line.Style.SOLID, '@', Color.BLUE)
        };
    }

    @Override
    public void paint(Renderer g) {
        for (Line line : lines) {
            line.paint(g);
        }
    }

    @Override
    public Optional<Legend[]> legends() {
        return Optional.ofNullable(legends);
    }

    @Override
    public Figure figure() {
        Figure canvas = new Figure(getLowerBound(), getUpperBound(), false);
        canvas.setAxisLabels("Principal Component", "Proportion of Variance");
        canvas.getAxis(0).setTicks(labels, x);
        canvas.add(this);
        return canvas;
    }

    @Override
    public double[] getLowerBound() {
        return new double[]{1, 0.0};
    }

    @Override
    public double[] getUpperBound() {
        return new double[]{x.length, 1.0};
    }
}
