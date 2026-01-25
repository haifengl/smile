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
package smile.manifold;

import smile.math.kernel.GaussianKernel;
import smile.datasets.CPU;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class KPCATest {

    double[] latent = {
            4.91131039160626, 2.976525602644278, 2.7371649107443994, 2.109980455032412,
            2.00000000002315, 2.000000000000374, 2.0000000000000018, 2.000000000000000,
            2.00000000000000, 2.000000000000000, 2.0,                1.999999999999999,
            1.99999999999999, 1.99999999999999,  1.999999999972224,  1.88513691900984,
            1.79637381617472, 1.47414075439963,  1.441990487811404,  1.36229297251701,
            1.16314072557562, 1.04026341974681,  1.026973122071443,  1.0148003610812555,
            1.00664898579298, 1.00073677825638, 1.000181640039423,   1.0000027607724675,
            1.00000268674600
    };

    public KPCATest() {
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
    public void test() throws Exception {
        System.out.println("KPCA");
        var cpu = new CPU();
        var x = cpu.x();
        KPCA<double[]> kpca = KPCA.fit(x, new GaussianKernel(Math.sqrt(2.5)), new KPCA.Options(29));
        for (int i = 0; i < latent.length; i++) {
            assertEquals(latent[i], kpca.variances()[i], 1E-3);
        }

        double[][] points = kpca.apply(x);
        double[][] coord = kpca.coordinates();
        for (int i = 0; i < points.length; i++) {
            for (int j = 0; j < points[i].length; j++) {
                assertEquals(points[i][j], coord[i][j], 1E-7);
            }
        }
    }
}