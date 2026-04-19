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

import java.util.Arrays;
import java.util.Properties;
import smile.math.MathEx;
import smile.datasets.MNIST;
import smile.datasets.SwissRoll;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Karl Li
 */
public class UMAPTest {

    public UMAPTest() {
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
    }

    @BeforeEach
    public void setUp() {
        MathEx.setSeed(19650218); // to get repeatable results.
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void testMnist() throws Exception {
        System.out.println("UMAP MNIST");
        var mnist = new MNIST();
        var x = mnist.x();
        long start = System.currentTimeMillis();
        double[][] coordinates = UMAP.fit(x, new UMAP.Options(15));
        long end = System.currentTimeMillis();
        System.out.format("UMAP takes %.2f seconds\n", (end - start) / 1000.0);
        assertEquals(x.length, coordinates.length);
    }

    @Test
    public void testSwissRoll() throws Exception {
        System.out.println("UMAP SwissRoll");
        var roll = new SwissRoll();
        double[][] data = Arrays.copyOf(roll.data(), 1000);
        long start = System.currentTimeMillis();
        double[][] coordinates = UMAP.fit(data, new UMAP.Options(15));
        long end = System.currentTimeMillis();
        System.out.format("UMAP takes %.2f seconds\n", (end - start) / 1000.0);
        assertEquals(data.length, coordinates.length);
    }

    @Test
    public void givenOptions_whenRoundTripToProperties_thenValuesPreserved() {
        // Given
        UMAP.Options options = new UMAP.Options(15, 2, 200, 1.5, 0.2, 1.0, 7, 1.2, 1.0);

        // When
        Properties props = options.toProperties();
        UMAP.Options restored = UMAP.Options.of(props);

        // Then
        assertEquals(options, restored);
    }

    @Test
    public void givenSparseProperties_whenParsingOptions_thenDefaultsApplied() {
        // Given
        Properties props = new Properties();

        // When
        UMAP.Options options = UMAP.Options.of(props);

        // Then
        assertEquals(15, options.k());
        assertEquals(2, options.d());
        assertEquals(0, options.epochs());
        assertEquals(1.0, options.learningRate(), 1E-12);
        assertEquals(0.1, options.minDist(), 1E-12);
    }
}
