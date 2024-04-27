/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.vq;

import smile.test.data.USPS;
import smile.math.MathEx;
import smile.math.TimeFunction;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class NeuralGasTest {
    
    public NeuralGasTest() {
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
    public void testUSPS() {
        System.out.println("USPS");
        MathEx.setSeed(19650218); // to get repeatable results.

        double[][] x = USPS.x;
        double[][] testx = USPS.testx;

        int epochs = 20;
        int T = x.length * epochs;
        NeuralGas model = new NeuralGas(NeuralGas.seed(400, x),
                TimeFunction.exp(0.3, T / 2.0),
                TimeFunction.exp(30, T / 8.0),
                TimeFunction.constant(x.length * 2));

        for (int i = 1; i <= epochs; i++) {
            for (int j : MathEx.permutate(x.length)) {
                model.update(x[j]);
            }
        }

        double error = 0.0;
        for (double[] xi : x) {
            double[] yi = model.quantize(xi);
            error += MathEx.distance(xi, yi);
        }
        error /= x.length;
        System.out.format("Training Quantization Error = %.4f%n", error);
        assertEquals(5.7002, error, 1E-4);

        error = 0.0;
        for (double[] xi : testx) {
            double[] yi = model.quantize(xi);
            error += MathEx.distance(xi, yi);
        }
        error /= testx.length;

        System.out.format("Test Quantization Error = %.4f%n", error);
        assertEquals(6.5502, error, 1E-4);
    }
}
