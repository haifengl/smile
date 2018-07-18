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
import smile.data.parser.ArffParser;
import smile.data.AttributeDataset;
import smile.data.parser.DelimitedTextParser;
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
 * @author Haifeng
 */
public class LogisticRegressionTest {

    public LogisticRegressionTest() {
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
     * Test of learn method, of class LogisticRegression.
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
            LOOCV loocv = new LOOCV(n);
            int error = 0;
            for (int i = 0; i < n; i++) {
                double[][] trainx = Math.slice(x, loocv.train[i]);
                int[] trainy = Math.slice(y, loocv.train[i]);
                LogisticRegression logit = new LogisticRegression(trainx, trainy);

                if (y[loocv.test[i]] != logit.predict(x[loocv.test[i]]))
                    error++;
            }

            System.out.println("Logistic Regression error = " + error);
            assertEquals(3, error);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of online learning method, of class LogisticRegression.
     */
    @Test
    public void testIris2WithSgd() {
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
            LOOCV loocv = new LOOCV(n);
            int error = 0;
            LogisticRegression logit = null;
            for (int i = 0; i < n; i++) {
                double[][] trainx = Math.slice(x, loocv.train[i]);
                int[] trainy = Math.slice(y, loocv.train[i]);

                int sgdIdx = (int) (trainx.length * 0.95);    
                double[][] bx = new double[sgdIdx][trainx[0].length];
                int[] by = new int[sgdIdx];
                for(int j = 0;j < sgdIdx;j++) {     
                    for(int k = 0;k < trainx[0].length;k++) {
                    	 bx[j][k] = trainx[j][k];
                    }
                    by[j] = trainy[j];   
                }
                double[][] xsgd = new double[trainx.length - sgdIdx][trainx[0].length];
                int[] ysgd = new int[trainx.length - sgdIdx];
                for(int l = sgdIdx;l < trainx.length;l++) {     
                    for(int m = 0;m < trainx[0].length;m++) {
                      	 xsgd[l - sgdIdx][m] = trainx[l][m];
                    }
                    ysgd[l - sgdIdx] = trainy[l];      	
                }
                
                logit = new LogisticRegression(bx, by);
                logit.setLearningRate(1e-7);
                
                System.out.println("SGD-Iris Binary start...");
                //
                // permutate before sgd for better performance
                //
                int[] idxsgd = new int[ysgd.length];
                for(int isgd = 0;isgd < ysgd.length;isgd++) {
                	idxsgd[isgd] = isgd;
                }
                Math.permutate(idxsgd);            
                for(int a = 0;a < ysgd.length;a++) {
                	logit.learn(xsgd[idxsgd[a]], ysgd[idxsgd[a]]);
                }                
                
                if (y[loocv.test[i]] != logit.predict(x[loocv.test[i]]))
                    error++;            
            }

            System.out.println("SGD-Iris Binary Logistic Regression error = " + error);
            assertTrue(error <= 3);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of learn method, of class LogisticRegression.
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
            LOOCV loocv = new LOOCV(n);
            int error = 0;
            for (int i = 0; i < n; i++) {
                double[][] trainx = Math.slice(x, loocv.train[i]);
                int[] trainy = Math.slice(y, loocv.train[i]);
                LogisticRegression logit = new LogisticRegression(trainx, trainy);

                if (y[loocv.test[i]] != logit.predict(x[loocv.test[i]]))
                    error++;
            }

            System.out.println("Logistic Regression error = " + error);
            assertEquals(3, error);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of learn method, of class LogisticRegression.
     */
    @Test
    public void testSegment() {
        System.out.println("Segment");
        ArffParser arffParser = new ArffParser();
        arffParser.setResponseIndex(19);
        try {
            AttributeDataset train = arffParser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/segment-challenge.arff"));
            AttributeDataset test = arffParser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/segment-test.arff"));

            double[][] x = train.toArray(new double[train.size()][]);
            int[] y = train.toArray(new int[train.size()]);
            double[][] testx = test.toArray(new double[test.size()][]);
            int[] testy = test.toArray(new int[test.size()]);

            LogisticRegression logit = new LogisticRegression(x, y, 0.05, 1E-3, 1000);
            
            int error = 0;
            for (int i = 0; i < testx.length; i++) {
                if (logit.predict(testx[i]) != testy[i]) {
                    error++;
                }
            }

            System.out.format("Segment error rate = %.2f%%%n", 100.0 * error / testx.length);
            assertEquals(48, error);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }
    
    /**
     * Test of online learning method, of class LogisticRegression.
     */
    @Test
    public void testSegmentWithSgd() {
        System.out.println("SGD-Segment");
        ArffParser arffParser = new ArffParser();
        arffParser.setResponseIndex(19);
        try {
            AttributeDataset train = arffParser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/segment-challenge.arff"));
            AttributeDataset test = arffParser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/segment-test.arff"));

            int sgdIdx = (int) (train.size() * 0.7);            
            double[][] x = train.range(0, sgdIdx).toArray(new double[sgdIdx][]);
            int[] y = train.range(0, sgdIdx).toArray(new int[sgdIdx]);     
            double[][] xsgd = train.range(sgdIdx, train.size()).toArray(new double[train.size() - sgdIdx][]);
            int[] ysgd = train.range(sgdIdx, train.size()).toArray(new int[train.size() - sgdIdx]);
            
            double[][] testx = test.toArray(new double[test.size()][]);
            int[] testy = test.toArray(new int[test.size()]);
            
            LogisticRegression logit = new LogisticRegression(x, y, 0.3, 1E-3, 1000);
            System.out.println("SGD-Segemnt start...");
            logit.setLearningRate(1e-7);
            //
            // permutate before sgd for better performance
            //
            int[] idx = new int[ysgd.length];
            for(int i = 0;i < ysgd.length;i++) {
            	idx[i] = i;
            }
            Math.permutate(idx);            
            for(int i = 0;i < ysgd.length;i++) {
            	logit.learn(xsgd[idx[i]], ysgd[idx[i]]);
            }
            
            int error = 0;
            for (int i = 0; i < testx.length; i++) {
                if (logit.predict(testx[i]) != testy[i]) {
                    error++;
                }
            }

            System.out.format("SGD-Segment error rate = %.2f%%%n", 100.0 * error / testx.length);
            assertTrue(error <= 78);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of learn method, of class LogisticRegression.
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
            
            LogisticRegression logit = new LogisticRegression(x, y, 0.3, 1E-3, 1000);
            
            int error = 0;
            for (int i = 0; i < testx.length; i++) {
                if (logit.predict(testx[i]) != testy[i]) {
                    error++;
                }
            }

            System.out.format("USPS error rate = %.2f%%%n", 100.0 * error / testx.length);
            assertEquals(188, error);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }
    
    /**
     * Test of online learning method, of class LogisticRegression.
     */
    @Test
    public void testUSPSWithSgd() {
        System.out.println("SGD-USPS");
        DelimitedTextParser parser = new DelimitedTextParser();
        parser.setResponseIndex(new NominalAttribute("class"), 0);
        try {
            AttributeDataset train = parser.parse("USPS Train", smile.data.parser.IOUtils.getTestDataFile("usps/zip.train"));
            AttributeDataset test = parser.parse("USPS Test", smile.data.parser.IOUtils.getTestDataFile("usps/zip.test"));

            int sgdIdx = (int) (train.size() * 0.8);            
            double[][] x = train.range(0, sgdIdx).toArray(new double[sgdIdx][]);
            int[] y = train.range(0, sgdIdx).toArray(new int[sgdIdx]);     
            double[][] xsgd = train.range(sgdIdx, train.size()).toArray(new double[train.size() - sgdIdx][]);
            int[] ysgd = train.range(sgdIdx, train.size()).toArray(new int[train.size() - sgdIdx]);
            
            double[][] testx = test.toArray(new double[test.size()][]);
            int[] testy = test.toArray(new int[test.size()]);
            
            LogisticRegression logit = new LogisticRegression(x, y, 0.3, 1E-3, 1000);
            System.out.println("SGD-USPS start...");
            //
            // permutate before sgd for better performance
            //
            int[] idx = new int[ysgd.length];
            for(int i = 0;i < ysgd.length;i++) {
            	idx[i] = i;
            }
            Math.permutate(idx);            
            for(int i = 0;i < ysgd.length;i++) {
            	logit.learn(xsgd[idx[i]], ysgd[idx[i]]);
            }
            
            int error = 0;
            for (int i = 0; i < testx.length; i++) {
                if (logit.predict(testx[i]) != testy[i]) {
                    error++;
                }
            }

            System.out.format("SGD-USPS error rate = %.2f%%%n", 100.0 * error / testx.length);
            assertTrue(error <= 208);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }
}