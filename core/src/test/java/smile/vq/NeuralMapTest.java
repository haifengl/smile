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

package smile.vq;

import org.apache.commons.csv.CSVFormat;
import smile.data.DataFrame;
import smile.data.USPS;
import smile.data.formula.Formula;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.io.CSV;
import smile.math.MathEx;
import smile.util.Paths;
import smile.validation.RandIndex;
import smile.validation.AdjustedRandIndex;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

/**
 *
 * @author Haifeng
 */
public class NeuralMapTest {
    
    public NeuralMapTest() {
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
    public void testUSPS() {
        System.out.println("USPS");
        MathEx.setSeed(19650218); // to get repeatable results.

        double[][] x = USPS.x;
        double[][] testx = USPS.testx;

        NeuralMap model = new NeuralMap(x[0].length, 16, 5, 10.0, 0.2, 0.006, x.length);
        for (int epoch = 1; epoch <= 5; epoch++) {
            for (int i = 0; i < x.length; i++) {
                model.update(x[i]);
            }
            System.out.format("%d neurons after %d epochs%n", model.neurons().length, epoch);
        }

        double error = 0.0;
        for (double[] xi : x) {
            double[] yi = model.quantize(xi).get();
            error += MathEx.distance(xi, yi);
        }
        error /= x.length;
        System.out.format("Training Quantization Error = %.4f%n", error);
        assertEquals(5.3979, error, 1E-4);

        error = 0.0;
        for (double[] xi : testx) {
            double[] yi = model.quantize(xi).get();
            error += MathEx.distance(xi, yi);
        }
        error /= testx.length;

        System.out.format("Test Quantization Error = %.4f%n", error);
        assertEquals(6.5582, error, 1E-4);
    }
}
