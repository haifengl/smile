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

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.data.USPS;
import smile.math.MathEx;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng
 */
public class GrowingNeuralGasTest {
    
    public GrowingNeuralGasTest() {
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

        GrowingNeuralGas model = new GrowingNeuralGas(x[0].length);
        for (int i = 1; i <= 10; i++) {
            for (int j : MathEx.permutate(x.length)) {
                model.update(x[j]);
            }
            System.out.format("%d neurons after %d epochs%n", model.neurons().length, i);
        }

        double error = 0.0;
        for (double[] xi : x) {
            double[] yi = model.quantize(xi);
            error += MathEx.distance(xi, yi);
        }
        error /= x.length;
        System.out.format("Training Quantization Error = %.4f%n", error);
        assertEquals(5.6185, error, 1E-4);

        error = 0.0;
        for (double[] xi : testx) {
            double[] yi = model.quantize(xi);
            error += MathEx.distance(xi, yi);
        }
        error /= testx.length;

        System.out.format("Test Quantization Error = %.4f%n", error);
        assertEquals(6.4506, error, 1E-4);
    }
}
