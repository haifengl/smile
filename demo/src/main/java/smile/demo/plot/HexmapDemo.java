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
import java.util.Arrays;

import javax.swing.JFrame;
import javax.swing.JPanel;

import smile.vq.SOM;
import smile.data.AttributeDataset;
import smile.data.NominalAttribute;
import smile.data.parser.DelimitedTextParser;
import smile.math.Math;
import smile.mds.IsotonicMDS;
import smile.mds.MDS;
import smile.mds.SammonMapping;
import smile.plot.Hexmap;
import smile.plot.Palette;
import smile.plot.PlotCanvas;
import smile.plot.Surface;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class HexmapDemo extends JPanel {
    public HexmapDemo() {
        super(new GridLayout(2,4));
        setBackground(Color.white);

        int n = 41;
        double[] x = new double[n];
        for (int i = 0; i < n; i++)
            x[i] = -2.0 + 0.1 * i;

        int m = 41;
        double[] y = new double[m];
        for (int i = 0; i < m; i++)
            y[i] = -2.0 + 0.1 * i;

        double[][] z = new double[m][n];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++)
                z[i][j] = x[j] * Math.exp(-x[j]*x[j] - y[i]*y[i]);
        }

        PlotCanvas canvas = Hexmap.plot(z, Palette.jet(256));
        canvas.setTitle("jet");
        add(canvas);
        canvas = Hexmap.plot(z, Palette.redblue(256));
        canvas.setTitle("redblue");
        add(canvas);
        canvas = Hexmap.plot(z, Palette.redgreen(256));
        canvas.setTitle("redgreen");
        add(canvas);
        canvas = Hexmap.plot(z, Palette.heat(256));
        canvas.setTitle("heat");
        add(canvas);
        canvas = Hexmap.plot(z, Palette.terrain(256));
        canvas.setTitle("terrain");
        add(canvas);
        canvas = Hexmap.plot(z, Palette.rainbow(256));
        canvas.setTitle("rainbow");
        add(canvas);
        canvas = Hexmap.plot(z, Palette.topo(256));
        canvas.setTitle("topo");
        add(canvas);
    }

    @Override
    public String toString() {
        return "Hexmap";
    }

    public static void main(String[] args) {
        DelimitedTextParser parser = new DelimitedTextParser();
        parser.setResponseIndex(new NominalAttribute("class"), 0);
        try {
            AttributeDataset train = parser.parse("USPS Train", smile.data.parser.IOUtils.getTestDataFile("usps/zip.train"));
            
            double[][] x = train.toArray(new double[train.size()][]);
            int[] y = train.toArray(new int[train.size()]);
            
            int m = 20;
            int n = 20;
            SOM som = new SOM(x, m, n);
            
            String[][] labels = new String[m][n];
            int[] neurons = new int[x.length];
            for (int i = 0; i < x.length; i++) {
                neurons[i] = som.predict(x[i]);
            }
            
            int[] count = new int[10];
            for (int i = 0; i < m; i++) {
                for (int j = 0; j < n; j++) {
                    Arrays.fill(count, 0);
                    for (int k = 0; k < neurons.length; k++) {
                        if (neurons[k] == i * n + j) {
                            count[y[k]]++;
                        }
                    }
                    
                    int sum = Math.sum(count);
                    if (sum == 0.0) {
                        labels[i][j] = "no samples";
                    } else {
                        labels[i][j] = String.format("<table border=\"1\"><tr><td>Total</td><td align=\"right\">%d</td></tr>", sum);
                        
                        for (int l = 0; l < count.length; l++) {
                            if (count[l] > 0) {
                                labels[i][j] += String.format("<tr><td>class %d</td><td align=\"right\">%.1f%%</td></tr>", l, 100.0*count[l]/sum);
                            }
                        }
                        
                        labels[i][j] += "</table>";
                    }
                }
            }
            
            double[][] umatrix = som.umatrix();
            
            double[][][] map = som.map();
            double[][] proximity = new double[m*n][m*n];
            for (int i = 0; i < m*n; i++) {
                for (int j = 0; j < m*n; j++) {
                    proximity[i][j] = Math.distance(map[i/n][i%n], map[j/n][j%n]);
                }
            }
            
            MDS mds = new MDS(proximity, 3);
            double[][] coords = mds.getCoordinates();
            double[][][] mdsgrid = new double[m][n][];
            for (int i = 0; i < m*n; i++) {
                mdsgrid[i/n][i%n] = mds.getCoordinates()[i];
            }            
            
            SammonMapping sammon = new SammonMapping(proximity, coords);
            double[][][] sammongrid = new double[m][n][];
            for (int i = 0; i < m*n; i++) {
                sammongrid[i/n][i%n] = sammon.getCoordinates()[i];
            }
            
            IsotonicMDS isomds = new IsotonicMDS(proximity, coords);
            double[][][] isomdsgrid = new double[m][n][];
            for (int i = 0; i < m*n; i++) {
                isomdsgrid[i/n][i%n] = isomds.getCoordinates()[i];
            }
            
            JFrame frame = new JFrame("Hexmap");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLocationRelativeTo(null);
            frame.add(Hexmap.plot(labels, umatrix));
            
            PlotCanvas canvas = Surface.plot(mdsgrid);
            canvas.setTitle("MDS");
            frame.add(canvas);
            
            canvas = Surface.plot(isomdsgrid);
            canvas.setTitle("Isotonic MDS");
            frame.add(canvas);
            
            canvas = Surface.plot(sammongrid);
            canvas.setTitle("Sammon Mapping");
            frame.add(canvas);
            frame.setVisible(true);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }
}
