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

package smile.regression;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import smile.data.AttributeDataset;
import smile.data.NumericAttribute;
import smile.data.parser.ArffParser;
import smile.data.parser.DelimitedTextParser;
import smile.math.Math;
import smile.validation.CrossValidation;

/**
 *
 * @author rayeaster
 */
public class ElasticNetTest {
    public ElasticNetTest() {
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
     * Test of learn method, of class LinearRegression.
     */
    @Test
    public void testCPU() {
        System.out.println("CPU");
        ArffParser parser = new ArffParser();
        parser.setResponseIndex(6);
        try {
            AttributeDataset data = parser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/cpu.arff"));
            double[][] datax = data.toArray(new double[data.size()][]);
            double[] datay = data.toArray(new double[data.size()]);

            int n = datax.length;
            int k = 10;

            CrossValidation cv = new CrossValidation(n, k);
            double rss = 0.0;
            for (int i = 0; i < k; i++) {
                double[][] trainx = Math.slice(datax, cv.train[i]);
                double[] trainy = Math.slice(datay, cv.train[i]);
                double[][] testx = Math.slice(datax, cv.test[i]);
                double[] testy = Math.slice(datay, cv.test[i]);

                ElasticNet elasticnet = new ElasticNet(trainx, trainy, 0.8, 0.2);

                for (int j = 0; j < testx.length; j++) {
                    double r = testy[j] - elasticnet.predict(testx[j]);
                    rss += r * r;
                }
            }

            System.out.println("CPU 10-CV RMSE = " + Math.sqrt(rss / n));
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of learn method, of class LinearRegression.
     */
    @Test
    public void tesProstate() {
        System.out.println("---ProStateCancer---");
        DelimitedTextParser parser = new DelimitedTextParser();
        parser.setResponseIndex(new NumericAttribute("lpsa"), 8);
        parser.setColumnNames(true);
        try {
            AttributeDataset train = parser.parse("prostate Train",
                    smile.data.parser.IOUtils.getTestDataFile("regression/prostate-train.csv"));
            AttributeDataset test = parser.parse("prostate Test",
                    smile.data.parser.IOUtils.getTestDataFile("regression/prostate-test.csv"));

            double[][] x = train.toArray(new double[train.size()][]);
            double[] y = train.toArray(new double[train.size()]);
            double[][] testx = test.toArray(new double[test.size()][]);
            double[] testy = test.toArray(new double[test.size()]);

            ElasticNet elasticnet = new ElasticNet(x, y, 0.8, 0.2);

            double testrss = 0;
            int n = testx.length;
            for (int j = 0; j < testx.length; j++) {
                double r = testy[j] - elasticnet.predict(testx[j]);
                testrss += r * r;
            }

            System.out.println("Prostate Test MSE = " + testrss / n);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of learn method, of class LinearRegression.
     */
    @Test
    public void tesAbalone() {
        System.out.println("---Abalone---");
        DelimitedTextParser parser = new DelimitedTextParser();
        parser.setResponseIndex(new NumericAttribute("ring"), 8);
        parser.setColumnNames(false);
        parser.setDelimiter(",");
        parser.addIgnoredColumn(0);
        try {
            AttributeDataset train = parser.parse("abalone Train",
                    smile.data.parser.IOUtils.getTestDataFile("regression/abalone-train.data"));
            AttributeDataset test = parser.parse("abalone Test",
                    smile.data.parser.IOUtils.getTestDataFile("regression/abalone-test.data"));

            double[][] x = train.toArray(new double[train.size()][]);
            double[] y = train.toArray(new double[train.size()]);
            double[][] testx = test.toArray(new double[test.size()][]);
            double[] testy = test.toArray(new double[test.size()]);

            ElasticNet elasticnet = new ElasticNet(x, y, 0.8, 0.2);

            double testrss = 0;
            int n = testx.length;
            for (int j = 0; j < testx.length; j++) {
                double r = testy[j] - elasticnet.predict(testx[j]);
                testrss += r * r;
            }

            System.out.println("Abalone Test MSE = " + testrss / n);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of learn method, of class LinearRegression.
     */
    @Test
    public void tesDiabetes() {
        System.out.println("---Diabetes---");
        DelimitedTextParser parser = new DelimitedTextParser();
        parser.setResponseIndex(new NumericAttribute("y"), 0);
        parser.setColumnNames(true);
        parser.setDelimiter(",");
        try {
            AttributeDataset data = parser.parse("diabetes",
                    smile.data.parser.IOUtils.getTestDataFile("regression/diabetes.csv"));
            double[][] datax = data.toArray(new double[data.size()][]);
            double[] datay = data.toArray(new double[data.size()]);

            int n = datax.length;
            int k = 40;

            CrossValidation cv = new CrossValidation(n, k);
            double rss = 0.0;
            for (int i = 0; i < k; i++) {
                double[][] trainx = Math.slice(datax, cv.train[i]);
                double[] trainy = Math.slice(datay, cv.train[i]);
                double[][] testx = Math.slice(datax, cv.test[i]);
                double[] testy = Math.slice(datay, cv.test[i]);

                ElasticNet elasticnet = new ElasticNet(trainx, trainy, 0.8, 0.2);

                for (int j = 0; j < testx.length; j++) {
                    double r = testy[j] - elasticnet.predict(testx[j]);
                    rss += r * r;
                }
            }

            System.out.println("Diabetes 40-CV RMSE = " + Math.sqrt(rss / n));
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }
}