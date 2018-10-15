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

package smile.demo.projection;

import java.awt.Dimension;
import java.awt.GridLayout;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import smile.plot.Palette;
import smile.plot.PlotCanvas;
import smile.projection.ICA;
import smile.projection.ICA.NegEntropyFunc;
import smile.math.Math;

/**
 *
 * @author rayeaster
 */
@SuppressWarnings("serial")
public class ICADemo extends ProjectionDemo {
    JComboBox<String> corBox;

    public ICADemo() {
        corBox = new JComboBox<>();
        corBox.addItem("LOGCOSH");
        corBox.addItem("EXP");
        corBox.setSelectedIndex(0);

        optionPane.add(new JLabel("NegEntropy:"));
        optionPane.add(corBox);
    }

    @Override
    public JComponent learn() {
        double[][] data = dataset[datasetIndex].toArray(new double[dataset[datasetIndex].size()][]);
        String[] names = dataset[datasetIndex].toArray(new String[dataset[datasetIndex].size()]);
        if (names[0] == null) {
            names = null;
        }
        boolean logcosh = corBox.getSelectedIndex() != 0;

        long clock = System.currentTimeMillis();
        int icap = 2;
        switch (datasetIndex) {
        case 1: // USArrests
        case 2: // food
        case 4: // combo17
            icap = 3;
            break;
        default: // iris pendigits
            icap = 2;
            break;
        }
        ICA fastICA = new ICA(data, icap);
        if (!logcosh) {
            fastICA.setFuncMode(NegEntropyFunc.EXP);
        }
        System.out.format("Learn Independent Component Anaysis from %d samples in %dms\n", data.length,
                System.currentTimeMillis() - clock);

        JPanel pane = new JPanel(new GridLayout(1, 1));

        double[][] y = fastICA.project(data);

        PlotCanvas plot = new PlotCanvas(Math.colMin(y), Math.colMax(y));
        if (names != null) {
            plot.points(y, names);
        } else if (dataset[datasetIndex].responseAttribute() != null) {
            int[] labels = dataset[datasetIndex].toArray(new int[dataset[datasetIndex].size()]);
            for (int i = 0; i < y.length; i++) {
                plot.point(pointLegend, Palette.COLORS[labels[i]], y[i]);
            }
        } else {
            plot.points(y, pointLegend);
        }

        plot.setTitle("Scatter Plot");
        pane.add(plot);
        return pane;
    }

    @Override
    public String toString() {
        return "Independent Component Analysis";
    }

    public static void main(String argv[]) {
        ICADemo demo = new ICADemo();
        JFrame f = new JFrame("Independent Component Analysis");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
