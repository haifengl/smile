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
package smile.validation;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;
import smile.classification.ClassifierTrainer;
import smile.classification.DecisionTree;
import smile.classification.LDA;
import smile.regression.RBFNetwork;
import smile.util.SmileUtils;
import smile.data.AttributeDataset;
import smile.data.NominalAttribute;
import smile.data.parser.ArffParser;
import smile.data.parser.DelimitedTextParser;
import smile.math.Math;
import smile.math.distance.EuclideanDistance;
import smile.math.rbf.RadialBasisFunction;

/**
 *
 * @author Haifeng
 */
public class ValidationTest {
    
    public ValidationTest() {
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
     * Test of test method, of class Validation.
     */
    @Test
    public void testTest_3args_1() {
        System.out.println("test");
        DelimitedTextParser parser = new DelimitedTextParser();
        parser.setResponseIndex(new NominalAttribute("class"), 0);
        try {
            AttributeDataset train = parser.parse("USPS Train", smile.data.parser.IOUtils.getTestDataFile("usps/zip.train"));
            AttributeDataset test = parser.parse("USPS Test", smile.data.parser.IOUtils.getTestDataFile("usps/zip.test"));

            double[][] x = train.toArray(new double[train.size()][]);
            int[] y = train.toArray(new int[train.size()]);
            double[][] testx = test.toArray(new double[test.size()][]);
            int[] testy = test.toArray(new int[test.size()]);

            LDA lda = new LDA(x, y);
            double accuracy = Validation.test(lda, testx, testy);
            System.out.println("accuracy = " + accuracy);
            assertEquals(0.8724, accuracy, 1E-4);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of test method, of class Validation.
     */
    @Test
    public void testTest_3args_2() {
        System.out.println("test");
        ArffParser parser = new ArffParser();
        parser.setResponseIndex(6);
        try {
            AttributeDataset data = parser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/cpu.arff"));
            double[] datay = data.toArray(new double[data.size()]);
            double[][] datax = data.toArray(new double[data.size()][]);
            Math.standardize(datax);

            int n = datax.length;
            int m = 3 * n / 4;
            double[][] x = new double[m][];
            double[] y = new double[m];
            double[][] testx = new double[n-m][];
            double[] testy = new double[n-m];
            
            int[] index = Math.permutate(n);
            for (int i = 0; i < m; i++) {
                x[i] = datax[index[i]];
                y[i] = datay[index[i]];
            }
            for (int i = m; i < n; i++) {
                testx[i-m] = datax[index[i]];
                testy[i-m] = datay[index[i]];
            }

            double[][] centers = new double[20][];
            RadialBasisFunction[] rbf = SmileUtils.learnGaussianRadialBasis(x, centers, 2);
            RBFNetwork<double[]> rkhs = new RBFNetwork<>(x, y, new EuclideanDistance(), rbf, centers);
            double rmse = Validation.test(rkhs, testx, testy);
            System.out.println("RMSE = " + rmse);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of test method, of class Validation.
     */
    @Test
    public void testTest_4args_1() {
        System.out.println("test");
        DelimitedTextParser parser = new DelimitedTextParser();
        parser.setResponseIndex(new NominalAttribute("class"), 0);
        try {
            AttributeDataset train = parser.parse("USPS Train", smile.data.parser.IOUtils.getTestDataFile("usps/zip.train"));
            AttributeDataset test = parser.parse("USPS Test", smile.data.parser.IOUtils.getTestDataFile("usps/zip.test"));

            double[][] x = train.toArray(new double[train.size()][]);
            int[] y = train.toArray(new int[train.size()]);
            double[][] testx = test.toArray(new double[test.size()][]);
            int[] testy = test.toArray(new int[test.size()]);

            LDA lda = new LDA(x, y);
            ClassificationMeasure[] measures = {new Accuracy()};
            double[] accuracy = Validation.test(lda, testx, testy, measures);
            System.out.println("accuracy = " + accuracy[0]);
            assertEquals(0.8724, accuracy[0], 1E-4);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of test method, of class Validation.
     */
    @Test
    public void testTest_4args_2() {
        System.out.println("test");
        ArffParser parser = new ArffParser();
        parser.setResponseIndex(6);
        try {
            AttributeDataset data = parser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/cpu.arff"));
            double[] datay = data.toArray(new double[data.size()]);
            double[][] datax = data.toArray(new double[data.size()][]);
            Math.standardize(datax);

            int n = datax.length;
            int m = 3 * n / 4;
            double[][] x = new double[m][];
            double[] y = new double[m];
            double[][] testx = new double[n-m][];
            double[] testy = new double[n-m];
            
            int[] index = Math.permutate(n);
            for (int i = 0; i < m; i++) {
                x[i] = datax[index[i]];
                y[i] = datay[index[i]];
            }
            for (int i = m; i < n; i++) {
                testx[i-m] = datax[index[i]];
                testy[i-m] = datay[index[i]];
            }

            double[][] centers = new double[20][];
            RadialBasisFunction[] rbf = SmileUtils.learnGaussianRadialBasis(x, centers, 2);
            RBFNetwork<double[]> rkhs = new RBFNetwork<>(x, y, new EuclideanDistance(), rbf, centers);

            RegressionMeasure[] measures = {new RMSE(), new MeanAbsoluteDeviation()};
            double[] results = Validation.test(rkhs, testx, testy, measures);
            System.out.println("RMSE = " + results[0]);
            System.out.println("MAD = " + results[1]);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of loocv method, of class Validation.
     */
    @Test
    public void testLoocv_3args_1() {
        System.out.println("loocv");
        ArffParser arffParser = new ArffParser();
        arffParser.setResponseIndex(4);
        try {
            AttributeDataset iris = arffParser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/iris.arff"));
            double[][] x = iris.toArray(new double[iris.size()][]);
            int[] y = iris.toArray(new int[iris.size()]);

            ClassifierTrainer<double[]> trainer = new LDA.Trainer();
            double accuracy = Validation.loocv(trainer, x, y);
            
            System.out.println("LOOCV accuracy = " + accuracy);
            assertEquals(0.8533, accuracy, 1E-4);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of loocv method, of class Validation.
     */
    @Test
    public void testLoocv_3args_2() {
        System.out.println("loocv");
        ArffParser parser = new ArffParser();
        parser.setResponseIndex(6);
        try {
            AttributeDataset data = parser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/cpu.arff"));
            double[] y = data.toArray(new double[data.size()]);
            double[][] x = data.toArray(new double[data.size()][]);
            Math.standardize(x);

            RBFNetwork.Trainer<double[]> trainer = new RBFNetwork.Trainer<>(new EuclideanDistance());
            trainer.setNumCenters(20);
            double rmse = Validation.loocv(trainer, x, y);
            System.out.println("RMSE = " + rmse);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of loocv method, of class Validation.
     */
    @Test
    public void testLoocv_4args_1() {
        System.out.println("loocv");
        ArffParser arffParser = new ArffParser();
        arffParser.setResponseIndex(4);
        try {
            AttributeDataset weather = arffParser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/weather.nominal.arff"));
            double[][] x = weather.toArray(new double[weather.size()][]);
            int[] y = weather.toArray(new int[weather.size()]);

            DecisionTree.Trainer trainer = new DecisionTree.Trainer(3);
            trainer.setAttributes(weather.attributes());
            ClassificationMeasure[] measures = {new Accuracy(), new Recall(), new Precision()};
            double[] results = Validation.loocv(trainer, x, y, measures);
            for (int i = 0; i < measures.length; i++) {
                System.out.println(measures[i] + " = " + results[i]);
            }
            assertEquals(0.6429, results[0], 1E-4);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of loocv method, of class Validation.
     */
    @Test
    public void testLoocv_4args_2() {
        System.out.println("loocv");
        ArffParser parser = new ArffParser();
        parser.setResponseIndex(6);
        try {
            AttributeDataset data = parser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/cpu.arff"));
            double[] y = data.toArray(new double[data.size()]);
            double[][] x = data.toArray(new double[data.size()][]);
            Math.standardize(x);

            RBFNetwork.Trainer<double[]> trainer = new RBFNetwork.Trainer<>(new EuclideanDistance());
            trainer.setNumCenters(20);
            RegressionMeasure[] measures = {new RMSE(), new MeanAbsoluteDeviation()};
            double[] results = Validation.loocv(trainer, x, y, measures);
            System.out.println("RMSE = " + results[0]);
            System.out.println("MAD = " + results[1]);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of cv method, of class Validation.
     */
    @Test
    public void testCv_4args_1() {
        System.out.println("cv");
        ArffParser arffParser = new ArffParser();
        arffParser.setResponseIndex(4);
        try {
            AttributeDataset iris = arffParser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/iris.arff"));
            double[][] x = iris.toArray(new double[iris.size()][]);
            int[] y = iris.toArray(new int[iris.size()]);

            ClassifierTrainer<double[]> trainer = new LDA.Trainer();
            double accuracy = Validation.cv(10, trainer, x, y);
            
            System.out.println("10-fold CV accuracy = " + accuracy);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of cv method, of class Validation.
     */
    @Test
    public void testCv_4args_2() {
        System.out.println("cv");
        ArffParser parser = new ArffParser();
        parser.setResponseIndex(6);
        try {
            AttributeDataset data = parser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/cpu.arff"));
            double[] y = data.toArray(new double[data.size()]);
            double[][] x = data.toArray(new double[data.size()][]);
            Math.standardize(x);

            RBFNetwork.Trainer<double[]> trainer = new RBFNetwork.Trainer<>(new EuclideanDistance());
            trainer.setNumCenters(20);
            double rmse = Validation.cv(10, trainer, x, y);
            System.out.println("RMSE = " + rmse);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of cv method, of class Validation.
     */
    @Test
    public void testCv_5args_1() {
        System.out.println("cv");
        ArffParser arffParser = new ArffParser();
        arffParser.setResponseIndex(4);
        try {
            AttributeDataset iris = arffParser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/iris.arff"));
            double[][] x = iris.toArray(new double[iris.size()][]);
            int[] y = iris.toArray(new int[iris.size()]);

            ClassifierTrainer<double[]> trainer = new LDA.Trainer();
            ClassificationMeasure[] measures = {new Accuracy()};
            double[] results = Validation.cv(10, trainer, x, y, measures);
            for (int i = 0; i < measures.length; i++) {
                System.out.println(measures[i] + " = " + results[i]);
            }
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of cv method, of class Validation.
     */
    @Test
    public void testCv_5args_2() {
        System.out.println("cv");
        ArffParser parser = new ArffParser();
        parser.setResponseIndex(6);
        try {
            AttributeDataset data = parser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/cpu.arff"));
            double[] y = data.toArray(new double[data.size()]);
            double[][] x = data.toArray(new double[data.size()][]);
            Math.standardize(x);

            RBFNetwork.Trainer<double[]> trainer = new RBFNetwork.Trainer<>(new EuclideanDistance());
            trainer.setNumCenters(20);
            RegressionMeasure[] measures = {new RMSE(), new MeanAbsoluteDeviation()};
            double[] results = Validation.cv(10, trainer, x, y, measures);
            System.out.println("RMSE = " + results[0]);
            System.out.println("MAD = " + results[1]);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of bootstrap method, of class Validation.
     */
    @Test
    public void testBootstrap_4args_1() {
        System.out.println("bootstrap");
        ArffParser arffParser = new ArffParser();
        arffParser.setResponseIndex(4);
        try {
            AttributeDataset iris = arffParser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/iris.arff"));
            double[][] x = iris.toArray(new double[iris.size()][]);
            int[] y = iris.toArray(new int[iris.size()]);

            ClassifierTrainer<double[]> trainer = new LDA.Trainer();
            double[] accuracy = Validation.bootstrap(100, trainer, x, y);
            
            System.out.println("100-fold bootstrap accuracy average = " + Math.mean(accuracy));
            System.out.println("100-fold bootstrap accuracy std.dev = " + Math.sd(accuracy));
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of bootstrap method, of class Validation.
     */
    @Test
    public void testBootstrap_4args_2() {
        System.out.println("bootstrap");
        ArffParser parser = new ArffParser();
        parser.setResponseIndex(6);
        try {
            AttributeDataset data = parser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/cpu.arff"));
            double[] y = data.toArray(new double[data.size()]);
            double[][] x = data.toArray(new double[data.size()][]);
            Math.standardize(x);

            RBFNetwork.Trainer<double[]> trainer = new RBFNetwork.Trainer<>(new EuclideanDistance());
            trainer.setNumCenters(20);
            double[] rmse = Validation.bootstrap(100, trainer, x, y);
            System.out.println("100-fold bootstrap RMSE average = " + Math.mean(rmse));
            System.out.println("100-fold bootstrap RMSE std.dev = " + Math.sd(rmse));
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of bootstrap method, of class Validation.
     */
    @Test
    public void testBootstrap_5args_1() {
        System.out.println("bootstrap");
        ArffParser arffParser = new ArffParser();
        arffParser.setResponseIndex(4);
        try {
            AttributeDataset weather = arffParser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/weather.nominal.arff"));
            double[][] x = weather.toArray(new double[weather.size()][]);
            int[] y = weather.toArray(new int[weather.size()]);

            DecisionTree.Trainer trainer = new DecisionTree.Trainer(3);
            trainer.setAttributes(weather.attributes());
            ClassificationMeasure[] measures = {new Accuracy(), new Recall(), new Precision()};
            double[][] results = Validation.bootstrap(100, trainer, x, y, measures);
            for (int i = 0; i < 100; i++) {
                for (int j = 0; j < measures.length; j++) {
                    System.out.format("%s = %.4f\t", measures[j], results[i][j]);
                }
                System.out.println();
            }
            
            System.out.println("On average:");
            double[] avg = Math.colMeans(results);
            for (int j = 0; j < measures.length; j++) {
                System.out.format("%s = %.4f\t", measures[j], avg[j]);
            }
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of bootstrap method, of class Validation.
     */
    @Test
    public void testBootstrap_5args_2() {
        System.out.println("bootstrap");
        ArffParser parser = new ArffParser();
        parser.setResponseIndex(6);
        try {
            AttributeDataset data = parser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/cpu.arff"));
            double[] y = data.toArray(new double[data.size()]);
            double[][] x = data.toArray(new double[data.size()][]);
            Math.standardize(x);

            RBFNetwork.Trainer<double[]> trainer = new RBFNetwork.Trainer<>(new EuclideanDistance());
            trainer.setNumCenters(20);
            RegressionMeasure[] measures = {new RMSE(), new MeanAbsoluteDeviation()};
            double[][] results = Validation.bootstrap(100, trainer, x, y, measures);
            System.out.println("100-fold bootstrap RMSE average = " + Math.mean(results[0]));
            System.out.println("100-fold bootstrap RMSE std.dev = " + Math.sd(results[0]));
            System.out.println("100-fold bootstrap AbsoluteDeviation average = " + Math.mean(results[1]));
            System.out.println("100-fold bootstrap AbsoluteDeviation std.dev = " + Math.sd(results[1]));
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }
}
