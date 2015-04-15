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

package smile.math.distance;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import smile.math.SparseArray;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class EuclideanDistanceTest {

    public EuclideanDistanceTest() {
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
     * Test of distance method, of class EuclideanDistance.
     */
    @Test
    public void testDistance() {
        System.out.println("distance");

        double[] x = {1.0, 2.0, 3.0, 4.0};
        double[] y = {4.0, 3.0, 2.0, 1.0};
        assertEquals(4.472136, new EuclideanDistance().d(x, y), 1E-6);

        double[] w = {-2.1968219, -0.9559913, -0.0431738,  1.0567679,  0.3853515};
        double[] v = {-1.7781325, -0.6659839,  0.9526148, -0.9460919, -0.3925300};
        assertEquals(2.422302, new EuclideanDistance().d(w, v), 1E-6);

        SparseArray s = new SparseArray();
        s.append(1, 1.0);
        s.append(2, 2.0);
        s.append(3, 3.0);
        s.append(4, 4.0);

        SparseArray t = new SparseArray();
        t.append(1, 4.0);
        t.append(2, 3.0);
        t.append(3, 2.0);
        t.append(4, 1.0);

        assertEquals(4.472136, new SparseEuclideanDistance().d(s, t), 1E-6);

        s = new SparseArray();
        s.append(2, 2.0);
        s.append(3, 3.0);
        s.append(4, 4.0);

        t = new SparseArray();
        t.append(1, 4.0);
        t.append(2, 3.0);
        t.append(3, 2.0);

        assertEquals(5.830951, new SparseEuclideanDistance().d(s, t), 1E-6);

        s = new SparseArray();
        s.append(1, 1.0);

        t = new SparseArray();
        t.append(3, 2.0);

        assertEquals(2.236067, new SparseEuclideanDistance().d(s, t), 1E-6);
    }
}