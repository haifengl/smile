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
import smile.data.AttributeDataset;
import smile.data.parser.ArffParser;
import smile.validation.CrossValidation;
import smile.math.MathEx;

/**
 * 
 * @author Sam Erickson
 */
public class NeuralNetworkTest {
    public NeuralNetworkTest(){
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
    public void test(NeuralNetwork.ActivationFunction activation, String dataset, String url, int response) {
        System.out.println(dataset + "\t" + activation);
        ArffParser parser = new ArffParser();
        parser.setResponseIndex(response);
        try {
            AttributeDataset data = parser.parse(smile.util.Paths.getTestData(url));
            double[] datay = data.toArray(new double[data.size()]);
            double[][] datax = data.toArray(new double[data.size()][]);
            
            int n = datax.length;
            int p = datax[0].length;
            double[] mux = MathEx.colMeans(datax);
            double[] sdx = MathEx.colSds(datax);
            double muy = MathEx.mean(datay);
            double sdy = MathEx.sd(datay);
            for (int i = 0; i < n; i++) {
                datay[i]=(datay[i]-muy)/sdy;
                for (int j = 0; j < p; j++) {
                    datax[i][j] = (datax[i][j] - mux[j]) / sdx[j];
                }
            }
            
            int k = 10;

            CrossValidation cv = new CrossValidation(n, k);
            double rss = 0.0;
            double ad = 0.0;
            for (int i = 0; i < k; i++) {
                double[][] trainx = MathEx.slice(datax, cv.train[i]);
                double[] trainy = MathEx.slice(datay, cv.train[i]);
                double[][] testx = MathEx.slice(datax, cv.test[i]);
                double[] testy = MathEx.slice(datay, cv.test[i]);

                NeuralNetwork neuralNetwork = new NeuralNetwork(activation,new int[]{datax[0].length,10,10,1});
                neuralNetwork.learn(trainx,trainy);

                for (int j = 0; j < testx.length; j++) {
                    double r = testy[j] - neuralNetwork.predict(testx[j]);
                    rss += r * r;
                }
            }

            System.out.format("10-CV MSE = %.4f%n", rss/n);
         } catch (Exception ex) {
             System.err.println(ex);
         }
    }
    /**
     * Test of learn method, of class NeuralNetwork.
     */
    @Test
    public void testLogisticSigmoid() {
        test(NeuralNetwork.ActivationFunction.LOGISTIC_SIGMOID, "CPU", "weka/cpu.arff", 6);
        //test(NeuralNetwork.ActivationFunction.LOGISTIC_SIGMOID, "2dplanes", "weka/regression/2dplanes.arff", 6);
        test(NeuralNetwork.ActivationFunction.LOGISTIC_SIGMOID, "abalone", "weka/regression/abalone.arff", 8);
        //test(NeuralNetwork.ActivationFunction.LOGISTIC_SIGMOID, "ailerons", "weka/regression/ailerons.arff", 40);
        //test(NeuralNetwork.ActivationFunction.LOGISTIC_SIGMOID, "bank32nh", "weka/regression/bank32nh.arff", 32);
        test(NeuralNetwork.ActivationFunction.LOGISTIC_SIGMOID, "cal_housing", "weka/regression/cal_housing.arff", 8);
        //test(NeuralNetworkRegressor.ActivationFunction.LOGISTIC_SIGMOID, "puma8nh", "weka/regression/puma8nh.arff", 8);
        test(NeuralNetwork.ActivationFunction.LOGISTIC_SIGMOID, "kin8nm", "weka/regression/kin8nm.arff", 8);
    }
    
    /**
     * Test of learn method, of class NeuralNetwork.
     */
    @Test
    public void testTanh() {
        test(NeuralNetwork.ActivationFunction.TANH, "CPU", "weka/cpu.arff", 6);
        //test(NeuralNetworkRegressor.ActivationFunction.TANH, "2dplanes", "weka/regression/2dplanes.arff", 6);
        test(NeuralNetwork.ActivationFunction.TANH, "abalone", "weka/regression/abalone.arff", 8);
        //test(NeuralNetworkRegressor.ActivationFunction.TANH, "ailerons", "weka/regression/ailerons.arff", 40);
        //test(NeuralNetworkRegressor.ActivationFunction.TANH, "bank32nh", "weka/regression/bank32nh.arff", 32);
        test(NeuralNetwork.ActivationFunction.TANH, "cal_housing", "weka/regression/cal_housing.arff", 8);
        //test(NeuralNetworkRegressor.ActivationFunction.TANH, "puma8nh", "weka/regression/puma8nh.arff", 8);
        test(NeuralNetwork.ActivationFunction.TANH, "kin8nm", "weka/regression/kin8nm.arff", 8);
    }
}
