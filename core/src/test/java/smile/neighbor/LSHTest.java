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

package smile.neighbor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.stream.IntStream;

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
import smile.math.distance.EuclideanDistance;
import smile.util.Paths;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("rawtypes")
public class LSHTest {
    double[][] x = null;
    double[][] testx = null;
    LSH<double[]> lsh = null;
    LinearSearch<double[]> naive = null;

    public LSHTest() throws IOException {
        ArrayList<StructField> fields = new ArrayList<>();
        fields.add(new StructField("class", DataTypes.ByteType));
        IntStream.range(0, 256).forEach(i -> fields.add(new StructField("V"+i, DataTypes.ByteType)));
        StructType schema = DataTypes.struct(fields);

        CSV csv = new CSV(CSVFormat.DEFAULT.withDelimiter(' '));
        csv.schema(schema);

        DataFrame train = csv.read(Paths.getTestData("usps/zip.train"));
        DataFrame test = csv.read(Paths.getTestData("usps/zip.test"));
        Formula formula = Formula.lhs("class");

        x = formula.frame(train).toArray();
        testx = formula.frame(test).toArray();

        naive = new LinearSearch<>(x, new EuclideanDistance());
        lsh = new LSH<>(x, x);
        /*
        lsh = new LSH<double[]>(256, 100, 3, 4.0);
        for (double[] xi : x) {
            lsh.put(xi, xi);
        }
         * 
         */
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
    public void testNearest() {
        System.out.println("nearest");

        long time = System.currentTimeMillis();
        double recall = 0.0;
        double dist = 0.0;
        int hit = 0;
        for (int i = 0; i < testx.length; i++) {
            Neighbor neighbor = lsh.nearest(testx[i]);
            if (neighbor.index != -1) {
                dist += neighbor.distance;
                hit++;
            }
            if (neighbor.index == naive.nearest(testx[i]).index) {
                recall++;
            }
        }

        recall /= testx.length;
        System.out.println("recall is " + recall);
        System.out.println("average distance is " + dist / hit);
        System.out.println("time is " + (System.currentTimeMillis() - time) / 1000.0);
    }

    /**
     * Test of knn method, of class LSH.
     */
    @Test
    public void testKnn() {
        System.out.println("knn");

        long time = System.currentTimeMillis();
        double recall = 0.0;
        for (int i = 0; i < testx.length; i++) {
            int k = 3;
            Neighbor[] n1 = lsh.knn(testx[i], k);
            Neighbor[] n2 = naive.knn(testx[i], k);
            int hit = 0;
            for (int m = 0; m < n1.length && n1[m] != null; m++) {
                for (int n = 0; n < n2.length && n2[n] != null; n++) {
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
    public void testRange() {
        System.out.println("range");

        long time = System.currentTimeMillis();
        double recall = 0.0;
        for (int i = 0; i < testx.length; i++) {
            ArrayList<Neighbor<double[], double[]>> n1 = new ArrayList<>();
            ArrayList<Neighbor<double[], double[]>> n2 = new ArrayList<>();
            lsh.range(testx[i], 8.0, n1);
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
