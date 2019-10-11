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
import smile.data.formula.Formula;
import smile.feature.Standardizer;
import smile.validation.CrossValidation;
import smile.math.MathEx;
import smile.base.neuralnetwork.ActivationFunction;
import smile.validation.RMSE;
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
    public void test(ActivationFunction activation, String dataset, Formula formula, DataFrame data, double expected) {
        System.out.println(dataset + "\t" + activation);

        MathEx.setSeed(19650218); // to get repeatable results.

        Standardizer scaler = Standardizer.fit(data);
        DataFrame df = scaler.transform(data);
        double[][] x = formula.x(df).toArray();
        double[] y = formula.y(df).toDoubleArray();
        int p = x[0].length;

        double[] prediction = CrossValidation.regression(10, x, y, (xi, yi) -> {
            NeuralNetwork model = new NeuralNetwork(
                    new Layer(activation, 10, p),
                    new Layer(ActivationFunction.LINEAR, 1, 10));

            int b = 50;
            double[][] batchx = new double[b][];
            double[] batchy = new double[b];
            for (int e = 0; e < 5; e++) {
                int i = 0;
                for (; i < xi.length-b; i+=b) {
                    System.arraycopy(xi, i, batchx, 0, b);
                    System.arraycopy(yi, i, batchy, 0, b);
                    model.update(batchx, batchy);
                }

                for (; i < xi.length; i++) {
                    model.update(xi[i], yi[i]);
                }
            }
            return model;
        });
        double rmse = RMSE.apply(y, prediction);

        System.out.format("10-CV RMSE = %.4f%n", rmse);
        //assertEquals(expected, rmse, 1E-4);
    }

    @Test
    public void testLogisticSigmoid() {
        test(ActivationFunction.LOGISTIC_SIGMOID, "CPU", CPU.formula, CPU.data, 1.2137);
        test(ActivationFunction.LOGISTIC_SIGMOID, "2dplanes", Planes.formula, Planes.data, 1.1856);
        test(ActivationFunction.LOGISTIC_SIGMOID, "abalone", Abalone.formula, Abalone.train, 1.0551);
        test(ActivationFunction.LOGISTIC_SIGMOID, "ailerons", Ailerons.formula, Ailerons.data, 1.1503);
        test(ActivationFunction.LOGISTIC_SIGMOID, "bank32nh", Bank32nh.formula, Bank32nh.data, 1.1922);
        test(ActivationFunction.LOGISTIC_SIGMOID, "cal_housing", CalHousing.formula, CalHousing.data, 1.0678);
        test(ActivationFunction.LOGISTIC_SIGMOID, "puma8nh", Puma8NH.formula, Puma8NH.data, 1.0647);
        test(ActivationFunction.LOGISTIC_SIGMOID, "kin8nm", Kin8nm.formula, Kin8nm.data, 1.0640);
    }
    
    @Test
    public void testTanh() {
        test(ActivationFunction.HYPERBOLIC_TANGENT, "CPU", CPU.formula, CPU.data, 1.2260);
        test(ActivationFunction.HYPERBOLIC_TANGENT, "2dplanes", Planes.formula, Planes.data, 1.1856);
        test(ActivationFunction.HYPERBOLIC_TANGENT, "abalone", Abalone.formula, Abalone.train, 1.0551);
        test(ActivationFunction.HYPERBOLIC_TANGENT, "ailerons", Ailerons.formula, Ailerons.data, 1.1503);
        test(ActivationFunction.HYPERBOLIC_TANGENT, "bank32nh", Bank32nh.formula, Bank32nh.data, 1.1922);
        test(ActivationFunction.HYPERBOLIC_TANGENT, "cal_housing", CalHousing.formula, CalHousing.data, 1.0678);
        test(ActivationFunction.HYPERBOLIC_TANGENT, "puma8nh", Puma8NH.formula, Puma8NH.data, 1.0647);
        test(ActivationFunction.HYPERBOLIC_TANGENT, "kin8nm", Kin8nm.formula, Kin8nm.data, 1.0640);
    }

    @Test
    public void testReLU() {
        test(ActivationFunction.RECTIFIER, "CPU", CPU.formula, CPU.data, 1.2198);
        test(ActivationFunction.RECTIFIER, "2dplanes", Planes.formula, Planes.data, 1.1856);
        test(ActivationFunction.RECTIFIER, "abalone", Abalone.formula, Abalone.train, 1.0551);
        test(ActivationFunction.RECTIFIER, "ailerons", Ailerons.formula, Ailerons.data, 1.1503);
        test(ActivationFunction.RECTIFIER, "bank32nh", Bank32nh.formula, Bank32nh.data, 1.1922);
        test(ActivationFunction.RECTIFIER, "cal_housing", CalHousing.formula, CalHousing.data, 1.0678);
        test(ActivationFunction.RECTIFIER, "puma8nh", Puma8NH.formula, Puma8NH.data, 1.0647);
        test(ActivationFunction.RECTIFIER, "kin8nm", Kin8nm.formula, Kin8nm.data, 1.0640);
    }
}
