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

package smile.neighbor;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.data.AttributeDataset;
import smile.data.NominalAttribute;
import smile.data.parser.DelimitedTextParser;
import smile.math.Math;
import smile.math.distance.EditDistance;
import smile.math.distance.EuclideanDistance;
import smile.stat.distribution.MultivariateGaussianDistribution;

/**
 *
 * @author Haifeng Li
 */
public class LinearSearchSpeedTest {

    public LinearSearchSpeedTest() {

    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of range method, of class LinearSearch.
     */
    @Test
    public void testString() {
        System.out.println("string");

        List<String> words = new ArrayList<>();
        long start = System.currentTimeMillis();
        try {
            BufferedReader input = smile.data.parser.IOUtils.getTestDataReader("neighbor/index.noun");
            String line = input.readLine();
            while (line != null) {
                if (!line.startsWith(" ")) {
                    String[] w = line.split("\\s");
                    words.add(w[0].replace('_', ' '));
                }
                line = input.readLine();
            }
        } catch (Exception e) {
            System.err.println(e);
        }

        double time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("Loading string data: %.2fs%n", time);

        String[] data = words.toArray(new String[words.size()]);
        LinearSearch<String> naive = new LinearSearch<>(data, new EditDistance(50, true));

        start = System.currentTimeMillis();
        List<Neighbor<String, String>> neighbors = new ArrayList<>();
        for (int i = 1000; i < 1100; i++) {
            naive.range(words.get(i), 1, neighbors);
            neighbors.clear();
        }

        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("Linear string search: %.2fs%n", time);
    }

    /**
     * Test of nearest method, of class LinearSearch.
     */
    @Test
    public void testToy() {
        System.out.println("toy data");

        long start = System.currentTimeMillis();
        double[] mu1 = {1.0, 1.0, 1.0};
        double[][] sigma1 = {{1.0, 0.0, 0.0}, {0.0, 1.0, 0.0}, {0.0, 0.0, 1.0}};
        double[] mu2 = {-2.0, -2.0, -2.0};
        double[][] sigma2 = {{1.0, 0.3, 0.8}, {0.3, 1.0, 0.5}, {0.8, 0.5, 1.0}};
        double[] mu3 = {4.0, 2.0, 3.0};
        double[][] sigma3 = {{1.0, 0.8, 0.3}, {0.8, 1.0, 0.5}, {0.3, 0.5, 1.0}};
        double[] mu4 = {3.0, 5.0, 1.0};
        double[][] sigma4 = {{1.0, 0.5, 0.5}, {0.5, 1.0, 0.5}, {0.5, 0.5, 1.0}};
        double[][] data = new double[10000][];

        MultivariateGaussianDistribution g1 = new MultivariateGaussianDistribution(mu1, sigma1);
        for (int i = 0; i < 2000; i++) {
            data[i] = g1.rand();
        }

        MultivariateGaussianDistribution g2 = new MultivariateGaussianDistribution(mu2, sigma2);
        for (int i = 0; i < 3000; i++) {
            data[2000 + i] = g2.rand();
        }

        MultivariateGaussianDistribution g3 = new MultivariateGaussianDistribution(mu3, sigma3);
        for (int i = 0; i < 3000; i++) {
            data[5000 + i] = g3.rand();
        }

        MultivariateGaussianDistribution g4 = new MultivariateGaussianDistribution(mu4, sigma4);
        for (int i = 0; i < 2000; i++) {
            data[8000 + i] = g4.rand();
        }

        double time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("Generating toy data (four Gaussians): %.2fs%n", time);

        LinearSearch<double[]> naive = new LinearSearch<>(data, new EuclideanDistance());

        start = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            naive.nearest(data[Math.randomInt(data.length)]);
        }
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("NN: %.2fs%n", time);

        start = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            naive.knn(data[Math.randomInt(data.length)], 10);
        }
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("10-NN: %.2fs%n", time);

        start = System.currentTimeMillis();
        List<Neighbor<double[], double[]>> n = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            naive.range(data[Math.randomInt(data.length)], 1.0, n);
            n.clear();
        }
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("Range: %.2fs%n", time);
    }

    /**
     * Test of nearest method, of class LinearSearch.
     */
    @Test
    public void testUSPS() {
        System.out.println("USPS");
        double[][] x = null;
        double[][] testx = null;

        long start = System.currentTimeMillis();
        DelimitedTextParser parser = new DelimitedTextParser();
        parser.setResponseIndex(new NominalAttribute("class"), 0);
        try {
            AttributeDataset train = parser.parse("USPS Train", smile.data.parser.IOUtils.getTestDataFile("usps/zip.train"));
            AttributeDataset test = parser.parse("USPS Test", smile.data.parser.IOUtils.getTestDataFile("usps/zip.test"));

            x = train.toArray(new double[train.size()][]);
            testx = test.toArray(new double[test.size()][]);
        } catch (Exception ex) {
            System.err.println(ex);
        }

        double time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("Loading USPS: %.2fs%n", time);

        LinearSearch<double[]> naive = new LinearSearch<>(x, new EuclideanDistance());

        start = System.currentTimeMillis();
        for (int i = 0; i < testx.length; i++) {
            naive.nearest(testx[i]);
        }
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("NN: %.2fs%n", time);

        start = System.currentTimeMillis();
        for (int i = 0; i < testx.length; i++) {
            naive.knn(testx[i], 10);
        }
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("10-NN: %.2fs%n", time);

        start = System.currentTimeMillis();
        List<Neighbor<double[], double[]>> n = new ArrayList<>();
        for (int i = 0; i < testx.length; i++) {
            naive.range(testx[i], 8.0, n);
            n.clear();
        }
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("Range: %.2fs%n", time);
    }
}