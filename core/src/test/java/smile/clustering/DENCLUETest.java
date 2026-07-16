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
import smile.math.MathEx;
import smile.validation.metric.*;
import org.junit.jupiter.api.*;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class DENCLUETest {
    
    public DENCLUETest() {
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
    public void testGaussianMixture() throws Exception {
        System.out.println("Gaussian Mixture");
        GaussianMixture mixture = GaussianMixture.generate();
        double[][] x = mixture.x();
        int[] y = mixture.y();

        DENCLUE model = DENCLUE.fit(x, 0.85, 100);
        System.out.println(model);

        double r = RandIndex.of(y, model.group());
        double r2 = AdjustedRandIndex.of(y, model.group());
        System.out.println("The number of clusters: " + model.k);
        System.out.format("Training rand index = %.2f%%, adjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertEquals(0.9562, r, 1E-4);
        assertEquals(0.8851, r2, 1E-4);

        System.out.format("MI = %.2f%n", MutualInformation.of(y, model.group()));
        System.out.format("NMI.joint = %.2f%%%n", 100 * NormalizedMutualInformation.joint(y, model.group()));
        System.out.format("NMI.max = %.2f%%%n", 100 * NormalizedMutualInformation.max(y, model.group()));
        System.out.format("NMI.min = %.2f%%%n", 100 * NormalizedMutualInformation.min(y, model.group()));
        System.out.format("NMI.sum = %.2f%%%n", 100 * NormalizedMutualInformation.sum(y, model.group()));
        System.out.format("NMI.sqrt = %.2f%%%n", 100 * NormalizedMutualInformation.sqrt(y, model.group()));

        java.nio.file.Path temp = Write.object(model);
        Read.object(temp);
    }

    // ── Options round-trip ────────────────────────────────────────────────────

    @Test
    void givenOptions_whenRoundTripped_thenSigmaIsPreserved() {
        // This test directly exercises the bug fix: before the fix, Options.of()
        // read the key "smile.denclue.radius" while toProperties() wrote
        // "smile.denclue.sigma", so sigma always restored to the default 1.0.
        DENCLUE.Options opts = new DENCLUE.Options(2.5, 100, 15, 1E-3);
        Properties props = opts.toProperties();
        DENCLUE.Options restored = DENCLUE.Options.of(props);

        assertEquals(2.5, restored.sigma(), 1E-12,
                "sigma must survive a toProperties/of round-trip");
        assertEquals(100, restored.m());
        assertEquals(15, restored.minPts());
        assertEquals(1E-3, restored.tol(), 1E-15);
    }

    @Test
    void givenMissingProperties_whenRestoringOptions_thenDefaultsAreApplied() {
        DENCLUE.Options restored = DENCLUE.Options.of(new Properties());

        assertEquals(1.0, restored.sigma(), 1E-12);
        assertEquals(100, restored.m());
        assertEquals(10, restored.minPts());
        assertEquals(1E-2, restored.tol(), 1E-15);
    }

    @Test
    void givenOptions_whenRoundTripped_thenAllFieldsPreserved() {
        DENCLUE.Options opts = new DENCLUE.Options(3.14, 50, 7, 5E-4);
        Properties props = opts.toProperties();
        DENCLUE.Options restored = DENCLUE.Options.of(props);

        assertEquals(3.14, restored.sigma(), 1E-12);
        assertEquals(50, restored.m());
        assertEquals(7, restored.minPts());
        assertEquals(5E-4, restored.tol(), 1E-15);
    }

    // ── Options validation ─────────────────────────────────────────────────────

    @Test
    void givenZeroSigma_whenConstructingOptions_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new DENCLUE.Options(0.0, 100));
    }

    @Test
    void givenNegativeSigma_whenConstructingOptions_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new DENCLUE.Options(-1.0, 100));
    }

    @Test
    void givenZeroM_whenConstructingOptions_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new DENCLUE.Options(1.0, 0));
    }

    @Test
    void givenZeroMinPts_whenConstructingOptions_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new DENCLUE.Options(1.0, 100, 0, 1E-2));
    }

    @Test
    void givenNegativeTol_whenConstructingOptions_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new DENCLUE.Options(1.0, 100, 5, -1.0));
    }

    @Test
    void givenValidShortConstructor_whenConstructingOptions_thenSucceeds() {
        assertDoesNotThrow(() -> new DENCLUE.Options(1.0, 100));
    }
}
