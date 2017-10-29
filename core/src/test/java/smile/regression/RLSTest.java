/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 * Modifications copyright (C) 2017 Sam Erickson
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
import smile.data.parser.ArffParser;
import smile.validation.CrossValidation;

/**
 *
 * @author Sam Erickson
 */
public class RLSTest {
    
    
    public RLSTest() {
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
    public void testRLS(String name, String fileName, int responseIndex){
        System.out.println(name+"\t Learn ");
        ArffParser parser = new ArffParser();
        parser.setResponseIndex(responseIndex);
        try {
            AttributeDataset data = parser.parse(smile.data.parser.IOUtils.getTestDataFile(fileName));

            double[][] datax = data.toArray(new double[data.size()][]);
            double[] datay = data.toArray(new double[data.size()]);

            int n = datax.length;
            int k = 10;
            int trainOnlineSplit=5;

            CrossValidation cv = new CrossValidation(n, k);
            double rss = 0.0;
            for (int i = 0; i < k; i++) {
                double[][] trainx = smile.math.Math.slice(datax, cv.train[i]);
                double[] trainy = smile.math.Math.slice(datay, cv.train[i]);
                double[][] testx = smile.math.Math.slice(datax, cv.test[i]);
                double[] testy = smile.math.Math.slice(datay, cv.test[i]);

                RLS rls = new RLS(trainx[0].length);
                rls.learn(trainx, trainy);

                for (int j = 0; j < testx.length; j++) {
                    double r = testy[j] - rls.predict(testx[j]);
                    rss += r * r;
                }
                
            }
            System.out.println("MSE = " + rss / n);
        } catch (Exception ex) {
             System.err.println(ex);
        }
    }
    
    /**
     * Test of online learn method of class OLS.
     */
    public void testOnlineLearn(boolean svd, String name, String fileName, int responseIndex){
        System.out.println(name+"\t Online Learn "+(svd?" SVD":" QR"));
        ArffParser parser = new ArffParser();
        parser.setResponseIndex(responseIndex);
        try {
            AttributeDataset data = parser.parse(smile.data.parser.IOUtils.getTestDataFile(fileName));

            double[][] datax = data.toArray(new double[data.size()][]);
            double[] datay = data.toArray(new double[data.size()]);

            int n = datax.length;
            int k = 10;
            int trainOnlineSplit=2;

            CrossValidation cv = new CrossValidation(n, k);
            double rss = 0.0;
            for (int i = 0; i < k; i++) {
                double[][] trainx = smile.math.Math.slice(datax, cv.train[i]);
                double[] trainy = smile.math.Math.slice(datay, cv.train[i]);
                double[][] testx = smile.math.Math.slice(datax, cv.test[i]);
                double[] testy = smile.math.Math.slice(datay, cv.test[i]);
                
                // Split the training data to instances learned from least square solutions
                // and instances for online learning
                CrossValidation splitTrainOnlineLearn = new CrossValidation(trainx.length,trainOnlineSplit);
                double[][] constructorTrainx = smile.math.Math.slice(trainx, splitTrainOnlineLearn.train[0]);
                double[] constructorTrainy = smile.math.Math.slice(trainy, splitTrainOnlineLearn.train[0]);
                double[][] onlineTrainX = smile.math.Math.slice(trainx, splitTrainOnlineLearn.test[0]);
                double[] onlineTrainY = smile.math.Math.slice(trainy, splitTrainOnlineLearn.test[0]);

                RLS rls = new RLS(constructorTrainx, constructorTrainy, svd, 1);
                rls.learn(onlineTrainX,onlineTrainY);

                for (int j = 0; j < testx.length; j++) {
                    double r = testy[j] - rls.predict(testx[j]);
                    rss += r * r;
                }
                
            }
            System.out.println("MSE = " + rss / n);
        } catch (Exception ex) {
             System.err.println(ex);
        }
    }
    
    /**
     * Test learn Method
     */
    @Test
    public void testLearn() {
        testRLS("CPU", "weka/cpu.arff", 6);
        testRLS("2dplanes", "weka/regression/2dplanes.arff", 10);
        testRLS("abalone", "weka/regression/abalone.arff", 8);
        //testRLS("bank32nh", "weka/regression/bank32nh.arff", 32);
        //testRLS("cal_housing", "weka/regression/cal_housing.arff", 8);
        //testRLS("puma8nh", "weka/regression/puma8nh.arff", 8);
        //testRLS("kin8nm", "weka/regression/kin8nm.arff", 8);
    }
    
    /**
     * Test SVD Online Method
     */
    @Test
    public void testSVDOnlineMethod() {
        testOnlineLearn(true, "CPU", "weka/cpu.arff", 6);
        testOnlineLearn(true, "2dplanes", "weka/regression/2dplanes.arff", 10);
        testOnlineLearn(true, "abalone", "weka/regression/abalone.arff", 8);
        //testOnlineLearn(true, "bank32nh", "weka/regression/bank32nh.arff", 32);
        //testOnlineLearn(true, "cal_housing", "weka/regression/cal_housing.arff", 8);
        //testOnlineLearn(true, "puma8nh", "weka/regression/puma8nh.arff", 8);
        //testOnlineLearn(true, "kin8nm", "weka/regression/kin8nm.arff", 8);
    }
    
    /**
     * Test QR Online Method
     */
    @Test
    public void testQROnlineMethod() {
        testOnlineLearn(false, "CPU", "weka/cpu.arff", 6);
        testOnlineLearn(false, "2dplanes", "weka/regression/2dplanes.arff", 10);
        testOnlineLearn(false, "abalone", "weka/regression/abalone.arff", 8);
        //testOnlineLearn(true, "bank32nh", "weka/regression/bank32nh.arff", 32);
        //testOnlineLearn(false, "cal_housing", "weka/regression/cal_housing.arff", 8);
        //testOnlineLearn(false, "puma8nh", "weka/regression/puma8nh.arff", 8);
        //testOnlineLearn(false, "kin8nm", "weka/regression/kin8nm.arff", 8);
    }
}
