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
public class QRTest {

    double[][] A = {
        {0.9000, 0.4000, 0.7000},
        {0.4000, 0.5000, 0.3000},
        {0.7000, 0.3000, 0.8000}
    };
    double[] b = {0.5, 0.5, 0.5};
    double[] x = {-0.2027027, 0.8783784, 0.4729730};
    double[][] B = {
        {0.5, 0.2},
        {0.5, 0.8},
        {0.5, 0.3}
    };
    double[][] X = {
        {-0.2027027, -1.2837838},
        {0.8783784, 2.2297297},
        {0.4729730, 0.6621622}
    };

    public QRTest() {
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
     * Test of solve method, of class QRDecomposition.
     */
    @Test
    public void testSolve() {
        System.out.println("solve");
        NLMatrix a = new NLMatrix(A);
        QR result = a.qr();
        result.solve(b.clone(), b);
        for (int i = 0; i < x.length; i++) {
            assertEquals(x[i], b[i], 1E-7);
        }
    }

    /**
     * Test of solve method, of class QRDecomposition.
     */
    @Test
    public void testSolveMatrix() {
        System.out.println("solve");
        NLMatrix a = new NLMatrix(A);
        QR result = a.qr();
        NLMatrix b = new NLMatrix(B);
        result.solve(b);
        for (int i = 0; i < X.length; i++) {
            for (int j = 0; j < X[i].length; j++) {
                assertEquals(X[i][j], b.get(i, j), 1E-7);
            }
        }
    }
}