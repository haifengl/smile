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
package smile.anomaly;

import org.apache.commons.csv.CSVFormat;
import org.junit.jupiter.api.*;
import smile.io.Read;
import smile.io.Write;
import smile.math.kernel.GaussianKernel;
import smile.io.Paths;

/**
 *
 * @author Haifeng
 */
public class SVMTest {

    public SVMTest() {
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
    public void testSixClusters() throws Exception {
        System.out.println("Six clusters");

        CSVFormat format = CSVFormat.Builder.create().setDelimiter(' ').get();
        double[][] data = Read.csv(Paths.getTestData("clustering/rem.txt"), format).toArray();
        SVM<double[]> model = SVM.fit(data, new GaussianKernel(1.0), new SVM.Options(0.2, 1E-3));

        double[][] grid = new double[201][201];
        for (int i = 0; i < grid.length; i++) {
            for (int j = 0; j < grid[i].length; j++) {
                double[] point = {-5 + i * 0.1, -5 + j * 0.1};
                grid[j][i] = model.score(point);
            }
        }

        java.nio.file.Path temp = Write.object(model);
        Read.object(temp);
        /*
        double[] x = new double[201];
        double[] y = new double[201];
        for (int i = 0; i < x.length; i++) {
            x[i] = -5 + i * 0.1;
            y[i] = -5 + i * 0.1;
        }

        ScatterPlot.of(data).canvas().window();
        Heatmap.of(x, y, grid).canvas().window();
         */
    }

    @Test
    public void testSinCos() throws Exception {
        System.out.println("SinCos");

        CSVFormat format = CSVFormat.Builder.create().setDelimiter('\t').get();
        double[][] data = Read.csv(Paths.getTestData("clustering/sincos.txt"), format).toArray();
        SVM<double[]> model = SVM.fit(data, new GaussianKernel(0.5));

        double[][] grid = new double[51][51];
        for (int i = 0; i < grid.length; i++) {
            for (int j = 0; j < grid[i].length; j++) {
                double[] point = {-2 + i * 0.1, -2 + j * 0.1};
                grid[j][i] = model.score(point);
            }
        }
        /*
        double[] x = new double[51];
        double[] y = new double[51];
        for (int i = 0; i < x.length; i++) {
            x[i] = -2 + i * 0.1;
            y[i] = -2 + i * 0.1;
        }

        ScatterPlot.of(data).canvas().window();
        Heatmap.of(x, y, grid).canvas().window();
         */
    }
}
