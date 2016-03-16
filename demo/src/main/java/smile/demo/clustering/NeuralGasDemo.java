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

import java.awt.Dimension;

import javax.swing.JComponent;
import javax.swing.JFrame;

import smile.vq.NeuralGas;
import smile.plot.Palette;
import smile.plot.PlotCanvas;
import smile.plot.ScatterPlot;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class NeuralGasDemo extends ClusteringDemo {
    public NeuralGasDemo() {
    }

    @Override
    public JComponent learn() {

        long clock = System.currentTimeMillis();
        NeuralGas gas = new NeuralGas(dataset[datasetIndex], clusterNumber);
        System.out.format("Neural Gas clusterings %d samples in %dms\n", dataset[datasetIndex].length, System.currentTimeMillis()-clock);

        PlotCanvas plot = ScatterPlot.plot(gas.centroids(), '@');
        for (int k = 0; k < clusterNumber; k++) {
            if (gas.getClusterSize()[k] > 0) {
                double[][] cluster = new double[gas.getClusterSize()[k]][];
                for (int i = 0, j = 0; i < dataset[datasetIndex].length; i++) {
                    if (gas.getClusterLabel()[i] == k) {
                        cluster[j++] = dataset[datasetIndex][i];
                    }
                }

                plot.points(cluster, pointLegend, Palette.COLORS[k % Palette.COLORS.length]);
            }
        }
        plot.points(gas.centroids(), '@');
        return plot;
    }

    @Override
    public String toString() {
        return "Neural Gas";
    }

    public static void main(String argv[]) {
        ClusteringDemo demo = new NeuralGasDemo();
        JFrame f = new JFrame("Neural Gas");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
