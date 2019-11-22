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

package smile.validation;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import smile.classification.DecisionTree;
import smile.data.CPU;
import smile.data.Iris;
import smile.math.MathEx;
import smile.regression.RegressionTree;

/**
 *
 * @author Haifeng Li
 */
public class BootstrapTest {

    public BootstrapTest() {
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
    public void testComplete() {
        System.out.println("Complete");
        int n = 57;
        int k = 100;
        Bootstrap instance = new Bootstrap(n, k);
        boolean[] hit = new boolean[n];
        for (int i = 0; i < k; i++) {
            for (int j = 0; j < n; j++) {
                hit[j] = false;
            }

            int[] train = instance.train[i];
            for (int j = 0; j < train.length; j++) {
                hit[train[j]] = true;
            }

            int[] test = instance.test[i];
            for (int j = 0; j < test.length; j++) {
                assertFalse(hit[test[j]]);
                hit[test[j]] = true;
            }

            for (int j = 0; j < n; j++) {
                assertTrue(hit[j]);
            }
        }
    }

    /**
     * Test the coverage of samples, of class CrossValidation.
     */
    @Test
    public void testOrthogonal() {
        System.out.println("Coverage");
        int n = 57;
        int k = 100;
        Bootstrap instance = new Bootstrap(n, k);
        int[] trainhit = new int[n];
        int[] testhit = new int[n];
        for (int i = 0; i < k; i++) {
            int[] train = instance.train[i];
            for (int j = 0; j < train.length; j++) {
                trainhit[train[j]]++;
            }

            int[] test = instance.test[i];
            for (int j = 0; j < test.length; j++) {
                testhit[test[j]]++;
            }
        }

        System.out.format("Train coverage: %d\t%d\t%d%n", MathEx.min(trainhit), MathEx.median(trainhit), MathEx.max(trainhit));
        System.out.format("Test coverage: %d\t%d\t%d%n", MathEx.min(testhit), MathEx.median(testhit), MathEx.max(testhit));

        for (int j = 0; j < n; j++) {
            assertTrue(trainhit[j] > 60);
            assertTrue(testhit[j] > 20);
        }
    }

    @Test
    public void testIris() {
        System.out.println("Iris");

        double[] error = Bootstrap.classification(100, Iris.formula, Iris.data, (f, x) -> DecisionTree.fit(f, x));

        System.out.println("100-fold bootstrap error rate average = " + MathEx.mean(error));
        System.out.println("100-fold bootstrap error rate std.dev = " + MathEx.sd(error));
    }

    @Test
    public void testCPU() {
        System.out.println("CPU");

        double[] rmse = Bootstrap.regression(100, CPU.formula, CPU.data, (f, x) -> RegressionTree.fit(f, x));

        System.out.println("100-fold bootstrap RMSE average = " + MathEx.mean(rmse));
        System.out.println("100-fold bootstrap RMSE std.dev = " + MathEx.sd(rmse));
    }
}