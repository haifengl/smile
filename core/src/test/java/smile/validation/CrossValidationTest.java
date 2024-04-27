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

import smile.math.MathEx;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class CrossValidationTest {

    public CrossValidationTest() {
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
        int k = 5;
        Bag[] bags = CrossValidation.of(n, k);
        boolean[] hit = new boolean[n];
        for (int i = 0; i < k; i++) {
            for (int j = 0; j < n; j++) {
                hit[j] = false;
            }

            int[] train = bags[i].samples;
            for (int j = 0; j < train.length; j++) {
                assertFalse(hit[train[j]]);
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
        System.out.println("Orthogonal");
        int n = 57;
        int k = 5;
        Bag[] bags = CrossValidation.of(n, k);
        boolean[] hit = new boolean[n];
        for (int i = 0; i < k; i++) {
            int[] test = bags[i].oob;
            for (int j = 0; j < test.length; j++) {
                assertFalse(hit[test[j]]);
                hit[test[j]] = true;
            }
        }

        for (int j = 0; j < n; j++) {
            assertTrue(hit[j]);
        }
    }

    @Test
    public void testStratifiedComplete() {
        System.out.println("Stratified complete");
        int n = 57;
        int k = 5;

        int[] label = new int[n];
        for (int i = 0; i < n; i++) {
            label[i] = MathEx.randomInt(3);
        }

        Bag[] bags = CrossValidation.stratify(label, k);
        boolean[] hit = new boolean[n];
        for (int i = 0; i < k; i++) {
            for (int j = 0; j < n; j++) {
                hit[j] = false;
            }

            int[] train = bags[i].samples;
            for (int j = 0; j < train.length; j++) {
                assertFalse(hit[train[j]]);
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
        System.out.println("Stratified orthogonal");
        int n = 57;
        int k = 5;

        int[] label = new int[n];
        for (int i = 0; i < n; i++) {
            label[i] = MathEx.randomInt(3);
        }

        Bag[] bags = CrossValidation.stratify(label, k);
        boolean[] hit = new boolean[n];
        for (int i = 0; i < k; i++) {
            int[] test = bags[i].oob;
            for (int j = 0; j < test.length; j++) {
                assertFalse(hit[test[j]]);
                hit[test[j]] = true;
            }
        }

        for (int j = 0; j < n; j++) {
            assertTrue(hit[j]);
        }
    }
}
