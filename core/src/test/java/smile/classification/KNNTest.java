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

package smile.classification;

import smile.data.NominalAttribute;
import java.text.ParseException;
import smile.data.parser.DelimitedTextParser;
import smile.data.AttributeDataset;
import smile.data.parser.ArffParser;
import java.io.IOException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class KNNTest {

    public KNNTest() {
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
     * Test of learn method, of class KNN.
     */
    @Test
    public void testLearn_3args() {
        System.out.println("learn");
        ArffParser arffParser = new ArffParser();
        arffParser.setResponseIndex(4);
        try {
            AttributeDataset iris = arffParser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/iris.arff"));
            double[][] x = iris.toArray(new double[0][]);
            int[] y = iris.toArray(new int[0]);

            KNN<double[]> knn = KNN.learn(x, y, 1);
            int error = 0;
            for (int i = 0; i < x.length; i++) {
                if (knn.predict(x[i]) != y[i]) {
                    error++;
                }
            }
            System.out.println("1-nn error = " + error);
            assertEquals(6, error);

            knn = KNN.learn(x, y, 3);
            error = 0;
            for (int i = 0; i < x.length; i++) {
                if (knn.predict(x[i]) != y[i]) {
                    error++;
                }
            }
            System.out.println("3-nn error = " + error);
            assertEquals(6, error);

            knn = KNN.learn(x, y, 5);
            error = 0;
            for (int i = 0; i < x.length; i++) {
                if (knn.predict(x[i]) != y[i]) {
                    error++;
                }
            }
            System.out.println("5-nn error = " + error);
            assertEquals(5, error);

            knn = KNN.learn(x, y, 7);
            error = 0;
            for (int i = 0; i < x.length; i++) {
                if (knn.predict(x[i]) != y[i]) {
                    error++;
                }
            }
            System.out.println("7-nn error = " + error);
            assertEquals(5, error);

            knn = KNN.learn(x, y, 9);
            error = 0;
            for (int i = 0; i < x.length; i++) {
                if (knn.predict(x[i]) != y[i]) {
                    error++;
                }
            }
            System.out.println("9-nn error = " + error);
            assertEquals(5, error);

            knn = KNN.learn(x, y, 11);
            error = 0;
            for (int i = 0; i < x.length; i++) {
                if (knn.predict(x[i]) != y[i]) {
                    error++;
                }
            }
            System.out.println("11-nn error = " + error);
            assertEquals(4, error);

            knn = KNN.learn(x, y, 13);
            error = 0;
            for (int i = 0; i < x.length; i++) {
                if (knn.predict(x[i]) != y[i]) {
                    error++;
                }
            }
            System.out.println("13-nn error = " + error);
            assertEquals(5, error);

            knn = KNN.learn(x, y, 15);
            error = 0;
            for (int i = 0; i < x.length; i++) {
                if (knn.predict(x[i]) != y[i]) {
                    error++;
                }
            }
            System.out.println("15-nn error = " + error);
            assertEquals(4, error);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of learn method, of class KNN.
     */
    @Test
    public void testSegment() throws ParseException {
        System.out.println("Segment");
        ArffParser parser = new ArffParser();
        parser.setResponseIndex(19);
        try {
            AttributeDataset train = parser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/segment-challenge.arff"));
            AttributeDataset test = parser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/segment-test.arff"));

            double[][] x = train.toArray(new double[0][]);
            int[] y = train.toArray(new int[0]);
            double[][] testx = test.toArray(new double[0][]);
            int[] testy = test.toArray(new int[0]);
            
            KNN<double[]> knn = KNN.learn(x, y);

            int error = 0;
            for (int i = 0; i < testx.length; i++) {
                if (knn.predict(testx[i]) != testy[i]) {
                    error++;
                }
            }

            System.out.format("Segment error rate = %.2f%%%n", 100.0 * error / testx.length);
            assertEquals(39, error);
        } catch (IOException ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of learn method, of class KNN.
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
            
            KNN<double[]> knn = KNN.learn(x, y);
            
            int error = 0;
            for (int i = 0; i < testx.length; i++) {
                if (knn.predict(testx[i]) != testy[i]) {
                    error++;
                }
            }

            System.out.format("USPS error rate = %.2f%%%n", 100.0 * error / testx.length);
            assertEquals(113, error);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }
}