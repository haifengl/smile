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

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import smile.data.AttributeDataset;
import smile.data.NominalAttribute;
import smile.data.parser.ArffParser;
import smile.data.parser.DelimitedTextParser;
import smile.math.Math;
import smile.math.kernel.GaussianKernel;
import smile.math.kernel.LinearKernel;
import smile.math.kernel.PolynomialKernel;

/**
 *
 * @author Haifeng Li
 */
public class SVMTest {

    public SVMTest() {
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
     * Test of learn method, of class SVM.
     */
    @Test
    public void testLearn() {
        System.out.println("learn");
        ArffParser arffParser = new ArffParser();
        arffParser.setResponseIndex(4);
        try {
            AttributeDataset iris = arffParser.parse(this.getClass().getResourceAsStream("/smile/data/weka/iris.arff"));
            double[][] x = iris.toArray(new double[iris.size()][]);
            int[] y = iris.toArray(new int[iris.size()]);

            SVM<double[]> svm = new SVM<double[]>(new LinearKernel(), 10.0, Math.max(y)+1, SVM.Multiclass.ONE_VS_ALL);
            svm.learn(x, y);
            svm.learn(x, y);
            svm.finish();
            
            int error = 0;
            for (int i = 0; i < x.length; i++) {
                if (svm.predict(x[i]) != y[i]) {
                    error++;
                }
            }
            System.out.println("Linear ONE vs. ALL error = " + error);
            assertTrue(error <= 10);

            svm = new SVM<double[]>(new GaussianKernel(1), 1.0, Math.max(y)+1, SVM.Multiclass.ONE_VS_ALL);
            svm.learn(x, y);
            svm.learn(x, y);
            svm.finish();
            
            error = 0;
            for (int i = 0; i < x.length; i++) {
                if (svm.predict(x[i]) != y[i]) {
                    error++;
                }
            }
            System.out.println("Gaussian ONE vs. ALL error = " + error);
            assertTrue(error <= 5);

            svm = new SVM<double[]>(new GaussianKernel(1), 1.0, Math.max(y)+1, SVM.Multiclass.ONE_VS_ONE);
            svm.learn(x, y);
            svm.learn(x, y);
            svm.finish();
            
            error = 0;
            for (int i = 0; i < x.length; i++) {
                if (svm.predict(x[i]) != y[i]) {
                    error++;
                }
            }
            System.out.println("Gaussian ONE vs. ONE error = " + error);
            assertTrue(error <= 5);

            svm = new SVM<double[]>(new PolynomialKernel(2), 1.0, Math.max(y)+1, SVM.Multiclass.ONE_VS_ALL);
            svm.learn(x, y);
            svm.learn(x, y);
            svm.finish();
            
            error = 0;
            for (int i = 0; i < x.length; i++) {
                if (svm.predict(x[i]) != y[i]) {
                    error++;
                }
            }
            System.out.println("Polynomial ONE vs. ALL error = " + error);
            assertTrue(error <= 5);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Test of learn method, of class SVM.
     */
    @Test
    public void testSegment() {
        System.out.println("Segment");
        ArffParser parser = new ArffParser();
        parser.setResponseIndex(19);
        try {
            AttributeDataset train = parser.parse(this.getClass().getResourceAsStream("/smile/data/weka/segment-challenge.arff"));
            AttributeDataset test = parser.parse(this.getClass().getResourceAsStream("/smile/data/weka/segment-test.arff"));

            System.out.println(train.size() + " " + test.size());
            double[][] x = train.toArray(new double[0][]);
            int[] y = train.toArray(new int[0]);
            double[][] testx = test.toArray(new double[0][]);
            int[] testy = test.toArray(new int[0]);
            
            SVM<double[]> svm = new SVM<double[]>(new GaussianKernel(8.0), 5.0, Math.max(y)+1, SVM.Multiclass.ONE_VS_ALL);
            svm.learn(x, y);
            svm.finish();
            
            int error = 0;
            for (int i = 0; i < testx.length; i++) {
                if (svm.predict(testx[i]) != testy[i]) {
                    error++;
                }
            }

            System.out.format("Segment error rate = %.2f%%\n", 100.0 * error / testx.length);
            assertTrue(error < 70);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Test of learn method, of class SVM.
     */
    @Test
    public void testUSPS() {
        System.out.println("USPS");
        DelimitedTextParser parser = new DelimitedTextParser();
        parser.setResponseIndex(new NominalAttribute("class"), 0);
        try {
            AttributeDataset train = parser.parse("USPS Train", this.getClass().getResourceAsStream("/smile/data/usps/zip.train"));
            AttributeDataset test = parser.parse("USPS Test", this.getClass().getResourceAsStream("/smile/data/usps/zip.test"));

            double[][] x = train.toArray(new double[train.size()][]);
            int[] y = train.toArray(new int[train.size()]);
            double[][] testx = test.toArray(new double[test.size()][]);
            int[] testy = test.toArray(new int[test.size()]);
            
            SVM<double[]> svm = new SVM<double[]>(new GaussianKernel(8.0), 5.0, Math.max(y)+1, SVM.Multiclass.ONE_VS_ONE);
            svm.learn(x, y);
            svm.finish();
            
            int error = 0;
            for (int i = 0; i < testx.length; i++) {
                if (svm.predict(testx[i]) != testy[i]) {
                    error++;
                }
            }

            System.out.format("USPS error rate = %.2f%%\n", 100.0 * error / testx.length);
            assertTrue(error < 95);
            
            System.out.println("USPS one more epoch...");
            for (int i = 0; i < x.length; i++) {
                int j = Math.randomInt(x.length);
                svm.learn(x[j], y[j]);
            }
            
            svm.finish();

            error = 0;
            for (int i = 0; i < testx.length; i++) {
                if (svm.predict(testx[i]) != testy[i]) {
                    error++;
                }
            }
            System.out.format("USPS error rate = %.2f%%\n", 100.0 * error / testx.length);
            assertTrue(error < 95);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
