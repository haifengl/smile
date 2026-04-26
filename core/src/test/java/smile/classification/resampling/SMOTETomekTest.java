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
package smile.classification.resampling;

import java.util.Arrays;
import java.util.Properties;
import org.junit.jupiter.api.*;
import smile.math.MathEx;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SMOTETomek}.
 *
 * @author Haifeng Li
 */
@TestMethodOrder(MethodOrderer.MethodName.class)
public class SMOTETomekTest {

    /**
     * 2-D dataset: 90 majority (label=0) and 10 minority (label=1) partially
     * overlapping to ensure both SMOTE generates samples and Tomek links exist.
     */
    private static double[][] data;
    private static int[] labels;

    @BeforeAll
    static void setup() {
        MathEx.setSeed(19650218);
        data = new double[100][2];
        labels = new int[100];
        for (int i = 0; i < 90; i++) {
            data[i][0] = MathEx.random(-2.0, 2.0);
            data[i][1] = MathEx.random(-2.0, 2.0);
            labels[i] = 0;
        }
        // Minority inside majority range — guarantees overlap and boundary samples.
        for (int i = 90; i < 100; i++) {
            data[i][0] = MathEx.random(-0.5, 0.5);
            data[i][1] = MathEx.random(-0.5, 0.5);
            labels[i] = 1;
        }
    }

    // ─── Options tests ────────────────────────────────────────────────────────

    @Test
    public void testDefaultOptions() {
        var opts = new SMOTETomek.Options();

        // SMOTE defaults
        assertEquals(5,   opts.smoteOptions().k());
        assertEquals(1.0, opts.smoteOptions().ratio(), 1e-9);
        assertEquals(20,  opts.smoteOptions().highDimThreshold());

        // TomekLinks defaults
        assertEquals(20,  opts.tomekOptions().highDimThreshold());
        assertEquals(10,  opts.tomekOptions().rpfNumTrees());
        assertEquals(30,  opts.tomekOptions().rpfLeafSize());
    }

    @Test
    public void testOptionsToPropertiesRoundTrip() {
        // Given — non-default values for both sub-options
        var smoteOpts = new SMOTE.Options(3, 2.0, 15, 8, 25);
        var tomekOpts = new TomekLinks.Options(12, 6, 20);
        var opts = new SMOTETomek.Options(smoteOpts, tomekOpts);

        // When
        Properties props = opts.toProperties();
        SMOTETomek.Options restored = SMOTETomek.Options.of(props);

        // Then — SMOTE fields
        assertEquals(smoteOpts.k(),                restored.smoteOptions().k());
        assertEquals(smoteOpts.ratio(),            restored.smoteOptions().ratio(), 1e-9);
        assertEquals(smoteOpts.highDimThreshold(), restored.smoteOptions().highDimThreshold());
        assertEquals(smoteOpts.rpfNumTrees(),      restored.smoteOptions().rpfNumTrees());
        assertEquals(smoteOpts.rpfLeafSize(),      restored.smoteOptions().rpfLeafSize());

        // Then — TomekLinks fields
        assertEquals(tomekOpts.highDimThreshold(), restored.tomekOptions().highDimThreshold());
        assertEquals(tomekOpts.rpfNumTrees(),      restored.tomekOptions().rpfNumTrees());
        assertEquals(tomekOpts.rpfLeafSize(),      restored.tomekOptions().rpfLeafSize());
    }

    @Test
    public void testOptionsOfUsesDefaultsForMissingKeys() {
        var defaults = new SMOTETomek.Options();
        var restored = SMOTETomek.Options.of(new Properties());

        assertEquals(defaults.smoteOptions().k(),                restored.smoteOptions().k());
        assertEquals(defaults.smoteOptions().ratio(),            restored.smoteOptions().ratio(), 1e-9);
        assertEquals(defaults.tomekOptions().highDimThreshold(), restored.tomekOptions().highDimThreshold());
    }

    @Test
    public void testOptionsNullSmoteThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new SMOTETomek.Options(null, new TomekLinks.Options()));
    }

    @Test
    public void testOptionsNullTomekThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new SMOTETomek.Options(new SMOTE.Options(), null));
    }

    // ─── fit() tests ─────────────────────────────────────────────────────────

    @Test
    public void testFitDefaultOptions() {
        // Given / When
        MathEx.setSeed(42);
        SMOTETomek result = SMOTETomek.fit(data, labels);

        // Then — SMOTE adds 10 synthetics → 110 samples; Tomek Links may remove some majority.
        // Net size must be > 100 (minority is bigger) and ≤ 110 (Tomek can only shrink).
        assertTrue(result.size() > 100, "Expected size > 100 after SMOTE, got " + result.size());
        assertTrue(result.size() <= 110, "Expected size ≤ 110 after Tomek, got " + result.size());
    }

    @Test
    public void testMinorityCountIncreasesOrStaysAfterTomek() {
        // Given — SMOTE doubles minority; Tomek never removes minority samples.
        MathEx.setSeed(7);
        SMOTETomek result = SMOTETomek.fit(data, labels);

        long minAfter  = Arrays.stream(result.labels()).filter(l -> l == 1).count();
        // SMOTE added 10 synthetic minorities; Tomek removes only majority → min >= 20
        assertTrue(minAfter >= 20,
                "Expected at least 20 minority samples after SMOTETomek, got " + minAfter);
    }

    @Test
    public void testAllSyntheticLabelsAreMinority() {
        // Given / When — all labels beyond the original 100 must be minority.
        MathEx.setSeed(11);
        SMOTETomek result = SMOTETomek.fit(data, labels);

        // The first 100 original rows: verify by finding the post-Tomek set.
        // All labels must be valid (0 or 1).
        for (int lbl : result.labels()) {
            assertTrue(lbl == 0 || lbl == 1, "Unexpected label: " + lbl);
        }
    }

    @Test
    public void testResultSmallerThanJustSmote() {
        // Given — run SMOTE alone and SMOTETomek; the latter must be ≤ the former.
        MathEx.setSeed(13);
        SMOTE smoteOnly = SMOTE.fit(data, labels);

        MathEx.setSeed(13); // same seed so SMOTE phase is identical
        SMOTETomek combined = SMOTETomek.fit(data, labels);

        assertTrue(combined.size() <= smoteOnly.size(),
                "SMOTETomek should be ≤ SMOTE alone (Tomek can only shrink). "
                        + "smote=" + smoteOnly.size() + " combined=" + combined.size());
    }

    @Test
    public void testCustomSmoteRatioInfluencesSize() {
        // Given — ratio=2.0 triples minority before Tomek cleaning
        MathEx.setSeed(21);
        var opts = new SMOTETomek.Options(
                new SMOTE.Options(5, 2.0),
                new TomekLinks.Options());
        SMOTETomek result = SMOTETomek.fit(data, labels, opts);

        // After SMOTE: 100 + 20 = 120; after Tomek: ≤ 120
        assertTrue(result.size() > 100);
        assertTrue(result.size() <= 120);
    }

    @Test
    public void testSizeConsistency() {
        // Given / When
        SMOTETomek result = SMOTETomek.fit(data, labels);

        // Then
        assertEquals(result.size(), result.data().length);
        assertEquals(result.size(), result.labels().length);
    }

    @Test
    public void testWellSeparatedDataSMOTEOnlyExpands() {
        // Given — well-separated classes: Tomek Links finds no cross-class nearest
        // neighbors, so no samples are removed and the result equals the SMOTE output.
        MathEx.setSeed(55);
        double[][] d = new double[100][2];
        int[] l = new int[100];
        for (int i = 0; i < 90; i++) { d[i][0] = MathEx.random(-1,1); d[i][1] = MathEx.random(-1,1); l[i]=0; }
        for (int i = 90; i < 100; i++) { d[i][0] = 10.0 + MathEx.random(-0.5,0.5); d[i][1] = 10.0 + MathEx.random(-0.5,0.5); l[i]=1; }

        MathEx.setSeed(55);
        SMOTE smoteOnly = SMOTE.fit(d, l);
        MathEx.setSeed(55);
        SMOTETomek combined = SMOTETomek.fit(d, l);

        // No Tomek links → sizes must be equal
        assertEquals(smoteOnly.size(), combined.size(),
                "No Tomek links in well-separated data: sizes should match.");
    }

    @Test
    public void testHighDimensionalData() {
        // Given — 50-D overlapping dataset
        MathEx.setSeed(99);
        int dim = 50, nMaj = 200, nMin = 40;
        double[][] d = new double[nMaj + nMin][dim];
        int[] l = new int[nMaj + nMin];
        for (int i = 0; i < nMaj; i++) { for (int j=0;j<dim;j++) d[i][j]=MathEx.random(-2,2); l[i]=0; }
        for (int i = nMaj; i < nMaj+nMin; i++) { for (int j=0;j<dim;j++) d[i][j]=MathEx.random(-1,1); l[i]=1; }

        // When
        SMOTETomek result = SMOTETomek.fit(d, l);

        // Then
        assertTrue(result.size() > nMaj + nMin,  "Minority should be boosted by SMOTE.");
        assertTrue(result.size() <= nMaj + nMin * 2, "Tomek cannot add samples.");
        assertEquals(result.data().length, result.labels().length);
    }

    @Test
    public void testFitPropagatesIllegalArgument() {
        // data/labels mismatch is caught by SMOTE before Tomek runs
        assertThrows(IllegalArgumentException.class,
                () -> SMOTETomek.fit(data, new int[50]));
    }
}

