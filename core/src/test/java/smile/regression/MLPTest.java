/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.regression;

import smile.base.mlp.Layer;
import smile.base.mlp.LayerBuilder;
import smile.data.DataFrame;
import smile.data.transform.InvertibleColumnTransform;
import smile.datasets.*;
import smile.feature.transform.Standardizer;
import smile.io.Read;
import smile.io.Write;
import smile.math.MathEx;
import smile.math.Scaler;
import smile.util.function.TimeFunction;
import smile.validation.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 
 * @author Haifeng Li
 */
public class MLPTest {
    public MLPTest(){
    }
    @BeforeAll
    public static void setUpClass() throws Exception {
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
    }
    
    @BeforeEach
    public void setUp() {
    }
    
    @AfterEach
    public void tearDown() {
    }

    @Test
    public void testLongley() throws Exception {
        System.out.println("longley");
        var longley = new Longley();
        double[][] x = longley.x();
        double[] y = longley.y();
        int p = x[0].length;
        MLP model = new MLP(Layer.input(p), Layer.rectifier(30), Layer.sigmoid(30));
        // small learning rate and weight decay to counter exploding gradient
        model.setLearningRate(TimeFunction.constant(0.01));
        model.setWeightDecay(0.1);

        for (int epoch = 0; epoch < 5; epoch++) {
            int[] permutation = MathEx.permutate(x.length);
            for (int i : permutation) {
                model.update(x[i], y[i]);
            }
        }

        java.nio.file.Path temp = Write.object(model);
        Read.object(temp);
    }

    public void test(String dataset, double[][] x, double[] y, Scaler scaler, double expected, LayerBuilder... builders) {
        System.out.println(dataset);

        MathEx.setSeed(19650218); // to get repeatable results.

        DataFrame data = DataFrame.of(x);
        InvertibleColumnTransform standardizer = Standardizer.fit(data);
        System.out.println(standardizer);
        x = standardizer.apply(data).toArray();

        RegressionValidations<MLP> result = CrossValidation.regression(10, x, y, (xi, yi) -> {
            MLP model = new MLP(scaler, builders);
            // small learning rate and weight decay to counter exploding gradient
            model.setLearningRate(TimeFunction.linear(0.01, 10000, 0.001));
            model.setWeightDecay(0.1);

            for (int epoch = 0; epoch < 5; epoch++) {
                int[] permutation = MathEx.permutate(xi.length);
                for (int i : permutation) {
                    model.update(xi[i], yi[i]);
                }
            }

            return model;
        });

        System.out.println(result);
        assertEquals(expected, result.avg().rmse(), 1);
    }

    @Test
    public void testCPU() throws Exception {
        var cpu = new CPU();
        double[][] x = cpu.x();
        double[] y = cpu.y();
        test("CPU", x, y, Scaler.standardizer(y, true), 65.4472,
                Layer.input(x[0].length), Layer.rectifier(30), Layer.sigmoid(30));
    }

    @Test
    public void test2DPlanes() throws Exception {
        var planes = new Planes2D();
        double[][] x = planes.x();
        double[] y = planes.y();
        test("2dplanes", x, y, null, 1.5174,
                Layer.input(x[0].length), Layer.rectifier(50), Layer.sigmoid(30));
    }

    @Test
    public void testAbalone() throws Exception {
        var abalone = new Abalone();
        var x = abalone.x();
        var y = abalone.y();
        test("abalone", x, y, null, 2.5298,
             Layer.input(x[0].length), Layer.rectifier(40), Layer.sigmoid(30));
    }

    @Test
    public void testAilerons() throws Exception {
        var ailerons = new Ailerons();
        double[][] x = ailerons.x();
        double[] y = ailerons.y();
        test("ailerons", x, y, null, 0.0004,
             Layer.input(x[0].length), Layer.rectifier(80), Layer.sigmoid(30));
    }

    @Test
    public void testBank32nh() throws Exception {
        var bank32nh = new Bank32nh();
        double[][] x = bank32nh.x();
        double[] y = bank32nh.y();
        test("bank32nh", x, y, null, 0.1218,
                Layer.input(x[0].length), Layer.rectifier(65), Layer.sigmoid(30));
    }

    @Test
    public void testCalHousing() throws Exception {
        var calHousing = new CalHousing();
        double[][] x = calHousing.x();
        double[] y = calHousing.y();
        test("cal_housing", x, y, null, 115704.4419,
                Layer.input(x[0].length), Layer.rectifier(40), Layer.sigmoid(30));
    }

    @Test
    public void testPuma8nh() throws Exception {
        var puma = new Puma8NH();
        double[][] x = puma.x();
        double[] y = puma.y();
        test("puma8nh", x, y, null, 3.9605,
                Layer.input(x[0].length), Layer.rectifier(40), Layer.sigmoid(30));
    }

    @Test
    public void testKin8nm() throws Exception {
        var kin8nm = new Kin8nm();
        double[][] x = kin8nm.x();
        double[] y = kin8nm.y();
        test("kin8nm", x, y, null, 0.2638,
                Layer.input(x[0].length), Layer.rectifier(40), Layer.sigmoid(30));
    }
}
