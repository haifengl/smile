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

import static org.junit.Assert.*;
import smile.math.MathEx;
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.Matrix;
import smile.stat.distribution.EmpiricalDistribution;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class HMMTest {

    double[] pi = {0.5, 0.5};
    double[][] a = {{0.8, 0.2}, {0.2, 0.8}};
    double[][] b = {{0.6, 0.4}, {0.4, 0.6}};

    public HMMTest() {
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

    /**
     * Test of getInitialStateProbabilities method, of class HMM.
     */
    @Test
    public void testGetInitialStateProbabilities() {
        System.out.println("getInitialStateProbabilities");
        HMM hmm = new HMM(pi, Matrix.of(a), Matrix.of(b));
        double[] result = hmm.getInitialStateProbabilities();
        for (int i = 0; i < pi.length; i++) {
            assertEquals(pi[i], result[i], 1E-7);
        }
    }

    /**
     * Test of getStateTransitionProbabilities method, of class HMM.
     */
    @Test
    public void testGetStateTransitionProbabilities() {
        System.out.println("getStateTransitionProbabilities");
        HMM hmm = new HMM(pi, Matrix.of(a), Matrix.of(b));
        DenseMatrix result = hmm.getStateTransitionProbabilities();
        for (int i = 0; i < a.length; i++) {
            for (int j = 0; j < a[i].length; j++) {
                assertEquals(a[i][j], result.get(i, j), 1E-7);
            }
        }
    }

    /**
     * Test of getSymbolEmissionProbabilities method, of class HMM.
     */
    @Test
    public void testGetSymbolEmissionProbabilities() {
        System.out.println("getSymbolEmissionProbabilities");
        HMM hmm = new HMM(pi, Matrix.of(a), Matrix.of(b));
        DenseMatrix result = hmm.getSymbolEmissionProbabilities();
        for (int i = 0; i < b.length; i++) {
            for (int j = 0; j < b[i].length; j++) {
                assertEquals(b[i][j], result.get(i, j), 1E-7);
            }
        }
    }

    /**
     * Test of p method, of class HMM.
     */
    @Test
    public void testJointP() {
        System.out.println("joint p");
        int[] o = {0, 0, 1, 1, 0, 1, 1, 0};
        int[] s = {0, 0, 1, 1, 1, 1, 1, 0};
        HMM hmm = new HMM(pi, Matrix.of(a), Matrix.of(b));
        double expResult = 7.33836e-05;
        double result = hmm.p(o, s);
        assertEquals(expResult, result, 1E-10);
    }

    /**
     * Test of logp method, of class HMM.
     */
    @Test
    public void testJointLogp() {
        System.out.println("joint logp");
        HMM hmm = new HMM(pi, Matrix.of(a), Matrix.of(b));
        int[] o = {0, 0, 1, 1, 0, 1, 1, 0};
        int[] s = {0, 0, 1, 1, 1, 1, 1, 0};
        double expResult = -9.51981;
        double result = hmm.logp(o, s);
        assertEquals(expResult, result, 1E-5);
    }

    /**
     * Test of p method, of class HMM.
     */
    @Test
    public void testP() {
        System.out.println("p");
        HMM hmm = new HMM(pi, Matrix.of(a), Matrix.of(b));
        int[] o = {0, 0, 1, 1, 0, 1, 1, 0};
        double expResult = 0.003663364;
        double result = hmm.p(o);
        assertEquals(expResult, result, 1E-9);
    }

    /**
     * Test of logp method, of class HMM.
     */
    @Test
    public void testLogp() {
        System.out.println("logp");
        HMM hmm = new HMM(pi, Matrix.of(a), Matrix.of(b));
        int[] o = {0, 0, 1, 1, 0, 1, 1, 0};
        double expResult = -5.609373;
        double result = hmm.logp(o);
        assertEquals(expResult, result, 1E-6);
    }

    /**
     * Test of predict method, of class HMM.
     */
    @Test
    public void testPredict() {
        System.out.println("predict");
        HMM hmm = new HMM(pi, Matrix.of(a), Matrix.of(b));
        int[] o = {0, 0, 1, 1, 0, 1, 1, 0};
        int[] s = {0, 0, 0, 0, 0, 0, 0, 0};
        int[] result = hmm.predict(o);
        assertEquals(o.length, result.length);
        for (int i = 0; i < s.length; i++) {
            assertEquals(s[i], result[i]);
        }
    }

    /**
     * Test of fit method, of class HMM.
     */
    @Test
    public void testFit() {
        System.out.println("fit");
        MathEx.setSeed(19650218); // to get repeatable results.

        EmpiricalDistribution initial = new EmpiricalDistribution(pi);

        EmpiricalDistribution[] transition = new EmpiricalDistribution[a.length];
        for (int i = 0; i < transition.length; i++) {
            transition[i] = new EmpiricalDistribution(a[i]);
        }

        EmpiricalDistribution[] emission = new EmpiricalDistribution[b.length];
        for (int i = 0; i < emission.length; i++) {
            emission[i] = new EmpiricalDistribution(b[i]);
        }

        int[][] sequences = new int[5000][];
        int[][] labels = new int[5000][];
        for (int i = 0; i < sequences.length; i++) {
            sequences[i] = new int[30 * (MathEx.randomInt(5) + 1)];
            labels[i] = new int[sequences[i].length];
            int state = (int) initial.rand();
            sequences[i][0] = (int) emission[state].rand();
            labels[i][0] = state;
            for (int j = 1; j < sequences[i].length; j++) {
                state = (int) transition[state].rand();
                sequences[i][j] = (int) emission[state].rand();
                labels[i][j] = state;
            }
        }

        HMM model = HMM.fit(sequences, labels);
        System.out.println(model);

        double[] expPi2 = {0.5076, 0.4924};
        double[][] expA2 = {{0.8002, 0.1998}, {0.1987, 0.8013}};
        double[][] expB2 = {{0.5998, 0.4002}, {0.4003, 0.5997}};

        double[] pi2 = model.getInitialStateProbabilities();
        for (int i = 0; i < pi.length; i++) {
            assertEquals(expPi2[i], pi2[i], 1E-4);
        }

        DenseMatrix a2 = model.getStateTransitionProbabilities();
        for (int i = 0; i < a.length; i++) {
            for (int j = 0; j < a[i].length; j++) {
                assertEquals(expA2[i][j], a2.get(i, j), 1E-4);
            }
        }

        DenseMatrix b2 = model.getSymbolEmissionProbabilities();
        for (int i = 0; i < b.length; i++) {
            for (int j = 0; j < b[i].length; j++) {
                assertEquals(expB2[i][j], b2.get(i, j), 1E-4);
            }
        }
    }

    /**
     * Test of update method, of class HMM.
     */
    @Test
    public void testUpdate() {
        System.out.println("update");
        MathEx.setSeed(19650218); // to get repeatable results.

        EmpiricalDistribution initial = new EmpiricalDistribution(pi);

        EmpiricalDistribution[] transition = new EmpiricalDistribution[a.length];
        for (int i = 0; i < transition.length; i++) {
            transition[i] = new EmpiricalDistribution(a[i]);
        }

        EmpiricalDistribution[] emission = new EmpiricalDistribution[b.length];
        for (int i = 0; i < emission.length; i++) {
            emission[i] = new EmpiricalDistribution(b[i]);
        }

        int[][] sequences = new int[5000][];
        int[][] labels = new int[5000][];
        for (int i = 0; i < sequences.length; i++) {
            sequences[i] = new int[30 * (MathEx.randomInt(5) + 1)];
            labels[i] = new int[sequences[i].length];
            int state = (int) initial.rand();
            sequences[i][0] = (int) emission[state].rand();
            labels[i][0] = state;
            for (int j = 1; j < sequences[i].length; j++) {
                state = (int) transition[state].rand();
                sequences[i][j] = (int) emission[state].rand();
                labels[i][j] = state;
            }
        }

        double[] expPi2 = {0.47245901561967496, 0.527540984380325};
        double[][] expA2 = {{0.8006, 0.1994}, {0.1986, 0.8014}};
        double[][] expB2 = {{0.6008, 0.3992}, {0.3997, 0.6003}};
        HMM model = new HMM(pi, Matrix.of(a), Matrix.of(b));
        model.update(sequences, 100);
        System.out.println(model);

        double[] pi2 = model.getInitialStateProbabilities();
        for (int i = 0; i < pi.length; i++) {
            assertEquals(expPi2[i], pi2[i], 1E-4);
        }

        DenseMatrix a2 = model.getStateTransitionProbabilities();
        for (int i = 0; i < a.length; i++) {
            for (int j = 0; j < a[i].length; j++) {
                assertEquals(expA2[i][j], a2.get(i, j), 1E-4);
            }
        }

        DenseMatrix b2 = model.getSymbolEmissionProbabilities();
        for (int i = 0; i < b.length; i++) {
            for (int j = 0; j < b[i].length; j++) {
                assertEquals(expB2[i][j], b2.get(i, j), 1E-4);
            }
        }
    }
}