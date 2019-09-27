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
import smile.base.neuralnetwork.Layer;
import smile.data.Abalone;
import smile.data.CalHousing;
import smile.data.CPU;
import smile.data.Kin8nm;
import smile.validation.CrossValidation;
import smile.math.MathEx;
import smile.base.neuralnetwork.ActivationFunction;

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
    public void test(ActivationFunction activation, String dataset, double[][] x, double[] y) {
        System.out.println(dataset + "\t" + activation);

        int n = x.length;
        int p = x[0].length;
        double[] mux = MathEx.colMeans(x);
        double[] sdx = MathEx.colSds(x);
        double muy = MathEx.mean(y);
        double sdy = MathEx.sd(y);

        double[][] datax = new double[n][p];
        double[] datay = new double[n];
        for (int i = 0; i < n; i++) {
            datay[i]=(y[i]-muy)/sdy;
            for (int j = 0; j < p; j++) {
                datax[i][j] = (x[i][j] - mux[j]) / sdx[j];
            }
        }
            
        double rss = CrossValidation.test(10, datax, datay, (xi, yi) -> {
            NeuralNetwork neuralNetwork = new NeuralNetwork(
                    new Layer(activation, p, 10),
                    new Layer(ActivationFunction.LINEAR, 10, 1));
            neuralNetwork.update(xi, yi);
            return neuralNetwork;
        });

        System.out.format("10-CV MSE = %.4f%n", rss/n);
    }

    /**
     * Test of learn method, of class NeuralNetwork.
     */
    @Test
    public void testLogisticSigmoid() {
        test(ActivationFunction.LOGISTIC_SIGMOID, "CPU", CPU.x, CPU.y);
        //test(NeuralNetwork.ActivationFunction.LOGISTIC_SIGMOID, "2dplanes", "weka/regression/2dplanes.arff", 6);
        test(ActivationFunction.LOGISTIC_SIGMOID, "abalone", Abalone.x, Abalone.y);
        //test(NeuralNetwork.ActivationFunction.LOGISTIC_SIGMOID, "ailerons", "weka/regression/ailerons.arff", 40);
        //test(NeuralNetwork.ActivationFunction.LOGISTIC_SIGMOID, "bank32nh", "weka/regression/bank32nh.arff", 32);
        test(ActivationFunction.LOGISTIC_SIGMOID, "cal_housing", CalHousing.x, CalHousing.y);
        //test(NeuralNetworkRegressor.ActivationFunction.LOGISTIC_SIGMOID, "puma8nh", "weka/regression/puma8nh.arff", 8);
        test(ActivationFunction.LOGISTIC_SIGMOID, "kin8nm", Kin8nm.x, Kin8nm.y);
    }
    
    /**
     * Test of learn method, of class NeuralNetwork.
     */
    @Test
    public void testTanh() {
        test(ActivationFunction.HYPERBOLIC_TANGENT, "CPU", CPU.x, CPU.y);
        //test(NeuralNetworkRegressor.ActivationFunction.TANH, "2dplanes", "weka/regression/2dplanes.arff", 6);
        test(ActivationFunction.HYPERBOLIC_TANGENT, "abalone", Abalone.x, Abalone.y);
        //test(NeuralNetworkRegressor.ActivationFunction.TANH, "ailerons", "weka/regression/ailerons.arff", 40);
        //test(NeuralNetworkRegressor.ActivationFunction.TANH, "bank32nh", "weka/regression/bank32nh.arff", 32);
        test(ActivationFunction.HYPERBOLIC_TANGENT, "cal_housing", CalHousing.x, CalHousing.y);
        //test(NeuralNetworkRegressor.ActivationFunction.TANH, "puma8nh", "weka/regression/puma8nh.arff", 8);
        test(ActivationFunction.HYPERBOLIC_TANGENT, "kin8nm", Kin8nm.x, Kin8nm.y);
    }
}
