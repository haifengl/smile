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

    /**
     * Test of online learn method of class OLS.
     */
    public void testOnlineLearn(String name, String fileName, int responseIndex){
        System.out.println(name+"\t Online Learn");
        ArffParser parser = new ArffParser();
        parser.setResponseIndex(responseIndex);
        try {
            AttributeDataset data = parser.parse(smile.data.parser.IOUtils.getTestDataFile(fileName));

            double[][] datax = data.toArray(new double[data.size()][]);
            double[] datay = data.toArray(new double[data.size()]);

            int n = datax.length;
            int k = 10;

            CrossValidation cv = new CrossValidation(n, k);
            double rss = 0.0;
            for (int i = 0; i < k; i++) {
                double[][] trainx = smile.math.Math.slice(datax, cv.train[i]);
                double[] trainy = smile.math.Math.slice(datay, cv.train[i]);
                double[][] testx = smile.math.Math.slice(datax, cv.test[i]);
                double[] testy = smile.math.Math.slice(datay, cv.test[i]);

                int l = trainx.length / 2;
                double[][] batchx = new double[l][];
                double[]   batchy = new double[l];
                double[][] onlinex = new double[l][];
                double[]   onliney = new double[l];
                for (int j = 0; j < l; j++) {
                    batchx[j] = trainx[j];
                    batchy[j] = trainy[j];
                    onlinex[j] = trainx[l+j];
                    onliney[j] = trainy[l+j];
                }
                RLS rls = new RLS(batchx, batchy, 1);
                rls.learn(onlinex, onliney);

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
    
    @Test
    public void testOnlineLearn() {
        testOnlineLearn("CPU", "weka/cpu.arff", 6);
        testOnlineLearn("2dplanes", "weka/regression/2dplanes.arff", 10);
        testOnlineLearn("abalone", "weka/regression/abalone.arff", 8);
        //testOnlineLearn(true, "bank32nh", "weka/regression/bank32nh.arff", 32);
        //testOnlineLearn(true, "cal_housing", "weka/regression/cal_housing.arff", 8);
        //testOnlineLearn(true, "puma8nh", "weka/regression/puma8nh.arff", 8);
        //testOnlineLearn(true, "kin8nm", "weka/regression/kin8nm.arff", 8);
    }
}
