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
import smile.datasets.USPS;
import smile.math.MathEx;
import smile.validation.metric.*;
import org.junit.jupiter.api.*;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class DeterministicAnnealingTest {
    
    public DeterministicAnnealingTest() {
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
    public void testUSPS() throws Exception {
        System.out.println("USPS");
        var usps = new USPS();
        double[][] x = usps.x();
        int[] y = usps.y();
        double[][] testx = usps.testx();
        int[] testy = usps.testy();

        var model = DeterministicAnnealing.fit(x, 10, 0.8, 100);
        System.out.println(model);

        double r = RandIndex.of(y, model.group());
        double r2 = AdjustedRandIndex.of(y, model.group());
        System.out.format("Training rand index = %.2f%%, adjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertEquals(0.8975, r, 1E-4);
        assertEquals(0.4701, r2, 1E-4);

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
        assertEquals(0.8996, r, 1E-2);
        assertEquals(0.4745, r2, 1E-2);

        java.nio.file.Path temp = Write.object(model);
        Read.object(temp);
    }

    private static final double[][] DATA = {
            {0.0, 0.0}, {0.1, 0.1},
            {5.0, 5.0}, {5.1, 5.1}
    };

    // ── fit produces finite, valid result ─────────────────────────────────────

    @Test
    void givenSimpleData_whenFitting_thenDistortionIsFiniteAndGroupsAreValid() {
        MathEx.setSeed(42);
        CentroidClustering<double[], double[]> model =
                DeterministicAnnealing.fit(DATA, 2, 0.9, 50);

        assertTrue(Double.isFinite(model.distortion()),
                "Distortion must be finite (would be NaN before the 0*log(0) fix)");
        assertTrue(model.distortion() >= 0.0);
        assertEquals(2, model.k());
        assertEquals(DATA.length, model.group().length);

        for (int g : model.group()) {
            assertTrue(g >= 0 && g < model.k(), "Every label must be in [0, k)");
        }
    }

    @Test
    void givenWellSeparatedClusters_whenFitting_thenSeparateGroups() {
        MathEx.setSeed(42);
        CentroidClustering<double[], double[]> model =
                DeterministicAnnealing.fit(DATA, 2, 0.9, 50);

        // Points (0,0)+(0.1,0.1) should share a label; (5,5)+(5.1,5.1) the other.
        assertEquals(model.group(0), model.group(1),
                "Points in the near-origin cluster should share a label");
        assertEquals(model.group(2), model.group(3),
                "Points in the far cluster should share a label");
        assertNotEquals(model.group(0), model.group(2),
                "Points in different clusters should have different labels");
    }

    // ── Options validation ─────────────────────────────────────────────────────

    @Test
    void givenKmaxLessThanTwo_whenConstructingOptions_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new DeterministicAnnealing.Options(1, 0.9, 100));
    }

    @Test
    void givenAlphaOutOfRange_whenConstructingOptions_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new DeterministicAnnealing.Options(5, 0.0, 100));
        assertThrows(IllegalArgumentException.class,
                () -> new DeterministicAnnealing.Options(5, 1.0, 100));
        assertThrows(IllegalArgumentException.class,
                () -> new DeterministicAnnealing.Options(5, -0.1, 100));
    }

    @Test
    void givenNonPositiveMaxIter_whenConstructingOptions_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new DeterministicAnnealing.Options(5, 0.9, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new DeterministicAnnealing.Options(5, 0.9, -1));
    }

    @Test
    void givenNegativeTol_whenConstructingOptions_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new DeterministicAnnealing.Options(5, 0.9, 100, -1E-4, 1E-2, null));
    }

    @Test
    void givenNegativeSplitTol_whenConstructingOptions_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new DeterministicAnnealing.Options(5, 0.9, 100, 1E-4, -1E-2, null));
    }

    // ── Options round-trip ────────────────────────────────────────────────────

    @Test
    void givenOptions_whenRoundTripped_thenAllValuesPreserved() {
        DeterministicAnnealing.Options opts =
                new DeterministicAnnealing.Options(5, 0.85, 200, 1E-5, 5E-3, null);
        Properties props = opts.toProperties();
        DeterministicAnnealing.Options restored = DeterministicAnnealing.Options.of(props);

        assertEquals(5, restored.kmax());
        assertEquals(0.85, restored.alpha(), 1E-15);
        assertEquals(200, restored.maxIter());
        assertEquals(1E-5, restored.tol(), 1E-20);
        assertEquals(5E-3, restored.splitTol(), 1E-20);
        assertNull(restored.controller());
    }

    @Test
    void givenMissingProperties_whenRestoringOptions_thenDefaultsAreApplied() {
        DeterministicAnnealing.Options restored =
                DeterministicAnnealing.Options.of(new Properties());

        assertEquals(2, restored.kmax());
        assertEquals(0.9, restored.alpha(), 1E-15);
        assertEquals(100, restored.maxIter());
        assertEquals(1E-4, restored.tol(), 1E-15);
        assertEquals(1E-2, restored.splitTol(), 1E-15);
    }
}
