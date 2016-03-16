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

package smile.demo.clustering;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import smile.plot.Palette;
import smile.vq.SOM;
import smile.math.Math;
import smile.plot.Hexmap;
import smile.plot.Histogram;
import smile.plot.PlotCanvas;
import smile.plot.ScatterPlot;
import smile.stat.distribution.GaussianMixture;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class SOMDemo  extends ClusteringDemo {
    JTextField widthField;
    JTextField heightField;
    int width = 10;
    int height = 10;

    public SOMDemo() {
        widthField = new JTextField(Integer.toString(width), 10);
        optionPane.add(new JLabel("Width:"));
        optionPane.add(widthField);

        heightField = new JTextField(Integer.toString(height), 10);
        optionPane.add(new JLabel("Height:"));
        optionPane.add(heightField);
    }

    @Override
    public JComponent learn() {
        try {
            width = Integer.parseInt(widthField.getText().trim());
            if (width < 1) {
                JOptionPane.showMessageDialog(this, "Invalid width: " + width, "Error", JOptionPane.ERROR_MESSAGE);
                return null;
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Invalid width: " + widthField.getText(), "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        try {
            height = Integer.parseInt(heightField.getText().trim());
            if (height < 1) {
                JOptionPane.showMessageDialog(this, "Invalid height: " + height, "Error", JOptionPane.ERROR_MESSAGE);
                return null;
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Invalid height: " + heightField.getText(), "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        long clock = System.currentTimeMillis();
        SOM som = new SOM(dataset[datasetIndex], width, height);
        System.out.format("SOM clusterings %d samples in %dms\n", dataset[datasetIndex].length, System.currentTimeMillis()-clock);

        JPanel pane = new JPanel(new GridLayout(2, 3));
        PlotCanvas plot = ScatterPlot.plot(dataset[datasetIndex], pointLegend);
        plot.grid(som.map());
        plot.setTitle("SOM Grid");
        pane.add(plot);

        int[] membership = som.partition(clusterNumber);
        int[] clusterSize = new int[clusterNumber];
        for (int i = 0; i < membership.length; i++) {
            clusterSize[membership[i]]++;
        }

        plot = ScatterPlot.plot(dataset[datasetIndex], pointLegend);
        plot.setTitle("Hierarchical Clustering");
        for (int k = 0; k < clusterNumber; k++) {
                double[][] cluster = new double[clusterSize[k]][];
                for (int i = 0, j = 0; i < dataset[datasetIndex].length; i++) {
                    if (membership[i] == k) {
                        cluster[j++] = dataset[datasetIndex][i];
                    }
                }

                plot.points(cluster, pointLegend, Palette.COLORS[k % Palette.COLORS.length]);
        }
        pane.add(plot);

        double[][] umatrix = som.umatrix();

        double[] umatrix1 = new double[umatrix.length * umatrix[0].length];
        for (int i = 0, k = 0; i < umatrix.length; i++) {
            for (int j = 0; j < umatrix[i].length; j++, k++)
                umatrix1[k] = umatrix[i][j];
        }

        plot = Histogram.plot(null, umatrix1, 20);
        plot.setTitle("U-Matrix Histogram");
        pane.add(plot);

        GaussianMixture mixture = new GaussianMixture(umatrix1);

        double w = (Math.max(umatrix1) - Math.min(umatrix1)) / 24;
        double[][] p = new double[50][2];
        for (int i = 0; i < p.length; i++) {
            p[i][0] = Math.min(umatrix1) + i * w;
            p[i][1] = mixture.p(p[i][0]) * w;
        }
        plot.line(p, Color.RED);

        plot = Hexmap.plot(umatrix, Palette.jet(256));
        plot.setTitle("U-Matrix");
        pane.add(plot);
/*
        double[][] x = new double[height][width];
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                x[i][j] = som.getMap()[i][j][0];
            }
        }
        plot = PlotCanvas.hexmap(x, Palette.jet(256));
        plot.setTitle("X");
        pane.add(plot);

        double[][] y = new double[height][width];
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                y[i][j] = som.getMap()[i][j][1];
            }
        }
        plot = PlotCanvas.hexmap(y, Palette.jet(256));
        plot.setTitle("Y");
        pane.add(plot);
*/
        return pane;
    }

    @Override
    public String toString() {
        return "SOM";
    }

    public static void main(String argv[]) {
        ClusteringDemo demo = new SOMDemo();
        JFrame f = new JFrame("SOM");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
