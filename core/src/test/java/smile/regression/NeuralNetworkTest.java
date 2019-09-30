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
import smile.data.*;
import smile.validation.CrossValidation;
import smile.math.MathEx;
import smile.base.neuralnetwork.ActivationFunction;

import static org.junit.Assert.assertEquals;

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
    public void test(ActivationFunction activation, String dataset, double[][] x, double[] y, double expected) {
        System.out.println(dataset + "\t" + activation);

        // to get repeatable results.
        MathEx.setSeed(19650218);
        int n = x.length;
        int p = x[0].length;
        double[] xmu = MathEx.colMeans(x);
        double[] xsd = MathEx.colSds(x);
        double ym = MathEx.mean(y);
        double ysd = MathEx.sd(y);

        double[][] datax = new double[n][p];
        double[] datay = new double[n];
        for (int i = 0; i < n; i++) {
            datay[i]=(y[i]-ym)/ysd;
            for (int j = 0; j < p; j++) {
                datax[i][j] = (x[i][j] - xmu[j]) / xsd[j];
            }
        }
            
        double rmse = CrossValidation.test(10, datax, datay, (xi, yi) -> {
            NeuralNetwork neuralNetwork = new NeuralNetwork(
                    new Layer(activation, 10, p),
                    new Layer(ActivationFunction.LINEAR, 1, 10));
            neuralNetwork.update(xi, yi);
            return neuralNetwork;
        });

        System.out.format("10-CV RMSE = %.4f%n", rmse);
        assertEquals(expected, rmse, 1E-4);
    }

    @Test
    public void testLogisticSigmoid() {
        test(ActivationFunction.LOGISTIC_SIGMOID, "CPU", CPU.x, CPU.y, 1.4323490904770806);
        test(ActivationFunction.LOGISTIC_SIGMOID, "2dplanes", Planes.x, Planes.y, 1.3170049315123504);
        test(ActivationFunction.LOGISTIC_SIGMOID, "abalone", Abalone.x, Abalone.y, 1.4714925348689385);
        test(ActivationFunction.LOGISTIC_SIGMOID, "ailerons", Ailerons.x, Ailerons.y, 1.5283443385411195);
        test(ActivationFunction.LOGISTIC_SIGMOID, "bank32nh", Bank32nh.x, Bank32nh.y, 1.4475694135601782);
        test(ActivationFunction.LOGISTIC_SIGMOID, "cal_housing", CalHousing.x, CalHousing.y, 1.531987977990714);
        test(ActivationFunction.LOGISTIC_SIGMOID, "puma8nh", Puma8NH.x, Puma8NH.y, 1.5028720339848398);
        test(ActivationFunction.LOGISTIC_SIGMOID, "kin8nm", Kin8nm.x, Kin8nm.y, 1.5349664439904975);
    }
    
    @Test
    public void testTanh() {
        test(ActivationFunction.HYPERBOLIC_TANGENT, "CPU", CPU.x, CPU.y, 1.5135952087582767);
        test(ActivationFunction.HYPERBOLIC_TANGENT, "2dplanes", Planes.x, Planes.y, 1.3655539514366672);
        test(ActivationFunction.HYPERBOLIC_TANGENT, "abalone", Abalone.x, Abalone.y, 1.145693078341211);
        test(ActivationFunction.HYPERBOLIC_TANGENT, "ailerons", Ailerons.x, Ailerons.y, 1.4062914108043678);
        test(ActivationFunction.HYPERBOLIC_TANGENT, "bank32nh", Bank32nh.x, Bank32nh.y, 1.4793812146158358);
        test(ActivationFunction.HYPERBOLIC_TANGENT, "cal_housing", CalHousing.x, CalHousing.y, 1.2829651044027872);
        test(ActivationFunction.HYPERBOLIC_TANGENT, "puma8nh", Puma8NH.x, Puma8NH.y, 1.2031297146595983);
        test(ActivationFunction.HYPERBOLIC_TANGENT, "kin8nm", Kin8nm.x, Kin8nm.y, 1.3674644013638797);
    }

    @Test
    public void testReLU() {
        test(ActivationFunction.RECTIFIER, "CPU", CPU.x, CPU.y, 1.409162150467836);
        test(ActivationFunction.RECTIFIER, "2dplanes", Planes.x, Planes.y, 1.2636614267361297);
        test(ActivationFunction.RECTIFIER, "abalone", Abalone.x, Abalone.y, 1.1488262477349933);
        test(ActivationFunction.RECTIFIER, "ailerons", Ailerons.x, Ailerons.y, 1.7114108817801885);
        test(ActivationFunction.RECTIFIER, "bank32nh", Bank32nh.x, Bank32nh.y, 1.536662808132295);
        test(ActivationFunction.RECTIFIER, "cal_housing", CalHousing.x, CalHousing.y, 1.310312244424206);
        test(ActivationFunction.RECTIFIER, "puma8nh", Puma8NH.x, Puma8NH.y, 1.1927815842139216);
        test(ActivationFunction.RECTIFIER, "kin8nm", Kin8nm.x, Kin8nm.y, 1.310226535254051);
    }
}
