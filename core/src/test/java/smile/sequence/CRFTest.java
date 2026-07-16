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
package smile.sequence;

import java.util.Properties;
import smile.data.Tuple;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.datasets.Hyphen;
import smile.datasets.Protein;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class CRFTest {

    public CRFTest() {
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
    @Tag("integration")
    public void testProtein() throws Exception {
        System.out.println("protein");
        var protein = new Protein();
        CRF model = CRF.fit(protein.train().seq(), protein.train().tag(), new CRF.Options(100, 20, 100, 5, 0.3));

        int error = 0;
        int n = 0;
        var seq = protein.test().seq();
        var tag = protein.test().tag();
        for (int i = 0; i < seq.length; i++) {
            n += seq[i].length;
            int[] label = model.predict(seq[i]);
            for (int j = 0; j < seq[i].length; j++) {
                if (tag[i][j] != label[j]) {
                    error++;
                }
            }
        }

        int viterbiError = 0;
        for (int i = 0; i < seq.length; i++) {
            int[] label = model.viterbi(seq[i]);
            for (int j = 0; j < seq[i].length; j++) {
                if (tag[i][j] != label[j]) {
                    viterbiError++;
                }
            }
        }

        System.out.format("Protein error (forward-backward) is %d of %d%n", error, n);
        System.out.format("Protein error (forward-backward) rate = %.2f%%%n", 100.0 * error / n);
        System.out.format("Protein error (Viterbi) is %d of %d%n", viterbiError, n);
        System.out.format("Protein error (Viterbi) rate = %.2f%%%n", 100.0 * viterbiError / n);
        assertEquals(1235, error);
        assertEquals(1320, viterbiError);
    }

    @Test
    @Tag("integration")
    public void testHyphen() throws Exception {
        System.out.println("hyphen");
        var hyphen = new Hyphen();
        CRF model = CRF.fit(hyphen.train().seq(), hyphen.train().tag(), new CRF.Options(100, 20, 100, 5, 0.3));

        int error = 0;
        int n = 0;
        var seq = hyphen.test().seq();
        var tag = hyphen.test().tag();
        for (int i = 0; i < seq.length; i++) {
            n += seq[i].length;
            int[] label = model.predict(seq[i]);
            for (int j = 0; j < seq[i].length; j++) {
                if (tag[i][j] != label[j]) {
                    error++;
                }
            }
        }

        int viterbiError = 0;
        for (int i = 0; i < seq.length; i++) {
            int[] label = model.viterbi(seq[i]);
            for (int j = 0; j < seq[i].length; j++) {
                if (tag[i][j] != label[j]) {
                    viterbiError++;
                }
            }
        }

        System.out.format("Hyphen error (forward-backward) is %d of %d%n", error, n);
        System.out.format("Hyphen error (forward-backward) rate = %.2f%%%n", 100.0 * error / n);
        System.out.format("Hyphen error (Viterbi) is %d of %d%n", viterbiError, n);
        System.out.format("Hyphen error (Viterbi) rate = %.2f%%%n", 100.0 * viterbiError / n);
        assertEquals(470, error);
        assertEquals(508, viterbiError);
    }

    @Test
    public void givenInvalidCrfOptions_whenConstructed_thenThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new CRF.Options(0, 20, 100, 5, 0.3));
        assertThrows(IllegalArgumentException.class, () -> new CRF.Options(10, 1, 100, 5, 0.3));
        assertThrows(IllegalArgumentException.class, () -> new CRF.Options(10, 20, 1, 5, 0.3));
        assertThrows(IllegalArgumentException.class, () -> new CRF.Options(10, 20, 100, 0, 0.3));
        assertThrows(IllegalArgumentException.class, () -> new CRF.Options(10, 20, 100, 5, 1.5));
    }

    @Test
    public void givenCrfOptionsProperties_whenRoundTripped_thenValuesArePreserved() {
        CRF.Options expected = new CRF.Options(9, 7, 6, 2, 0.25);
        Properties props = expected.toProperties();
        CRF.Options actual = CRF.Options.of(props);
        assertEquals(expected, actual);
    }

    @Test
    public void givenInvalidTrainingSequences_whenFittingCrf_thenThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> CRF.fit(new Tuple[0][], new int[0][]));

        Tuple t = tuple(1);
        assertThrows(IllegalArgumentException.class, () -> CRF.fit(new Tuple[][] {{t, t}}, new int[][] {{0}}));
        assertThrows(IllegalArgumentException.class, () -> CRF.fit(new Tuple[][] {{}}, new int[][] {{}}));
    }

    @Test
    public void givenEmptySequence_whenPredictingCrf_thenThrowIllegalArgumentException() {
        Tuple t0 = tuple(0);
        Tuple t1 = tuple(1);
        CRF model = CRF.fit(
                new Tuple[][] {{t0, t1}, {t1, t0}},
                new int[][] {{0, 0}, {0, 0}},
                new CRF.Options(1, 2, 2, 1, 1.0)
        );

        assertThrows(IllegalArgumentException.class, () -> model.predict(new Tuple[0]));
        assertThrows(IllegalArgumentException.class, () -> model.viterbi(new Tuple[0]));
    }

    private static Tuple tuple(int value) {
        StructType schema = new StructType(new StructField("x", DataTypes.IntType));
        return Tuple.of(schema, new int[] { value });
    }
}