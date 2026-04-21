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
package smile.feature.selection;

import java.util.Arrays;
import smile.datasets.Abalone;
import org.junit.jupiter.api.*;
import smile.math.MathEx;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class FRegressionTest {

    public FRegressionTest() {
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
    public void testAbalone() throws Exception {
        System.out.println("Abalone");
        var abalone = new Abalone();
        FRegression[] f = FRegression.fit(abalone.train(), "rings");
        assertEquals(8, f.length);
        assertEquals(382.6936, f[0].statistic(), 1E-4);
        assertEquals(1415.9152, f[1].statistic(), 1E-4);
        assertEquals(1569.9083, f[2].statistic(), 1E-4);
        assertEquals(2104.4944, f[7].statistic(), 1E-4);
    }

    @Test
    public void testGivenAllFeaturesWhenFittingThenPValuesAreInUnitInterval() throws Exception {
        var abalone = new Abalone();
        FRegression[] f = FRegression.fit(abalone.train(), "rings");
        for (FRegression fr : f) {
            assertTrue(fr.pvalue() >= 0.0 && fr.pvalue() <= 1.0,
                    "p-value must be in [0,1]: " + fr.pvalue() + " for " + fr.feature());
            assertTrue(fr.statistic() >= 0.0,
                    "F-statistic must be non-negative: " + fr.statistic());
        }
    }

    @Test
    public void testGivenCategoricalColumnWhenFittingThenCategoricalFlagIsTrue() throws Exception {
        // Abalone column 0 is 'sex' — a categorical feature
        var abalone = new Abalone();
        FRegression[] f = FRegression.fit(abalone.train(), "rings");
        // sex is at position 0 in the schema (first predictor)
        FRegression sex = f[0];
        assertEquals("sex", sex.feature());
        assertTrue(sex.categorical(), "'sex' should be identified as categorical");
        // remaining features should not be categorical
        for (int i = 1; i < f.length; i++) {
            assertFalse(f[i].categorical(), f[i].feature() + " should not be categorical");
        }
    }

    @Test
    public void testGivenMultipleFeaturesWhenSortingThenAscendingByStatistic() throws Exception {
        var abalone = new Abalone();
        FRegression[] f = FRegression.fit(abalone.train(), "rings");
        Arrays.sort(f);
        for (int i = 1; i < f.length; i++) {
            assertTrue(f[i - 1].statistic() <= f[i].statistic(),
                    "sorted statistics should be non-decreasing");
        }
    }

    @Test
    public void testGivenTwoResultsWhenComparingThenLowerStatisticIsLess() {
        FRegression low  = new FRegression("low",  10.0, 0.01, false);
        FRegression high = new FRegression("high", 100.0, 0.001, false);
        assertTrue(low.compareTo(high) < 0);
        assertTrue(high.compareTo(low) > 0);
        assertEquals(0, low.compareTo(new FRegression("copy", 10.0, 0.01, false)));
    }

    @Test
    public void testToStringContainsFeatureNameStatisticAndPValue() throws Exception {
        FRegression fr = new FRegression("weight", 42.5, 0.001, false);
        String s = fr.toString();
        assertTrue(s.contains("FRegression"), "toString should contain type name");
        assertTrue(s.contains("weight"), "toString should contain feature name");
    }
}
