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
package smile.clustering;

import smile.io.Read;
import smile.io.Write;
import smile.datasets.GaussianMixture;
import smile.datasets.USPS;
import smile.math.MathEx;
import smile.validation.metric.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng
 */
public class XMeansTest {
    public XMeansTest() {
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
    @Tag("integration")
    public void givenFloatGaussianMixture_whenFittingXMeans_thenReturnFloatCentroidsWithAcceptableQuality() {
        System.out.println("XMeans float GaussianMixture");
        GaussianMixture mixture = GaussianMixture.generate();
        double[][] x = mixture.x();
        int[] y = mixture.y();

        // Given – convert double[][] mixture to float[][]
        float[][] fx = new float[x.length][x[0].length];
        for (int i = 0; i < x.length; i++)
            for (int j = 0; j < x[0].length; j++)
                fx[i][j] = (float) x[i][j];

        // When
        var model = XMeans.fit(fx, 10, 100);
        System.out.println(model);

        // Then – return type must be CentroidClustering<float[], float[]>
        assertInstanceOf(float[].class, model.center(0));
        assertTrue(model.k() >= 2, "Should find at least 2 clusters");
        assertTrue(model.distortion() > 0.0);

        double r = RandIndex.of(y, model.group());
        double r2 = AdjustedRandIndex.of(y, model.group());
        System.out.format("Float training rand index = %.2f%%, adjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertTrue(r > 0.75, "Rand index should exceed 0.75");
    }

    @Test
    public void givenFloatData_whenFittingXMeans_thenReturnFloatCentroidsAndDetectClusters() {
        // Given – four distinct float clusters
        float[][] data = {
            {0.0f, 0.0f}, {0.1f, 0.0f}, {0.0f, 0.1f}, {0.1f, 0.1f},
            {5.0f, 0.0f}, {5.1f, 0.0f}, {5.0f, 0.1f}, {5.1f, 0.1f},
            {0.0f, 5.0f}, {0.1f, 5.0f}, {0.0f, 5.1f}, {0.1f, 5.1f},
            {5.0f, 5.0f}, {5.1f, 5.0f}, {5.0f, 5.1f}, {5.1f, 5.1f},
            // duplicates to exceed the ni>=25 threshold per cluster
            {0.05f, 0.05f}, {0.05f, 0.05f}, {0.05f, 0.05f}, {0.05f, 0.05f}, {0.05f, 0.05f},
            {0.05f, 0.05f}, {0.05f, 0.05f}, {0.05f, 0.05f}, {0.05f, 0.05f},
            {5.05f, 0.05f}, {5.05f, 0.05f}, {5.05f, 0.05f}, {5.05f, 0.05f}, {5.05f, 0.05f},
            {5.05f, 0.05f}, {5.05f, 0.05f}, {5.05f, 0.05f}, {5.05f, 0.05f},
            {0.05f, 5.05f}, {0.05f, 5.05f}, {0.05f, 5.05f}, {0.05f, 5.05f}, {0.05f, 5.05f},
            {0.05f, 5.05f}, {0.05f, 5.05f}, {0.05f, 5.05f}, {0.05f, 5.05f},
            {5.05f, 5.05f}, {5.05f, 5.05f}, {5.05f, 5.05f}, {5.05f, 5.05f}, {5.05f, 5.05f},
            {5.05f, 5.05f}, {5.05f, 5.05f}, {5.05f, 5.05f}, {5.05f, 5.05f}
        };

        // When
        var model = XMeans.fit(data, 8, 100);

        // Then
        assertEquals("X-Means", model.name());
        assertInstanceOf(float[].class, model.center(0));
        assertTrue(model.k() >= 1, "Should return at least 1 cluster");
        assertTrue(model.distortion() >= 0.0);
    }

    @Test
    @Tag("integration")
    public void testUSPS() throws Exception {
        System.out.println("USPS");
        var usps = new USPS();
        double[][] x = usps.x();
        int[] y = usps.y();
        double[][] testx = usps.testx();
        int[] testy = usps.testy();
            
        var model = XMeans.fit(x, 10, 100);
        System.out.println(model);

        double r = RandIndex.of(y, model.group());
        double r2 = AdjustedRandIndex.of(y, model.group());
        System.out.format("Training rand index = %.2f%%, adjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertEquals(0.8977, r, 1E-4);
        assertEquals(0.4620, r2, 1E-4);

        System.out.format("MI = %.2f%n", MutualInformation.of(y, model.group()));
        System.out.format("NMI.joint = %.2f%%%n", 100 * NormalizedMutualInformation.joint(y, model.group()));
        System.out.format("NMI.max = %.2f%%%n", 100 * NormalizedMutualInformation.max(y, model.group()));
        System.out.format("NMI.min = %.2f%%%n", 100 * NormalizedMutualInformation.min(y, model.group()));
        System.out.format("NMI.sum = %.2f%%%n", 100 * NormalizedMutualInformation.sum(y, model.group()));
        System.out.format("NMI.sqrt = %.2f%%%n", 100 * NormalizedMutualInformation.sqrt(y, model.group()));

        int[] p = new int[testx.length];
        for (int i = 0; i < testx.length; i++) {
            p[i] = model.predict(testx[i]);
        }
            
        r = RandIndex.of(testy, p);
        r2 = AdjustedRandIndex.of(testy, p);
        System.out.format("Testing rand index = %.2f%%, adjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertEquals(0.8882, r, 1E-4);
        assertEquals(0.4189, r2, 1E-4);

        java.nio.file.Path temp = Write.object(model);
        Read.object(temp);
    }
}
