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
import java.awt.GridLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import smile.clustering.HierarchicalClustering;
import smile.clustering.linkage.*;
import smile.plot.swing.Palette;
import smile.plot.swing.PlotCanvas;
import smile.plot.swing.Dendrogram;
import smile.plot.swing.ScatterPlot;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class HierarchicalClusteringDemo extends ClusteringDemo {
    JComboBox<String> linkageBox;

    public HierarchicalClusteringDemo() {
        linkageBox = new JComboBox<>();
        linkageBox.addItem("Single");
        linkageBox.addItem("Complete");
        linkageBox.addItem("UPGMA");
        linkageBox.addItem("WPGMA");
        linkageBox.addItem("UPGMC");
        linkageBox.addItem("WPGMC");
        linkageBox.addItem("Ward");
        linkageBox.setSelectedIndex(0);

        optionPane.add(new JLabel("Linkage:"));
        optionPane.add(linkageBox);
    }

    @Override
    public JComponent learn() {
        long clock = System.currentTimeMillis();
        double[][] data = dataset[datasetIndex];
        Linkage linkage;
        switch (linkageBox.getSelectedIndex()) {
            case 0:
                linkage = SingleLinkage.of(data);
                break;
            case 1:
                linkage = CompleteLinkage.of(data);
                break;
            case 2:
                linkage = UPGMALinkage.of(data);
                break;
            case 3:
                linkage = WPGMALinkage.of(data);
                break;
            case 4:
                linkage = UPGMCLinkage.of(data);
                break;
            case 5:
                linkage = WPGMCLinkage.of(data);
                break;
            case 6:
                linkage = WardLinkage.of(data);
                break;
            default:
                throw new IllegalStateException("Unsupported Linkage");
        }

        HierarchicalClustering hac = HierarchicalClustering.fit(linkage);
        System.out.format("Hierarchical clusterings %d samples in %dms\n", dataset[datasetIndex].length, System.currentTimeMillis()-clock);

        int[] membership = hac.partition(clusterNumber);
        int[] clusterSize = new int[clusterNumber];
        for (int i = 0; i < membership.length; i++) {
            clusterSize[membership[i]]++;
        }

        JPanel pane = new JPanel(new GridLayout(1, 3));
        PlotCanvas plot = ScatterPlot.plot(dataset[datasetIndex], pointLegend);
        plot.setTitle("Data");
        pane.add(plot);

        for (int k = 0; k < clusterNumber; k++) {
            double[][] cluster = new double[clusterSize[k]][];
            for (int i = 0, j = 0; i < dataset[datasetIndex].length; i++) {
                if (membership[i] == k) {
                    cluster[j++] = dataset[datasetIndex][i];
                }
            }

            plot.points(cluster, pointLegend, Palette.COLORS[k % Palette.COLORS.length]);
        }

        plot = Dendrogram.plot("Dendrogram", hac.getTree(), hac.getHeight());
        plot.setTitle("Dendrogram");
        pane.add(plot);
        return pane;
    }

    @Override
    public String toString() {
        return "Hierarchical Agglomerative Clustering";
    }

    public static void main(String argv[]) {
        ClusteringDemo demo = new HierarchicalClusteringDemo();
        JFrame f = new JFrame("Agglomerative Hierarchical Clustering");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
