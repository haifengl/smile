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
import smile.data.parser.DelimitedTextParser;
import smile.data.AttributeDataset;
import smile.data.parser.ArffParser;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.math.Math;
import smile.validation.LOOCV;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class NeuralNetworkTest {

    public NeuralNetworkTest() {
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
     * Test of learn method, of class NeuralNetwork.
     */
    @Test
    public void testIris() {
        System.out.println("Iris");
        ArffParser arffParser = new ArffParser();
        arffParser.setResponseIndex(4);
        try {
            AttributeDataset iris = arffParser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/iris.arff"));
            double[][] x = iris.toArray(new double[iris.size()][]);
            int[] y = iris.toArray(new int[iris.size()]);

            int n = x.length;
            int p = x[0].length;
            double[] mu = Math.colMeans(x);
            double[] sd = Math.colSds(x);
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < p; j++) {
                    x[i][j] = (x[i][j] - mu[j]) / sd[j];
                }
            }

            LOOCV loocv = new LOOCV(n);
            int error = 0;
            for (int i = 0; i < n; i++) {
                double[][] trainx = Math.slice(x, loocv.train[i]);
                int[] trainy = Math.slice(y, loocv.train[i]);
                NeuralNetwork net = new NeuralNetwork(NeuralNetwork.ErrorFunction.CROSS_ENTROPY, NeuralNetwork.ActivationFunction.SOFTMAX, x[0].length, 10, 3);
                for (int j = 0; j < 20; j++) {
                    net.learn(trainx, trainy);
                }

                if (y[loocv.test[i]] != net.predict(x[loocv.test[i]]))
                    error++;
            }

            System.out.println("Neural network error = " + error);
            assertTrue(error <= 8);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of learn method, of class NeuralNetwork.
     */
    @Test
    public void testIris2() {
        System.out.println("Iris binary");
        ArffParser arffParser = new ArffParser();
        arffParser.setResponseIndex(4);
        try {
            AttributeDataset iris = arffParser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/iris.arff"));
            double[][] x = iris.toArray(new double[iris.size()][]);
            int[] y = iris.toArray(new int[iris.size()]);

            for (int i = 0; i < y.length; i++) {
                if (y[i] == 2) {
                    y[i] = 1;
                } else {
                    y[i] = 0;
                }
            }

            int n = x.length;
            int p = x[0].length;
            double[] mu = Math.colMeans(x);
            double[] sd = Math.colSds(x);
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < p; j++) {
                    x[i][j] = (x[i][j] - mu[j]) / sd[j];
                }
            }

            LOOCV loocv = new LOOCV(n);
            int error = 0;
            for (int i = 0; i < n; i++) {
                double[][] trainx = Math.slice(x, loocv.train[i]);
                int[] trainy = Math.slice(y, loocv.train[i]);
                NeuralNetwork net = new NeuralNetwork(NeuralNetwork.ErrorFunction.CROSS_ENTROPY, NeuralNetwork.ActivationFunction.LOGISTIC_SIGMOID, x[0].length, 10, 1);
                for (int j = 0; j < 30; j++) {
                    net.learn(trainx, trainy);
                }

                if (y[loocv.test[i]] != net.predict(x[loocv.test[i]]))
                    error++;
            }

            System.out.println("Neural network error = " + error);
            assertTrue(error <= 8);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of learn method, of class NeuralNetwork.
     */
    @Test
    public void testSegment() {
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
            
            int p = x[0].length;
            double[] mu = Math.colMin(x);
            double[] sd = Math.colMax(x);
            for (int i = 0; i < x.length; i++) {
                for (int j = 0; j < p; j++) {
                    x[i][j] = (x[i][j] - mu[j]) / sd[j];
                }
            }

            for (int i = 0; i < testx.length; i++) {
                for (int j = 0; j < p; j++) {
                    testx[i][j] = (testx[i][j] - mu[j]) / sd[j];
                }
            }

            NeuralNetwork net = new NeuralNetwork(NeuralNetwork.ErrorFunction.CROSS_ENTROPY, NeuralNetwork.ActivationFunction.SOFTMAX, x[0].length, 30, Math.max(y)+1);
            for (int j = 0; j < 20; j++) {
                net.learn(x, y);
            }
            
            int error = 0;
            for (int i = 0; i < testx.length; i++) {
                if (net.predict(testx[i]) != testy[i]) {
                    error++;
                }
            }

            System.out.format("Segment error rate = %.2f%%%n", 100.0 * error / testx.length);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of learn method, of class NeuralNetwork.
     */
    @Test
    public void testSegmentLMS() {
        System.out.println("Segment LMS");
        ArffParser parser = new ArffParser();
        parser.setResponseIndex(19);
        try {
            AttributeDataset train = parser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/segment-challenge.arff"));
            AttributeDataset test = parser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/segment-test.arff"));

            double[][] x = train.toArray(new double[0][]);
            int[] y = train.toArray(new int[0]);
            double[][] testx = test.toArray(new double[0][]);
            int[] testy = test.toArray(new int[0]);
            
            int p = x[0].length;
            double[] mu = Math.colMin(x);
            double[] sd = Math.colMax(x);
            for (int i = 0; i < x.length; i++) {
                for (int j = 0; j < p; j++) {
                    x[i][j] = (x[i][j] - mu[j]) / sd[j];
                }
            }

            for (int i = 0; i < testx.length; i++) {
                for (int j = 0; j < p; j++) {
                    testx[i][j] = (testx[i][j] - mu[j]) / sd[j];
                }
            }

            NeuralNetwork net = new NeuralNetwork(NeuralNetwork.ErrorFunction.LEAST_MEAN_SQUARES, NeuralNetwork.ActivationFunction.LOGISTIC_SIGMOID, x[0].length, 30, Math.max(y)+1);
            for (int j = 0; j < 30; j++) {
                net.learn(x, y);
            }
            int error = 0;
            for (int i = 0; i < testx.length; i++) {
                if (net.predict(testx[i]) != testy[i]) {
                    error++;
                }
            }

            System.out.format("Segment error rate = %.2f%%%n", 100.0 * error / testx.length);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of learn method, of class NeuralNetwork.
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
            
            int p = x[0].length;
            double[] mu = Math.colMeans(x);
            double[] sd = Math.colSds(x);
            for (int i = 0; i < x.length; i++) {
                for (int j = 0; j < p; j++) {
                    x[i][j] = (x[i][j] - mu[j]) / sd[j];
                }
            }

            for (int i = 0; i < testx.length; i++) {
                for (int j = 0; j < p; j++) {
                    testx[i][j] = (testx[i][j] - mu[j]) / sd[j];
                }
            }

            NeuralNetwork net = new NeuralNetwork(NeuralNetwork.ErrorFunction.CROSS_ENTROPY, NeuralNetwork.ActivationFunction.SOFTMAX, x[0].length, 40, Math.max(y)+1);
            for (int j = 0; j < 30; j++) {
                net.learn(x, y);
            }
            int error = 0;
            for (int i = 0; i < testx.length; i++) {
                if (net.predict(testx[i]) != testy[i]) {
                    error++;
                }
            }

            System.out.format("USPS error rate = %.2f%%%n", 100.0 * error / testx.length);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of learn method, of class NeuralNetwork.
     */
    @Test
    public void testUSPSLMS() {
        System.out.println("USPS LMS");
        DelimitedTextParser parser = new DelimitedTextParser();
        parser.setResponseIndex(new NominalAttribute("class"), 0);
        try {
            AttributeDataset train = parser.parse("USPS Train", smile.data.parser.IOUtils.getTestDataFile("usps/zip.train"));
            AttributeDataset test = parser.parse("USPS Test", smile.data.parser.IOUtils.getTestDataFile("usps/zip.test"));

            double[][] x = train.toArray(new double[train.size()][]);
            int[] y = train.toArray(new int[train.size()]);
            double[][] testx = test.toArray(new double[test.size()][]);
            int[] testy = test.toArray(new int[test.size()]);
            
            int p = x[0].length;
            double[] mu = Math.colMeans(x);
            double[] sd = Math.colSds(x);
            for (int i = 0; i < x.length; i++) {
                for (int j = 0; j < p; j++) {
                    x[i][j] = (x[i][j] - mu[j]) / sd[j];
                }
            }

            for (int i = 0; i < testx.length; i++) {
                for (int j = 0; j < p; j++) {
                    testx[i][j] = (testx[i][j] - mu[j]) / sd[j];
                }
            }

            NeuralNetwork net = new NeuralNetwork(NeuralNetwork.ErrorFunction.LEAST_MEAN_SQUARES, NeuralNetwork.ActivationFunction.LOGISTIC_SIGMOID, x[0].length, 40, Math.max(y)+1);
            for (int j = 0; j < 30; j++) {
                net.learn(x, y);
            }
            int error = 0;
            for (int i = 0; i < testx.length; i++) {
                if (net.predict(testx[i]) != testy[i]) {
                    error++;
                }
            }

            System.out.format("USPS error rate = %.2f%%%n", 100.0 * error / testx.length);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }
}