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

import java.util.ArrayList;

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
@SuppressWarnings("rawtypes")
public class MPLSHTest {
    double[][] x = null;
    double[][] testx = null;
    MPLSH<double[]> lsh = null;
    LinearSearch<double[]> naive = null;

    public MPLSHTest() {
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

        naive = new LinearSearch<>(x, new EuclideanDistance());
        lsh = new MPLSH<>(256, 100, 3, 4.0);
        for (double[] xi : x) {
            lsh.put(xi, xi);
        }
        
        double[][] train = new double[500][];
        int[] index = Math.permutate(x.length);
        for (int i = 0; i < train.length; i++) {
            train[i] = x[index[i]];
        }
        lsh.learn(naive, train, 8.0);
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
     * Test of nearest method, of class LSH.
     */
    @Test
    public void testNearestPosteriori() {
        System.out.println("nearest posteriori");

        long time = System.currentTimeMillis();
        double recall = 0.0;
        double dist = 0.0;
        for (int i = 0; i < testx.length; i++) {
            Neighbor neighbor = lsh.nearest(testx[i], 0.95, 50);
            dist += neighbor.distance;
            if (neighbor.index == naive.nearest(testx[i]).index) {
                recall++;
            }
        }

        recall /= testx.length;
        System.out.println("recall is " + recall);
        System.out.println("average distance is " + dist / testx.length);
        System.out.println("time is " + (System.currentTimeMillis() - time) / 1000.0);
    }

    /**
     * Test of knn method, of class LSH.
     */
    @Test
    public void testKnnPosteriori() {
        System.out.println("knn posteriori");

        long time = System.currentTimeMillis();
        double recall = 0.0;
        for (int i = 0; i < testx.length; i++) {
            int k = 3;
            Neighbor[] n1 = lsh.knn(testx[i], k, 0.95, 50);
            Neighbor[] n2 = naive.knn(testx[i], k);
            int hit = 0;
            for (int m = 0; m < k && n1[m] != null; m++) {
                for (int n = 0; n < k && n2[n] != null; n++) {
                    if (n1[m].index == n2[n].index) {
                        hit++;
                        break;
                    }
                }
            }
            recall += 1.0 * hit / k;
        }

        recall /= testx.length;
        System.out.println("recall is " + recall);
        System.out.println("time is " + (System.currentTimeMillis() - time) / 1000.0);
    }

    /**
     * Test of range method, of class LSH.
     */
    @Test
    public void testRangePosteriori() {
        System.out.println("range posteriori");

        long time = System.currentTimeMillis();
        double recall = 0.0;
        for (int i = 0; i < testx.length; i++) {
            ArrayList<Neighbor<double[], double[]>> n1 = new ArrayList<>();
            ArrayList<Neighbor<double[], double[]>> n2 = new ArrayList<>();
            lsh.range(testx[i], 8.0, n1, 0.95, 50);
            naive.range(testx[i], 8.0, n2);

            int hit = 0;
            for (int m = 0; m < n1.size(); m++) {
                for (int n = 0; n < n2.size(); n++) {
                    if (n1.get(m).index == n2.get(n).index) {
                        hit++;
                        break;
                    }
                }
            }
            if (!n2.isEmpty()) {
                recall += 1.0 * hit / n2.size();
            }
        }

        recall /= testx.length;
        System.out.println("recall is " + recall);
        System.out.println("time is " + (System.currentTimeMillis() - time) / 1000.0);
    }
}
