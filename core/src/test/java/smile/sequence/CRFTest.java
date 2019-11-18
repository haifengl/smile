/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/

package smile.sequence;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import smile.data.Hyphen;
import smile.data.Protein;

import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("unused")
public class CRFTest {

    public CRFTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testProtein() {
        System.out.println("protein");

        CRF model = CRF.fit(Protein.seq, Protein.label, 100, 20, 100, 5, 0.3);

        int error = 0;
        int n = 0;
        for (int i = 0; i < Protein.testSeq.length; i++) {
            n += Protein.testSeq[i].length;
            int[] label = model.predict(Protein.testSeq[i]);
            for (int j = 0; j < Protein.testSeq[i].length; j++) {
                if (Protein.testLabel[i][j] != label[j]) {
                    error++;
                }
            }
        }

        int viterbiError = 0;
        for (int i = 0; i < Protein.testSeq.length; i++) {
            n += Protein.testSeq[i].length;
            int[] label = model.viterbi(Protein.testSeq[i]);
            for (int j = 0; j < Protein.testSeq[i].length; j++) {
                if (Protein.testLabel[i][j] != label[j]) {
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
    public void testHyphen() {
        System.out.println("hyphen");

        CRF model = CRF.fit(Hyphen.seq, Hyphen.label, 100, 20, 100, 5, 0.3);

        int error = 0;
        int n = 0;
        for (int i = 0; i < Hyphen.testSeq.length; i++) {
            n += Hyphen.testSeq[i].length;
            int[] label = model.predict(Hyphen.testSeq[i]);
            for (int j = 0; j < Hyphen.testSeq[i].length; j++) {
                if (Hyphen.testLabel[i][j] != label[j]) {
                    error++;
                }
            }
        }

        int viterbiError = 0;
        for (int i = 0; i < Hyphen.testSeq.length; i++) {
            n += Hyphen.testSeq[i].length;
            int[] label = model.viterbi(Hyphen.testSeq[i]);
            for (int j = 0; j < Hyphen.testSeq[i].length; j++) {
                if (Hyphen.testLabel[i][j] != label[j]) {
                    viterbiError++;
                }
            }
        }

        System.out.format("Hypen error (forward-backward) is %d of %d%n", error, n);
        System.out.format("Hypen error (forward-backward) rate = %.2f%%%n", 100.0 * error / n);
        System.out.format("Hypen error (Viterbi) is %d of %d%n", viterbiError, n);
        System.out.format("Hypen error (Viterbi) rate = %.2f%%%n", 100.0 * viterbiError / n);
        assertEquals(470, error);
        assertEquals(508, viterbiError);
    }
}