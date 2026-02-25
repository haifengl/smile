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

import java.io.IOException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import smile.data.DataFrame;
import smile.interpolation.BicubicInterpolation;
import smile.io.Paths;
import smile.io.Read;
import smile.stat.distribution.GaussianDistribution;
import smile.stat.distribution.MultivariateGaussianDistribution;
import smile.tensor.DenseMatrix;
import smile.tensor.SparseMatrix;
import static java.lang.Math.*;
import static java.awt.Color.*;

public class PlotTest {
    DataFrame iris;
    double[][] Z = new double[101][101];

    public PlotTest() throws IOException, ParseException {
        iris = Read.arff(Paths.getTestData("weka/iris.arff"));

        // the matrix to display
        double[][] z = {
                {1.0, 2.0, 4.0, 1.0},
                {6.0, 3.0, 5.0, 2.0},
                {4.0, 2.0, 1.0, 5.0},
                {5.0, 4.0, 2.0, 3.0}
        };
        // make the matrix larger with bicubic interpolation
        double[] x = {0.0, 1.0, 2.0, 3.0};
        double[] y = {0.0, 1.0, 2.0, 3.0};
        var bicubic = new BicubicInterpolation(x, y, z);
        for (int i = 0; i <= 100; i++) {
            for (int j = 0; j <= 100; j++) {
                Z[i][j] = bicubic.interpolate(i * 0.03, j * 0.03);
            }
        }
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
    }

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void testHeart() throws Exception {
        System.out.println("Heart");

        double[][] heart = new double[200][2];
        for (int i = 0; i < 200; i++) {
            double t = PI * (i - 100) / 100;
            heart[i][0] = 16 * pow(sin(t), 3);
            heart[i][1] = 13 * cos(t) - 5 * cos(2*t) - 2 * cos(3*t) - cos(4*t);
        }

        var figure = LinePlot.of(heart, RED).figure();
        var pane = new FigurePane(figure);
        pane.window();
    }

    @Test
    public void testScatter() throws Exception {
        System.out.println("Scatter");

        var figure = ScatterPlot.of(iris, "sepallength", "sepalwidth", "class", '*').figure();
        figure.setAxisLabels("sepallength", "sepalwidth");
        var pane = new FigurePane(figure);
        pane.window();
    }

    @Test
    public void testIris() throws Exception {
        System.out.println("Iris");

        var figure = ScatterPlot.of(iris, "sepallength", "sepalwidth", "petallength", "class", '*').figure();
        figure.setAxisLabels("sepallength", "sepalwidth", "petallength");
        var pane = new FigurePane(figure);
        pane.window();
    }

    @Test
    public void testSPLOM() throws Exception {
        System.out.println("SPLOM");

        var canvas = MultiFigurePane.splom(iris, '*', "class");
        canvas.window();
    }

    @Test
    public void testBox() throws Exception {
        System.out.println("Box");

        String[] labels = ((smile.data.measure.NominalScale) iris.schema().field("class").measure()).levels();
        double[][] data = new double[labels.length][];
        for (int i = 0; i < data.length; i++) {
            var label = labels[i];
            data[i] = iris.stream().
                    filter(row -> row.getString("class").equals(label)).
                    mapToDouble(row -> row.getFloat("sepallength")).
                    toArray();
        }
        var figure = new BoxPlot(data, labels).figure();
        figure.setAxisLabels("", "sepallength");
        var pane = new FigurePane(figure);
        pane.window();
    }

    @Test
    public void testHistogram() throws Exception {
        System.out.println("Histogram");

        var cow = Read.csv(Paths.getTestData("stat/cow.txt")).column("V1").toDoubleArray();
        var data = Arrays.stream(cow).filter(w -> w <= 3500).toArray();
        var figure = Histogram.of(data, 50, true).figure();
        figure.setAxisLabels("Weight", "Probability");
        var pane = new FigurePane(figure);
        pane.window();
    }

    @Test
    public void testHistogram3D() throws Exception {
        System.out.println("Histogram 3D");

        double[] mu = {0.0, 0.0};
        double[][] v = { {1.0, 0.6}, {0.6, 2.0} };
        var gauss = new MultivariateGaussianDistribution(mu, DenseMatrix.of(v));
        var data = Stream.generate(gauss::rand).limit(10000).toArray(double[][]::new);
        var figure = Histogram3D.of(data, 50, false).figure();
        var pane = new FigurePane(figure);
        pane.window();

    }

    @Test
    public void testQQ() throws Exception {
        System.out.println("QQ");

        var gauss = new GaussianDistribution(0.0, 1.0);
        var data = DoubleStream.generate(gauss::rand).limit(1000).toArray();
        var figure = QQPlot.of(data).figure();
        var pane = new FigurePane(figure);
        pane.window();

    }

    @Test
    public void testHeatmap() throws Exception {
        System.out.println("Heatmap");

        var figure = Heatmap.of(Z, Palette.jet(256)).figure();
        var pane = new FigurePane(figure);
        pane.window();
    }

    @Test
    public void testSparseMatrix() throws Exception {
        System.out.println("Sparse Matrix");

        var sparse = SparseMatrix.text(Paths.getTestData("matrix/mesh2em5.txt"));
        var figure = SparseMatrixPlot.of(sparse).figure();
        figure.setTitle("mesh2em5");
        var pane = new FigurePane(figure);
        pane.window();
    }

    @Test
    public void testContour() throws Exception {
        System.out.println("Contour");

        var figure = Heatmap.of(Z, 256).figure();
        figure.add(Contour.of(Z));
        var pane = new FigurePane(figure);
        pane.window();
    }

    @Test
    public void testSurface() throws Exception {
        System.out.println("Surface");

        var figure = Surface.of(Z, Palette.jet(256, 1.0f)).figure();
        var pane = new FigurePane(figure);
        pane.window();
    }
}
