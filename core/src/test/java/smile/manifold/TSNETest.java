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
package smile.manifold;

import smile.datasets.MNIST;
import smile.math.MathEx;
import smile.feature.extraction.PCA;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class TSNETest {

    public TSNETest() {
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
        System.out.println("tSNE");
        MathEx.setSeed(19650218); // to get repeatable results.
        var mnist = new MNIST();
        double[][] x = mnist.x();
        PCA pca = PCA.fit(x).getProjection(50);
        double[][] X = pca.apply(x);

        long start = System.currentTimeMillis();
        TSNE tsne = new TSNE(X, new TSNE.Options(2, 20, 200, 550));
        long end = System.currentTimeMillis();
        System.out.format("t-SNE takes %.2f seconds\n", (end - start) / 1000.0);

        assertEquals(1.3872256, tsne.cost(), 0.1);
        /*
        var coordinates = tsne.coordinates();
        double[] coord0    = {  2.6870328, 16.8175010};
        double[] coord100  = {-16.3270630,  3.6016438};
        double[] coord1000 = {-16.2529939, 26.8543395};
        double[] coord2000 = {-17.0491869,  4.8453648};
        assertArrayEquals(coord0,    coordinates[0], 1E-6);
        assertArrayEquals(coord100,  coordinates[100], 1E-6);
        assertArrayEquals(coord1000, coordinates[1000], 1E-6);
        assertArrayEquals(coord2000, coordinates[2000], 1E-6);
         */
    }
}
