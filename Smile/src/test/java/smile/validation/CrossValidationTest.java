/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile.validation;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class CrossValidationTest {

    public CrossValidationTest() {
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
     * Test if the train and test dataset are complete, of class CrossValidation.
     */
    @Test
    public void testComplete() {
        System.out.println("Complete");
        int n = 57;
        int k = 5;
        CrossValidation instance = new CrossValidation(n, k);
        boolean[] hit = new boolean[n];
        for (int i = 0; i < k; i++) {
            for (int j = 0; j < n; j++) {
                hit[j] = false;
            }

            int[] train = instance.train[i];
            for (int j = 0; j < train.length; j++) {
                assertFalse(hit[train[j]]);
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
     * Test if different dataset are different, of class CrossValidation.
     */
    @Test
    public void testOrthogonal() {
        System.out.println("Orthogonal");
        int n = 57;
        int k = 5;
        CrossValidation instance = new CrossValidation(n, k);
        boolean[] hit = new boolean[n];
        for (int i = 0; i < k; i++) {
            int[] test = instance.test[i];
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