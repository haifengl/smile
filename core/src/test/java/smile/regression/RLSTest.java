/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/

package smile.regression;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.math.MathEx;
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
            AttributeDataset data = parser.parse(smile.util.Paths.getTestData(fileName));

            double[][] datax = data.toArray(new double[data.size()][]);
            double[] datay = data.toArray(new double[data.size()]);

            int n = datax.length;
            int k = 10;

            CrossValidation cv = new CrossValidation(n, k);
            double rss = 0.0;
            for (int i = 0; i < k; i++) {
                double[][] trainx = MathEx.slice(datax, cv.train[i]);
                double[] trainy = MathEx.slice(datay, cv.train[i]);
                double[][] testx = MathEx.slice(datax, cv.test[i]);
                double[] testy = MathEx.slice(datay, cv.test[i]);

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
