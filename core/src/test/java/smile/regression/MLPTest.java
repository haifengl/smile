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
import smile.base.mlp.Layer;
import smile.base.mlp.LayerBuilder;
import smile.data.*;
import smile.feature.Standardizer;
import smile.validation.CrossValidation;
import smile.math.MathEx;
import smile.validation.RMSE;
import static org.junit.Assert.assertEquals;

/**
 * 
 * @author Haifeng Li
 */
public class MLPTest {
    public MLPTest(){
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

    @Test(expected = Test.None.class)
    public void testLongley() throws Exception {
        System.out.println("longley");

        int p = Longley.x[0].length;
        MLP model = new MLP(p, Layer.rectifier(30), Layer.sigmoid(30));
        // small learning rate and weight decay to counter exploding gradient
        model.setLearningRate(0.01);
        model.setWeightDecay(0.1);

        for (int epoch = 0; epoch < 5; epoch++) {
            int[] permutation = MathEx.permutate(Longley.x.length);
            for (int i : permutation) {
                model.update(Longley.x[i], Longley.y[i]);
            }
        }

        java.nio.file.Path temp = smile.data.Serialize.write(model);
        smile.data.Serialize.read(temp);
    }

    public void test(String dataset, double[][] x, double[] y, double expected, LayerBuilder... builders) {
        System.out.println(dataset);

        MathEx.setSeed(19650218); // to get repeatable results.

        Standardizer scaler = Standardizer.fit(x);
        x = scaler.transform(x);
        int p = x[0].length;

        double[] prediction = CrossValidation.regression(10, x, y, (xi, yi) -> {
            MLP model = new MLP(p, builders);
            // small learning rate and weight decay to counter exploding gradient
            model.setLearningRate(0.01);
            model.setWeightDecay(0.1);

            for (int epoch = 0; epoch < 5; epoch++) {
                int[] permutation = MathEx.permutate(xi.length);
                for (int i : permutation) {
                    model.update(xi[i], yi[i]);
                }
            }

            return model;
        });
        double rmse = RMSE.of(y, prediction);

        System.out.format("10-CV RMSE = %.4f%n", rmse);
        assertEquals(expected, rmse, 1E-4);
    }

    @Test
    public void testReLUSigmoid() {
        test("CPU", CPU.x, CPU.y, 152.2285, Layer.rectifier(30), Layer.sigmoid(30));
        test("2dplanes", Planes.x, Planes.y, 4.4432, Layer.rectifier(50), Layer.sigmoid(30));
        test("abalone", Abalone.x, Abalone.y, 3.3641, Layer.rectifier(40), Layer.sigmoid(30));
        test("ailerons", Ailerons.x, Ailerons.y, 0.0004, Layer.rectifier(80), Layer.sigmoid(30));
        test("bank32nh", Bank32nh.x, Bank32nh.y, 0.1220, Layer.rectifier(65), Layer.sigmoid(30));
        test("cal_housing", CalHousing.x, CalHousing.y, 118046.5629, Layer.rectifier(40), Layer.sigmoid(30));
        test("puma8nh", Puma8NH.x, Puma8NH.y, 5.6834, Layer.rectifier(40), Layer.sigmoid(30));
        test("kin8nm", Kin8nm.x, Kin8nm.y, 0.2640, Layer.rectifier(40), Layer.sigmoid(30));
    }
}
