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

package smile.projection;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import smile.math.Math;
import smile.math.matrix.Matrix;

/**
 *
 * @author Haifeng Li
 */
public class RandomProjectionTest {

    public RandomProjectionTest() {
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
     * Test of getProjection method, of class RandomProjection.
     */
    @Test
    public void testRandomProjection() {
        System.out.println("getProjection");
        RandomProjection instance = new RandomProjection(128, 40);

        double[][] p = instance.getProjection();
        double[][] t = Math.aatmm(p);

        for (int i = 0; i < t.length; i++) {
            for (int j = 0; j < t[i].length; j++) {
                System.out.format("% .4f ", t[i][j]);
            }
            System.out.println();
        }

        assertTrue(Math.equals(Matrix.eye(40).array(), t, 1E-10));
    }
}