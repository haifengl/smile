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

package smile.clustering;

import org.apache.commons.csv.CSVFormat;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.io.CSV;
import smile.stat.distribution.MultivariateGaussianDistribution;
import smile.util.Paths;
import smile.validation.AdjustedRandIndex;
import smile.validation.RandIndex;

import java.util.ArrayList;
import java.util.stream.IntStream;

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
    @Test(expected = Test.None.class)
    public void testUSPS() throws Exception {
        System.out.println("USPS");

        ArrayList<StructField> fields = new ArrayList<>();
        fields.add(new StructField("class", DataTypes.ByteType));
        IntStream.range(0, 256).forEach(i -> fields.add(new StructField("V"+i, DataTypes.ByteType)));
        StructType schema = DataTypes.struct(fields);

        CSV csv = new CSV(CSVFormat.DEFAULT.withDelimiter(' '));
        csv.schema(schema);

        DataFrame train = csv.read(Paths.getTestData("usps/zip.train"));
        DataFrame test = csv.read(Paths.getTestData("usps/zip.test"));
        Formula formula = Formula.lhs("class");

        double[][] x = formula.frame(train).toArray();
        int[] y = formula.response(train).toIntArray();
        double[][] testx = formula.frame(test).toArray();
        int[] testy = formula.response(test).toIntArray();


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
    }
}