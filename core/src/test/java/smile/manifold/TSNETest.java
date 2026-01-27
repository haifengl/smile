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
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
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
        TSNE tsne = TSNE.fit(X, new TSNE.Options(2, 20, 200, 12, 550));
        long end = System.currentTimeMillis();
        System.out.format("t-SNE takes %.2f seconds\n", (end - start) / 1000.0);

        assertEquals(1.4170, tsne.cost(), 1E-3);
    }
}
