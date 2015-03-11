/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/
package smile.neighbor;

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
import smile.math.distance.EuclideanDistance;

/**
 *
 * @author Haifeng Li
 */
public class MPLSHSpeedTest {

    public MPLSHSpeedTest() {
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
     * Test of nearest method, of class KDTree.
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
            AttributeDataset train = parser.parse("USPS Train", this.getClass().getResourceAsStream("/smile/data/usps/zip.train"));
            AttributeDataset test = parser.parse("USPS Test", this.getClass().getResourceAsStream("/smile/data/usps/zip.test"));

            x = train.toArray(new double[train.size()][]);
            testx = test.toArray(new double[test.size()][]);
        } catch (Exception ex) {
            System.err.println(ex);
        }

        double time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("Loading USPS: %.2fs\n", time);

        start = System.currentTimeMillis();
        MPLSH<double[]> lsh = new MPLSH<double[]>(256, 100, 3, 4.0);
        for (double[] xi : x) {
            lsh.put(xi, xi);
        }
        
        double[][] train = new double[500][];
        int[] index = Math.permutate(x.length);
        for (int i = 0; i < train.length; i++) {
            train[i] = x[index[i]];
        }

        LinearSearch<double[]> naive = new LinearSearch<double[]>(x, new EuclideanDistance());
        lsh.learn(naive, train, 8.0);
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("Building LSH: %.2fs\n", time);

        start = System.currentTimeMillis();
        for (int i = 0; i < testx.length; i++) {
            lsh.nearest(testx[i]);
        }
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("NN: %.2fs\n", time);

        start = System.currentTimeMillis();
        for (int i = 0; i < testx.length; i++) {
            lsh.knn(testx[i], 10);
        }
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("10-NN: %.2fs\n", time);

        start = System.currentTimeMillis();
        List<Neighbor<double[], double[]>> n = new ArrayList<Neighbor<double[], double[]>>();
        for (int i = 0; i < testx.length; i++) {
            lsh.range(testx[i], 8.0, n);
            n.clear();
        }
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("Range: %.2fs\n", time);
    }
}
