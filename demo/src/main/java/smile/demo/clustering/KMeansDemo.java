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

package smile.demo.clustering;

import java.awt.Dimension;
import javax.swing.JComponent;
import javax.swing.JFrame;

import smile.clustering.KMeans;
import smile.plot.swing.Palette;
import smile.plot.swing.PlotCanvas;
import smile.plot.swing.ScatterPlot;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class KMeansDemo extends ClusteringDemo {
    public KMeansDemo() {
    }

    @Override
    public JComponent learn() {
        long clock = System.currentTimeMillis();
        KMeans kmeans = KMeans.fit(dataset[datasetIndex], clusterNumber, 100, 4);
        System.out.format("K-Means clusterings %d samples in %dms\n", dataset[datasetIndex].length, System.currentTimeMillis()-clock);

        PlotCanvas plot = ScatterPlot.plot(dataset[datasetIndex], kmeans.y, pointLegend, Palette.COLORS);
        plot.points(kmeans.centroids, '@');
        return plot;
    }

    @Override
    public String toString() {
        return "K-Means";
    }

    public static void main(String argv[]) {
        ClusteringDemo demo = new KMeansDemo();
        JFrame f = new JFrame("K-Means");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo( null );
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
