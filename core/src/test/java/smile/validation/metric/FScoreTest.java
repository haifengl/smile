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
package smile.validation.metric;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class FScoreTest {

    public FScoreTest() {
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
    public void test() {
        System.out.println("F-Score");
        int[] truth = {
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
        };
        int[] prediction = {
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
        };
        FScore instance = new FScore();
        double expResult = 0.87719;
        double result = instance.score(truth, prediction);
        assertEquals(expResult, result, 1E-5);
    }

    @Test
    public void testMicro() {
        System.out.println("Micro-FScore");
        int[] truth = {
                1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
        };
        int[] prediction = {
                1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
        };
        FScore instance = new FScore(1.0, Averaging.Micro);
        double expResult = 0.93;
        double result = instance.score(truth, prediction);
        assertEquals(expResult, result, 1E-4);
    }

    @Test
    public void testMacro() {
        System.out.println("Macro-FScore");
        int[] truth = {
                1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 5, 5, 5
        };
        int[] prediction = {
                1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 2, 3, 2, 2, 2, 3, 1, 3, 3, 3, 4, 5, 4, 4, 4, 4, 1, 5, 5
        };
        FScore instance = new FScore(1.0, Averaging.Macro);
        // Mean of the per-class F1 scores (scikit-learn average='macro'). The
        // previous value 0.8432 was F1 of the averaged precision and recall (#772).
        double expResult = 0.840906;
        double result = instance.score(truth, prediction);
        assertEquals(expResult, result, 1E-5);
    }

    @Test
    public void testWeighted() {
        System.out.println("Weighted-FScore");
        int[] truth = {
                1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 5, 5, 5
        };
        int[] prediction = {
                1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 2, 3, 2, 2, 2, 3, 1, 3, 3, 3, 4, 5, 4, 4, 4, 4, 1, 5, 5
        };
        FScore instance = new FScore(1.0, Averaging.Weighted);
        // Support-weighted mean of the per-class F1 scores (scikit-learn
        // average='weighted'). The previous value 0.8907 was F1 of the averaged
        // precision and recall (#772).
        double expResult = 0.889227;
        double result = instance.score(truth, prediction);
        assertEquals(expResult, result, 1E-5);
    }

    @Test
    public void testMacroPerClass() {
        System.out.println("Macro-FScore per-class");
        // Equal supports (2/2/2); per-class F1 = [2/3, 4/5, 1]. Values verified
        // against scikit-learn fbeta_score(average='macro').
        int[] truth      = {0, 0, 1, 1, 2, 2};
        int[] prediction = {0, 1, 1, 1, 2, 2};
        assertEquals(0.822222, FScore.of(truth, prediction, 1.0, Averaging.Macro), 1E-6);
        assertEquals(0.821549, FScore.of(truth, prediction, 2.0, Averaging.Macro), 1E-6);
        assertEquals(0.849206, FScore.of(truth, prediction, 0.5, Averaging.Macro), 1E-6);
        // With equal supports the weighted average matches the macro average.
        assertEquals(0.822222, FScore.of(truth, prediction, 1.0, Averaging.Weighted), 1E-6);
    }

    @Test
    public void testWeightedVsMacro() {
        System.out.println("Weighted vs Macro FScore");
        // Unequal supports (3/2/1) so weighted and macro differ. Values verified
        // against scikit-learn fbeta_score(average='macro'/'weighted'/'micro').
        int[] truth      = {0, 0, 0, 1, 1, 2};
        int[] prediction = {0, 0, 1, 1, 2, 2};
        assertEquals(0.655556, FScore.of(truth, prediction, 1.0, Averaging.Macro),    1E-6);
        assertEquals(0.677778, FScore.of(truth, prediction, 1.0, Averaging.Weighted), 1E-6);
        assertEquals(0.682540, FScore.of(truth, prediction, 2.0, Averaging.Macro),    1E-6);
        assertEquals(0.662698, FScore.of(truth, prediction, 2.0, Averaging.Weighted), 1E-6);
        // Micro F-score is unaffected (micro precision == micro recall == accuracy).
        assertEquals(0.666667, FScore.of(truth, prediction, 1.0, Averaging.Micro),    1E-6);
    }
}