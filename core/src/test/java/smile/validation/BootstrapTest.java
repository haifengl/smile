/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.validation;

import smile.classification.DecisionTree;
import smile.test.data.CPU;
import smile.test.data.Iris;
import smile.math.MathEx;
import smile.regression.RegressionTree;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class BootstrapTest {

    public BootstrapTest() {
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
    public void testComplete() {
        System.out.println("Complete");
        int n = 57;
        int k = 100;
        Bag[] bags = Bootstrap.of(n, k);
        boolean[] hit = new boolean[n];
        for (int i = 0; i < k; i++) {
            for (int j = 0; j < n; j++) {
                hit[j] = false;
            }

            int[] train = bags[i].samples;
            for (int j = 0; j < train.length; j++) {
                hit[train[j]] = true;
            }

            int[] test = bags[i].oob;
            for (int j = 0; j < test.length; j++) {
                assertFalse(hit[test[j]]);
                hit[test[j]] = true;
            }

            for (int j = 0; j < n; j++) {
                assertTrue(hit[j]);
            }
        }
    }

    @Test
    public void testOrthogonal() {
        System.out.println("Coverage");
        int n = 57;
        int k = 100;
        Bag[] bags = Bootstrap.of(n, k);
        int[] trainhit = new int[n];
        int[] testhit = new int[n];
        for (int i = 0; i < k; i++) {
            int[] train = bags[i].samples;
            for (int j = 0; j < train.length; j++) {
                trainhit[train[j]]++;
            }

            int[] test = bags[i].oob;
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
    public void testStratifiedComplete() {
        System.out.println("Stratified complete");
        int n = 57;
        int k = 100;

        int[] stratum = new int[n];
        for (int i = 0; i < n; i++) {
            stratum[i] = MathEx.randomInt(3);
        }

        Bag[] bags = Bootstrap.of(stratum, k);
        boolean[] hit = new boolean[n];
        for (int i = 0; i < k; i++) {
            for (int j = 0; j < n; j++) {
                hit[j] = false;
            }

            int[] train = bags[i].samples;
            for (int j = 0; j < train.length; j++) {
                hit[train[j]] = true;
            }

            int[] test = bags[i].oob;
            for (int j = 0; j < test.length; j++) {
                assertFalse(hit[test[j]]);
                hit[test[j]] = true;
            }

            for (int j = 0; j < n; j++) {
                assertTrue(hit[j]);
            }
        }
    }

    @Test
    public void testStratifiedOrthogonal() {
        System.out.println("Stratified coverage");
        int n = 57;
        int k = 100;

        int[] stratum = new int[n];
        for (int i = 0; i < n; i++) {
            stratum[i] = MathEx.randomInt(3);
        }

        Bag[] bags = Bootstrap.of(stratum, k);
        int[] trainhit = new int[n];
        int[] testhit = new int[n];
        for (int i = 0; i < k; i++) {
            int[] train = bags[i].samples;
            for (int j = 0; j < train.length; j++) {
                trainhit[train[j]]++;
            }

            int[] test = bags[i].oob;
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

        ClassificationValidations<DecisionTree> result = Bootstrap.classification(100, Iris.formula, Iris.data, (f, x) -> DecisionTree.fit(f, x));

        System.out.println("100-fold bootstrap accuracy average = " + result.avg.accuracy);
        System.out.println("100-fold bootstrap accuracy std.dev = " + result.sd.accuracy);
    }

    @Test
    public void testCPU() {
        System.out.println("CPU");

        RegressionValidations<RegressionTree> result = Bootstrap.regression(100, CPU.formula, CPU.data, (f, x) -> RegressionTree.fit(f, x));

        System.out.println("100-fold bootstrap RMSE average = " + result.avg.rmse);
        System.out.println("100-fold bootstrap RMSE std.dev = " + result.sd.rmse);
    }
}