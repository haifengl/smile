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

package smile.demo.plot;

import java.awt.Color;
import java.awt.GridLayout;

import javax.swing.JFrame;
import javax.swing.JPanel;

import smile.data.AttributeDataset;
import smile.data.parser.microarray.RESParser;
import smile.plot.Contour;
import smile.plot.Heatmap;
import smile.plot.Palette;
import smile.plot.PlotCanvas;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class HeatmapDemo extends JPanel {
    public HeatmapDemo() {
        super(new GridLayout(2,4));
        setBackground(Color.white);

        int n = 81;
        double[] x = new double[n];
        for (int i = 0; i < n; i++)
            x[i] = -2.0 + 0.05 * i;

        int m = 81;
        double[] y = new double[m];
        for (int i = 0; i < m; i++)
            y[i] = -2.0 + 0.05 * i;

        double[][] z = new double[m][n];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++)
                z[i][j] = x[j] * Math.exp(-x[j]*x[j] - y[i]*y[i]);
        }

        PlotCanvas canvas = Heatmap.plot(z, Palette.jet(256));
        canvas.add(new Contour(z));
        canvas.setTitle("jet");
        add(canvas);
        canvas = Heatmap.plot(x, y, z, Palette.redblue(256));
        canvas.add(new Contour(x, y, z));
        canvas.setTitle("redblue");
        add(canvas);
        canvas = Heatmap.plot(z, Palette.redgreen(256));
        canvas.add(new Contour(z));
        canvas.setTitle("redgreen");
        add(canvas);
        canvas = Heatmap.plot(x, y, z, Palette.heat(256));
        canvas.add(new Contour(x, y, z));
        canvas.setTitle("heat");
        add(canvas);
        canvas = Heatmap.plot(z, Palette.terrain(256));
        canvas.add(new Contour(z));
        canvas.setTitle("terrain");
        add(canvas);
        canvas = Heatmap.plot(x, y, z, Palette.rainbow(256));
        canvas.add(new Contour(x, y, z));
        canvas.setTitle("rainbow");
        add(canvas);
        canvas = Heatmap.plot(z, Palette.topo(256));
        canvas.add(new Contour(z));
        canvas.setTitle("topo");
        add(canvas);
    }

    @Override
    public String toString() {
        return "Heatmap";
    }

    public static void main(String[] args) {
        try {
            RESParser parser = new RESParser();
            AttributeDataset data = parser.parse("RES", smile.data.parser.IOUtils.getTestDataFile("microarray/all_aml_test.res"));
            
            double[][] x = data.toArray(new double[data.size()][]);
            String[] genes = data.toArray(new String[data.size()]);
            String[] arrays = new String[data.attributes().length];
            for (int i = 0; i < arrays.length; i++) {
                arrays[i] = data.attributes()[i].getName();
            }

            JFrame frame = new JFrame("Heatmap");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLocationRelativeTo(null);
            frame.getContentPane().add(Heatmap.plot(genes, arrays, x, Palette.jet(256)));
            frame.setVisible(true);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }
}
