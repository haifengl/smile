/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.math.kernel;

import org.junit.jupiter.api.*;

/**
 *
 * @author Haifeng Li
 */
public class MercerKernelTest {

    public MercerKernelTest() {
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
    public void testParse() {
        System.out.println("parse");
        MercerKernel.of("linear()");
        MercerKernel.of("polynomial(2, 0.1, 0.0)");
        MercerKernel.of("gaussian(0.1)");
        MercerKernel.of("matern(0.1, 1.5)");
        MercerKernel.of("laplacian(0.1)");
        MercerKernel.of("tanh(0.1, 0.0)");
        MercerKernel.of("tps(0.1)");
        MercerKernel.of("pearson(0.1, 0.0)");
        MercerKernel.of("hellinger");
    }

    @Test
    public void testParseToString() {
        System.out.println("parse");
        MercerKernel.of(new LinearKernel().toString());
        MercerKernel.of(new PolynomialKernel(2, 0.1, 0.0).toString());
        MercerKernel.of(new GaussianKernel(0.1).toString());
        MercerKernel.of(new MaternKernel(0.1, 1.5).toString());
        MercerKernel.of(new LaplacianKernel(0.1).toString());
        MercerKernel.of(new HyperbolicTangentKernel(0.1, 0.0).toString());
        MercerKernel.of(new ThinPlateSplineKernel(0.1).toString());
        MercerKernel.of(new PearsonKernel(0.1, 0.0).toString());
        MercerKernel.of(new HellingerKernel().toString());
    }
}