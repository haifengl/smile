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
import smile.classification.FLD.Model;
import smile.data.AttributeDataset;
import smile.data.parser.DelimitedTextParser;
import smile.data.parser.ArffParser;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import scala.collection.parallel.immutable.ParSet;
import smile.math.Math;
import smile.validation.LOOCV;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class FLDTest {

    public FLDTest() {
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
     * Test of predict method, of class FDA.
     */
    @Test
    public void testPredict() {
        System.out.println("---IRIS---");
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
                FLD fisher = new FLD(trainx, trainy);

                if (y[loocv.test[i]] != fisher.predict(x[loocv.test[i]]))
                    error++;
            }

            System.out.format("FLD-Iris error = " + error + ", error rate = %.2f%% %n", 100.0 * error / n);
            assertTrue(error <= 25);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of learn method, of class FDA.
     */
    @Test
    public void testUSPS() {
        System.out.println("---USPS---");
        DelimitedTextParser parser = new DelimitedTextParser();
        parser.setResponseIndex(new NominalAttribute("class"), 0);
        try {
            AttributeDataset train = parser.parse("USPS Train", smile.data.parser.IOUtils.getTestDataFile("usps/zip.train"));
            AttributeDataset test = parser.parse("USPS Test", smile.data.parser.IOUtils.getTestDataFile("usps/zip.test"));

            double[][] x = train.toArray(new double[train.size()][]);
            int[] y = train.toArray(new int[train.size()]);
            double[][] testx = test.toArray(new double[test.size()][]);
            int[] testy = test.toArray(new int[test.size()]);
            
            FLD fisher = new FLD(x, y, Model.HIGH_DIM);
                        
            int error = 0;
            for (int i = 0; i < testx.length; i++) {
                if (fisher.predict(testx[i]) != testy[i]) {
                    error++;
                }
            }

            System.out.format("FLD-USPS error = " + error + ",  error rate = %.2f%% %n", 100.0 * error / testy.length);
            assertTrue(error <= 561);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of learn method, of class FDA.
     */
    @Test
    public void testPendigits() {
        System.out.println("---Pendigits---");
        DelimitedTextParser parser = new DelimitedTextParser();
        parser.setResponseIndex(new NominalAttribute("class"), 16);
        try {
            AttributeDataset pendigits = parser.parse(smile.data.parser.IOUtils.getTestDataFile("classification/pendigits.txt"));
            double[][] x = pendigits.toArray(new double[pendigits.size()][]);
            int[] y = pendigits.toArray(new int[pendigits.size()]);

            int n = x.length;
            LOOCV loocv = new LOOCV(n);
            int error = 0;
            for (int i = 0; i < n; i++) {
                double[][] trainx = Math.slice(x, loocv.train[i]);
                int[] trainy = Math.slice(y, loocv.train[i]);
                FLD fisher = new FLD(trainx, trainy, Model.HIGH_DIM);

                if (y[loocv.test[i]] != fisher.predict(x[loocv.test[i]]))
                    error++;
            }

            System.out.format("FLD-Pendigits error = " + error + ", error rate = %.2f%% %n", 100.0 * error / n);
            assertTrue(error <= 900);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of learn method, of class FDA.
     */
    @Test
    public void testBreastCancer() {
        System.out.println("---Breastcancer---");
        DelimitedTextParser parser = new DelimitedTextParser();
        parser.setResponseIndex(new NominalAttribute("class"), 1);
        parser.setColumnNames(true);
        parser.setDelimiter(",");
        try {
            AttributeDataset breastcancer = parser.parse(smile.data.parser.IOUtils.getTestDataFile("classification/breastcancer.csv"));
            double[][] x = breastcancer.toArray(new double[breastcancer.size()][]);
            int[] y = breastcancer.toArray(new int[breastcancer.size()]);

            int n = x.length;
            LOOCV loocv = new LOOCV(n);
            int error = 0;
            for (int i = 0; i < n; i++) {
                double[][] trainx = Math.slice(x, loocv.train[i]);
                int[] trainy = Math.slice(y, loocv.train[i]);
                FLD fisher = new FLD(trainx, trainy, Model.HIGH_DIM);

                if (y[loocv.test[i]] != fisher.predict(x[loocv.test[i]]))
                    error++;
            }

            System.out.format("FLD-Breastcancer error = " + error + ", error rate = %.2f%% %n", 100.0 * error / n);
            assertTrue(error <= 900);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }
    
    @Test
    public void testSegmentation() {
        System.out.println("---Segmentation---");
        ArffParser arffParser = new ArffParser();
        arffParser.setResponseIndex(19);
        try {
            AttributeDataset seg = arffParser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/segment-challenge.arff"));
            AttributeDataset segtest = arffParser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/segment-test.arff"));
            
            double[][] x = seg.toArray(new double[seg.size()][]);
            int[] y = seg.toArray(new int[seg.size()]);
            double[][] testx = segtest.toArray(new double[segtest.size()][]);
            int[] testy = segtest.toArray(new int[segtest.size()]);

            FLD fisher = new FLD(x, y, 6, 3*1E-7, Model.HIGH_DIM);
            
            int error = 0;
            for (int i = 0; i < testx.length; i++) {
                if (fisher.predict(testx[i]) != testy[i]) {
                    error++;
                }
            }

            System.out.format("FLD-Segmentation error = " + error + ", error rate = %.2f%% %n", 100.0 * error / testy.length);
            assertTrue(error <= 100);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }
}