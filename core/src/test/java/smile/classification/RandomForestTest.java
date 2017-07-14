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

import smile.sort.QuickSort;
import smile.data.Attribute;
import smile.math.Math;
import smile.validation.LOOCV;
import smile.data.parser.ArffParser;
import smile.data.AttributeDataset;
import smile.data.NominalAttribute;
import smile.data.parser.DelimitedTextParser;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng
 */
public class RandomForestTest {
    
    public RandomForestTest() {
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
     * Test of learn method, of class RandomForest.
     */
    @Test
    public void testWeather() {
        System.out.println("Weather");
        ArffParser arffParser = new ArffParser();
        arffParser.setResponseIndex(4);
        try {
            AttributeDataset weather = arffParser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/weather.nominal.arff"));
            double[][] x = weather.toArray(new double[weather.size()][]);
            int[] y = weather.toArray(new int[weather.size()]);

            int n = x.length;
            LOOCV loocv = new LOOCV(n);
            int error = 0;
            for (int i = 0; i < n; i++) {
                double[][] trainx = Math.slice(x, loocv.train[i]);
                int[] trainy = Math.slice(y, loocv.train[i]);
                
                RandomForest forest = new RandomForest(weather.attributes(), trainx, trainy, 100);
                if (y[loocv.test[i]] != forest.predict(x[loocv.test[i]]))
                    error++;
            }
            
            System.out.println("Random Forest error = " + error);
            assertTrue(error <= 7);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of learn method, of class RandomForest.
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
                
                RandomForest forest = new RandomForest(iris.attributes(), trainx, trainy, 100);
                if (y[loocv.test[i]] != forest.predict(x[loocv.test[i]]))
                    error++;
            }
            
            System.out.println("Random Forest error = " + error);
            assertTrue(error <= 9);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of learn method, of class RandomForest.
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
            
            RandomForest forest = new RandomForest(x, y, 200);
            
            int error = 0;
            for (int i = 0; i < testx.length; i++) {
                if (forest.predict(testx[i]) != testy[i]) {
                    error++;
                }
            }

            System.out.println("USPS error = " + error);
            System.out.format("USPS OOB error rate = %.2f%%%n", 100.0 * forest.error());
            System.out.format("USPS error rate = %.2f%%%n", 100.0 * error / testx.length);
            assertTrue(error <= 225);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of learn method, of class RandomForest.
     */
    @Test
    public void testUSPSNominal() {
        System.out.println("USPS nominal");
        DelimitedTextParser parser = new DelimitedTextParser();
        parser.setResponseIndex(new NominalAttribute("class"), 0);
        try {
            AttributeDataset train = parser.parse("USPS Train", smile.data.parser.IOUtils.getTestDataFile("usps/zip.train"));
            AttributeDataset test = parser.parse("USPS Test", smile.data.parser.IOUtils.getTestDataFile("usps/zip.test"));

            double[][] x = train.toArray(new double[train.size()][]);
            int[] y = train.toArray(new int[train.size()]);
            double[][] testx = test.toArray(new double[test.size()][]);
            int[] testy = test.toArray(new int[test.size()]);
            
            for (double[] xi : x) {
                for (int i = 0; i < xi.length; i++) {
                    xi[i] = Math.round(255*(xi[i]+1)/2);
                }
            }
            
            for (double[] xi : testx) {
                for (int i = 0; i < xi.length; i++) {
                    xi[i] = Math.round(255*(xi[i]+1)/2);
                }
            }
            
            Attribute[] attributes = new Attribute[256];
            String[] values = new String[attributes.length];
            for (int i = 0; i < attributes.length; i++) {
                values[i] = String.valueOf(i);
            }
            
            for (int i = 0; i < attributes.length; i++) {
                attributes[i] = new NominalAttribute("V"+i, values);
            }
            
            RandomForest forest = new RandomForest(attributes, x, y, 200);
            
            int error = 0;
            for (int i = 0; i < testx.length; i++) {
                if (forest.predict(testx[i]) != testy[i]) {
                    error++;
                }
            }

            System.out.println(error);
            System.out.format("USPS OOB error rate = %.2f%%%n", 100.0 * forest.error());
            System.out.format("USPS error rate = %.2f%%%n", 100.0 * error / testx.length);
            
            double[] accuracy = forest.test(testx, testy);
            for (int i = 1; i <= accuracy.length; i++) {
                System.out.format("%d trees accuracy = %.2f%%%n", i, 100.0 * accuracy[i-1]);
            }
            
            double[] importance = forest.importance();
            int[] index = QuickSort.sort(importance);
            for (int i = importance.length; i-- > 0; ) {
                System.out.format("%s importance is %.4f%n", train.attributes()[index[i]], importance[i]);
            }

            System.out.println("USPS Nominal error = " + error);
            System.out.format("USPS Nominal OOB error rate = %.2f%%%n", 100.0 * forest.error());
            System.out.format("USPS Nominal error rate = %.2f%%%n", 100.0 * error / testx.length);
            assertTrue(error <= 250);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }
}
