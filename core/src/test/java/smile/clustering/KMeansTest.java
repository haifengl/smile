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

package smile.clustering;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.data.AttributeDataset;
import smile.data.NominalAttribute;
import smile.data.parser.DelimitedTextParser;
import smile.stat.distribution.MultivariateGaussianDistribution;
import smile.validation.AdjustedRandIndex;
import smile.validation.RandIndex;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class KMeansTest {
    double[] mu1 = {1.0, 1.0, 1.0};
    double[][] sigma1 = {{1.0, 0.0, 0.0}, {0.0, 1.0, 0.0}, {0.0, 0.0, 1.0}};
    double[] mu2 = {-2.0, -2.0, -2.0};
    double[][] sigma2 = {{1.0, 0.3, 0.8}, {0.3, 1.0, 0.5}, {0.8, 0.5, 1.0}};
    double[] mu3 = {4.0, 2.0, 3.0};
    double[][] sigma3 = {{1.0, 0.8, 0.3}, {0.8, 1.0, 0.5}, {0.3, 0.5, 1.0}};
    double[] mu4 = {3.0, 5.0, 1.0};
    double[][] sigma4 = {{1.0, 0.5, 0.5}, {0.5, 1.0, 0.5}, {0.5, 0.5, 1.0}};
    double[][] data = new double[100000][];
    int[] label = new int[100000];

    public KMeansTest() {
        MultivariateGaussianDistribution g1 = new MultivariateGaussianDistribution(mu1, sigma1);
        for (int i = 0; i < 20000; i++) {
            data[i] = g1.rand();
            label[i] = 0;
        }

        MultivariateGaussianDistribution g2 = new MultivariateGaussianDistribution(mu2, sigma2);
        for (int i = 0; i < 30000; i++) {
            data[20000 + i] = g2.rand();
            label[i] = 1;
        }

        MultivariateGaussianDistribution g3 = new MultivariateGaussianDistribution(mu3, sigma3);
        for (int i = 0; i < 30000; i++) {
            data[50000 + i] = g3.rand();
            label[i] = 2;
        }

        MultivariateGaussianDistribution g4 = new MultivariateGaussianDistribution(mu4, sigma4);
        for (int i = 0; i < 20000; i++) {
            data[80000 + i] = g4.rand();
            label[i] = 3;
        }
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
     * Test of learn method, of class KMeans.
     */
    @Test
    public void testBBD4() {
        System.out.println("BBD 4");
        KMeans kmeans = new KMeans(data, 4, 100);
        AdjustedRandIndex ari = new AdjustedRandIndex();
        RandIndex rand = new RandIndex();
        double r = rand.measure(label, kmeans.getClusterLabel());
        double r2 = ari.measure(label, kmeans.getClusterLabel());
        System.out.format("Training rand index = %.2f%%\tadjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
    }

    /**
     * Test of lloyd method, of class KMeans.
     */
    @Test
    public void testLloyd4() {
        System.out.println("Lloyd 4");
        KMeans kmeans = KMeans.lloyd(data, 4, 100);
        AdjustedRandIndex ari = new AdjustedRandIndex();
        RandIndex rand = new RandIndex();
        double r = rand.measure(label, kmeans.getClusterLabel());
        double r2 = ari.measure(label, kmeans.getClusterLabel());
        System.out.format("Training rand index = %.2f%%\tadjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
    }

    /**
     * Test of learn method, of class KMeans.
     */
    @Test
    public void testBBD64() {
        System.out.println("BBD 64");
        KMeans kmeans = new KMeans(data, 64, 100);
        AdjustedRandIndex ari = new AdjustedRandIndex();
        RandIndex rand = new RandIndex();
        double r = rand.measure(label, kmeans.getClusterLabel());
        double r2 = ari.measure(label, kmeans.getClusterLabel());
        System.out.format("Training rand index = %.2f%%\tadjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
    }

    /**
     * Test of lloyd method, of class KMeans.
     */
    @Test
    public void testLloyd64() {
        System.out.println("Lloyd 64");
        KMeans kmeans = KMeans.lloyd(data, 64, 100);
        AdjustedRandIndex ari = new AdjustedRandIndex();
        RandIndex rand = new RandIndex();
        double r = rand.measure(label, kmeans.getClusterLabel());
        double r2 = ari.measure(label, kmeans.getClusterLabel());
        System.out.format("Training rand index = %.2f%%\tadjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
    }

    /**
     * Test of learn method, of class KMeans.
     */
    @Test
    public void testUSPS() {
        System.out.println("USPS");
        DelimitedTextParser parser = new DelimitedTextParser();
        parser.setResponseIndex(new NominalAttribute("class"), 0);
        try {
            AttributeDataset train = parser.parse("USPS Train", smile.data.parser.IOUtils.getTestDataFile("usps/zip.train"));
            AttributeDataset test = parser.parse("USPS Test", smile.data.parser.IOUtils.getTestDataFile("usps/zip.test"));

            double[][] x = train.toArray(new double[train.size()][]);
            int[] y = train.toArray(new int[train.size()]);
            double[][] testx = test.toArray(new double[test.size()][]);
            int[] testy = test.toArray(new int[test.size()]);
            
            KMeans kmeans = new KMeans(x, 10, 100, 4);
            
            AdjustedRandIndex ari = new AdjustedRandIndex();
            RandIndex rand = new RandIndex();
            double r = rand.measure(y, kmeans.getClusterLabel());
            double r2 = ari.measure(y, kmeans.getClusterLabel());
            System.out.format("Training rand index = %.2f%%\tadjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
            assertTrue(r > 0.85);
            assertTrue(r2 > 0.45);
            
            int[] p = new int[testx.length];
            for (int i = 0; i < testx.length; i++) {
                p[i] = kmeans.predict(testx[i]);
            }
            
            r = rand.measure(testy, p);
            r2 = ari.measure(testy, p);
            System.out.format("Testing rand index = %.2f%%\tadjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
            assertTrue(r > 0.85);
            assertTrue(r2 > 0.45);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }
}