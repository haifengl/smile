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

package smile.netlib;

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
public class LUTest {
    double[][] A = {
        {0.9000, 0.4000, 0.7000},
        {0.4000, 0.5000, 0.3000},
        {0.7000, 0.3000, 0.8000}
    };
    double[] B = {0.5, 0.5, 0.5};
    double[] X = {-0.2027027, 0.8783784, 0.4729730};
    double[][] B2 = {
        {0.5, 0.2},
        {0.5, 0.8},
        {0.5, 0.3}
    };
    double[][] X2 = {
        {-0.2027027, -1.2837838},
        {0.8783784, 2.2297297},
        {0.4729730, 0.6621622}
    };

    public LUTest() {
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
     * Test of solve method, of class LUDecomposition.
     */
    @Test
    public void testSolve() {
        System.out.println("solve");
        NLMatrix a = new NLMatrix(A);
        LU result = a.lu();
        double[] x = B.clone();
        result.solve(x);
        assertEquals(X.length, x.length);
        for (int i = 0; i < X.length; i++) {
            assertEquals(X[i], x[i], 1E-7);
        }
    }

    /**
     * Test of solve method, of class LUDecomposition.
     */
    @Test
    public void testSolveMatrix() {
        System.out.println("solve");
        NLMatrix a = new NLMatrix(A);
        LU result = a.lu();
        NLMatrix x = new NLMatrix(B2);
        result.solve(x);
        assertEquals(X2.length, x.nrows());
        assertEquals(X2[0].length, x.ncols());
        for (int i = 0; i < X2.length; i++) {
            for (int j = 0; j < X2[i].length; j++) {
                assertEquals(X2[i][j], x.get(i, j), 1E-7);
            }
        }
    }
}