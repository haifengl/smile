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
public class GMeansTest {
    public GMeansTest() {
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
    public void givenFloatGaussianMixture_whenFittingGMeans_thenReturnFloatCentroidsWithAcceptableQuality() {
        System.out.println("GMeans float GaussianMixture");
        GaussianMixture mixture = GaussianMixture.generate();
        double[][] x = mixture.x();
        int[] y = mixture.y();

        // Given – convert double[][] mixture to float[][]
        float[][] fx = new float[x.length][x[0].length];
        for (int i = 0; i < x.length; i++)
            for (int j = 0; j < x[0].length; j++)
                fx[i][j] = (float) x[i][j];

        // When
        var model = GMeans.fit(fx, 10, 100);
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
    public void givenFloatData_whenFittingGMeans_thenReturnFloatCentroidsAndDetectClusters() {
        // Given – four distinct float clusters with enough points for the Anderson-Darling test
        float[][] data = new float[100][2];
        for (int i = 0; i < 25; i++) {
            data[i]       = new float[]{(float)(0.0 + 0.01 * i), (float)(0.0 + 0.01 * i)};
            data[25 + i]  = new float[]{(float)(5.0 + 0.01 * i), (float)(0.0 + 0.01 * i)};
            data[50 + i]  = new float[]{(float)(0.0 + 0.01 * i), (float)(5.0 + 0.01 * i)};
            data[75 + i]  = new float[]{(float)(5.0 + 0.01 * i), (float)(5.0 + 0.01 * i)};
        }

        // When
        var model = GMeans.fit(data, 8, 100);

        // Then
        assertEquals("G-Means", model.name());
        assertInstanceOf(float[].class, model.center(0));
        assertTrue(model.k() >= 2, "Should find at least 2 clusters");
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

        var model = GMeans.fit(x, 10, 100);
        System.out.println(model);

        double r = RandIndex.of(y, model.group());
        double r2 = AdjustedRandIndex.of(y, model.group());
        System.out.format("Training rand index = %.2f%%, adjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertEquals(0.9203, r, 1E-4);
        assertEquals(0.5881, r2, 1E-4);

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
        assertEquals(0.9103, r, 1E-4);
        assertEquals(0.5337, r2, 1E-4);

        java.nio.file.Path temp = Write.object(model);
        Read.object(temp);
    }
}
