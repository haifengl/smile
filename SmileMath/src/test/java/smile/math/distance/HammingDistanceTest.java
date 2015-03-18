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

import java.util.BitSet;
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
public class HammingDistanceTest {

    public HammingDistanceTest() {
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
     * Test of distance method, of class HammingDistance.
     */
    @Test
    public void testDistance() {
        System.out.println("distance");
        int x = 0x5D;
        int y = 0x49;
        assertEquals(2, HammingDistance.d(x, y));
    }

    /**
     * Test of distance method, of class HammingDistance.
     */
    @Test
    public void testDistanceArray() {
        System.out.println("distance");
        byte[] x = {1, 0, 1, 1, 1, 0, 1};
        byte[] y = {1, 0, 0, 1, 0, 0, 1};
        assertEquals(2, HammingDistance.d(x, y));
    }

    /**
     * Test of distance method, of class HammingDistance.
     */
    @Test
    public void testDistanceBitSet() {
        System.out.println("distance");

        BitSet x = new BitSet();
        x.set(1);
        x.set(3);
        x.set(4);
        x.set(5);
        x.set(7);

        BitSet y = new BitSet();
        y.set(1);
        y.set(4);
        y.set(7);

        assertEquals(2, HammingDistance.d(x, y), 1E-9);
    }
}