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

package smile.demo.classification;

import java.awt.Dimension;
import javax.swing.JFrame;
import org.apache.commons.csv.CSVFormat;
import smile.plot.swing.Palette;
import smile.plot.swing.PlotCanvas;
import smile.plot.swing.ScatterPlot;

/**
 * Use iris data set for demo and visualization purpose. 
 * 
 * @author rayeaster
 */
@SuppressWarnings("serial")
public class NaiveBayesDemo extends ClassificationDemo {

    /**
     * The number of classes.
     */
    private int k = 3;
    /**
     * The number of independent variables.
     */
    private int p = 2;
    /**
     * demo variables chosen from original iris data feature for easy-to-understand visualization: like Sepal.Length and Petal.Length
     */
    private static int[] pIdx = null;

    static {
        pIdx = new int[] {0, 2};//this combination has the best training error among all 6 alternatives

        datasetName = new String[]{
                "Iris"
        };

        datasource = new String[]{
                "classification/iris.txt"
        };

        CSVFormat format = CSVFormat.DEFAULT.withDelimiter('\t').withFirstRecordAsHeader();
    }


    /**
     * Constructor.
     */
    public NaiveBayesDemo() {
        super();
    }
    
    @Override
    protected PlotCanvas paintOnCanvas(double[][] data, int[] label) {

        int rows = data.length;
        int features = data[0].length;

        double[][] paintPoints = new double[rows][features - 2];// iris data set has 4 features
        for(int i = 0;i < rows;i++) {
            for(int j = 0;j < pIdx.length;j++) {
                paintPoints[i][j] = data[i][pIdx[j]];
            }
        }

        PlotCanvas canvas = ScatterPlot.plot(paintPoints, pointLegend);
        for (int i = 0; i < data.length; i++) {
            canvas.point(pointLegend, Palette.COLORS[label[i]], paintPoints[i]);
        }
        return canvas;
    }
    
    @Override
    protected double[] getContourLevels() {
        return new double[]{0.5, 2};
    }

    @Override
    public double[][] learn(double[] x, double[] y) {

        double[][] data = formula.x(dataset[datasetIndex]).toArray();
        int[] label = formula.y(dataset[datasetIndex]).toIntArray();
        int[] labelPredict = new int[label.length];
        
        double[][] trainData = new double[label.length][p];
        for(int i = 0;i < label.length;i++) {
            for(int j = 0;j < pIdx.length;j++) {
                trainData[i][j] = data[i][pIdx[j]];
            }
        }

        /*
        NaiveBayes nbc = new NaiveBayes(NaiveBayes.Model.POLYAURN, k, p);
        nbc.learn(trainData, label);
        
        for (int i = 0; i < label.length; i++) {
            labelPredict[i] = nbc.predict(trainData[i]);
        }
        double trainError = error(label, labelPredict);

        System.out.format("training error = %.2f%%\n", 100*trainError);
        
        double[][] z = new double[y.length][x.length];
        for (int i = 0; i < y.length; i++) {
            for (int j = 0; j < x.length; j++) {
                double[] p = {x[j], y[i]};
                z[i][j] = nbc.predict(p);
            }
        }
         */

        double[][] z = new double[y.length][x.length];
        return z;
    }

    @Override
    public String toString() {
        return "Naive Bayes";
    }

    public static void main(String argv[]) {
        ClassificationDemo demo = new NaiveBayesDemo();
        JFrame f = new JFrame(demo.toString());
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
