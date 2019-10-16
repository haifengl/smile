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
import smile.base.mlp.HiddenLayer;
import smile.base.mlp.HiddenLayerBuilder;
import smile.base.mlp.Layer;
import smile.data.*;
import smile.data.formula.Formula;
import smile.feature.Standardizer;
import smile.validation.CrossValidation;
import smile.math.MathEx;
import smile.base.mlp.ActivationFunction;
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
    public void test(String dataset, double[][] x, double[] y, double expected, HiddenLayerBuilder... builders) {
        System.out.println(dataset);

        MathEx.setSeed(19650218); // to get repeatable results.

        Standardizer scaler = Standardizer.fit(x);
        x = scaler.transform(x);
        int p = x[0].length;

        double[] prediction = CrossValidation.regression(10, x, y, (xi, yi) -> {
            NeuralNetwork model = new NeuralNetwork(p, builders);
            // regularize the weight to counter large gradient
            model.setWeightDecay(0.1);

            int b = 5; // small batch to avoid exploding gradient
            double[][] batchx = new double[b][];
            double[] batchy = new double[b];
            for (int e = 0; e < 30; e++) {
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
    public void testSigmoid() {
        System.out.println("----- sigmoid -----");
        test("CPU", CPU.x, CPU.y, 0.7644, HiddenLayer.rectifier(50), HiddenLayer.sigmoid(30));
        test("2dplanes", Planes.x, Planes.y, 0.7092, HiddenLayer.sigmoid(30));
        test("abalone", Abalone.x, Abalone.y, 0.8441, HiddenLayer.sigmoid(30));
        test("ailerons", Ailerons.x, Ailerons.y, 0.6631, HiddenLayer.sigmoid(30));
        test("bank32nh", Bank32nh.x, Bank32nh.y, 1.0924, HiddenLayer.sigmoid(30));
        test("cal_housing", CalHousing.x, CalHousing.y, 0.9747, HiddenLayer.sigmoid(30));
        test("puma8nh", Puma8NH.x, Puma8NH.y, 0.9093, HiddenLayer.sigmoid(30));
        test("kin8nm", Kin8nm.x, Kin8nm.y, 1.0135, HiddenLayer.sigmoid(30));
    }
    
    @Test
    public void testTanh() {
        System.out.println("----- sigmoid -----");
        test("CPU", CPU.x, CPU.y, 0.7644, HiddenLayer.tanh(30));
        test("2dplanes", Planes.x, Planes.y, 0.7092, HiddenLayer.tanh(30));
        test("abalone", Abalone.x, Abalone.y, 0.8441, HiddenLayer.tanh(30));
        test("ailerons", Ailerons.x, Ailerons.y, 0.6631, HiddenLayer.tanh(30));
        test("bank32nh", Bank32nh.x, Bank32nh.y, 1.0924, HiddenLayer.tanh(30));
        test("cal_housing", CalHousing.x, CalHousing.y, 0.9747, HiddenLayer.tanh(30));
        test("puma8nh", Puma8NH.x, Puma8NH.y, 0.9093, HiddenLayer.tanh(30));
        test("kin8nm", Kin8nm.x, Kin8nm.y, 1.0135, HiddenLayer.tanh(30));
    }

    @Test
    public void testReLU() {
        System.out.println("----- sigmoid -----");
        test("CPU", CPU.x, CPU.y, 0.7644, HiddenLayer.rectifier(30));
        test("2dplanes", Planes.x, Planes.y, 0.7092, HiddenLayer.rectifier(30));
        test("abalone", Abalone.x, Abalone.y, 0.8441, HiddenLayer.rectifier(30));
        test("ailerons", Ailerons.x, Ailerons.y, 0.6631, HiddenLayer.rectifier(30));
        test("bank32nh", Bank32nh.x, Bank32nh.y, 1.0924, HiddenLayer.rectifier(30));
        test("cal_housing", CalHousing.x, CalHousing.y, 0.9747, HiddenLayer.rectifier(30));
        test("puma8nh", Puma8NH.x, Puma8NH.y, 0.9093, HiddenLayer.rectifier(30));
        test("kin8nm", Kin8nm.x, Kin8nm.y, 1.0135, HiddenLayer.rectifier(30));
    }
}
