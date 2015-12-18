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
public class RandIndexTest {

    public RandIndexTest() {
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
     * Test of measure method, of class RandIndex.
     */
    @Test
    public void testMeasure() {
        System.out.println("measure");
        int[] clusters = {2, 3, 3, 1, 1, 3, 3, 1, 3, 1, 1, 3, 3, 3, 3, 3, 2, 3, 3, 1, 1, 1, 1, 1, 1, 4, 1, 3, 3, 3, 3, 3, 1, 4, 4, 4, 3, 1, 1, 3, 1, 4, 3, 3, 3, 3, 1, 1, 3, 1, 1, 3, 3, 3, 3, 4, 3, 1, 3, 1, 3, 1, 1, 1, 1, 1, 3, 3, 2, 3, 3, 1, 1, 3, 3, 3, 3, 3, 3, 1, 1, 3, 2, 3, 2, 2, 4, 1, 3, 1, 3, 1, 1, 3, 4, 4, 4, 1, 2, 3, 1, 1, 3, 1, 1, 1, 4, 3, 3, 2, 3, 3, 1, 3, 3, 1, 1, 1, 3, 4, 4, 2, 3, 3, 3, 3, 1, 1, 1, 3, 3, 3, 2, 3, 3, 3, 2, 3, 3, 1, 3, 1, 3, 3, 1, 1, 3, 3, 3, 1, 1, 1, 1, 3, 3, 4, 3, 2, 3, 1, 1, 3, 1, 2, 3, 1, 1, 3, 3, 1, 1, 1, 1, 1, 3, 1, 3, 1, 3, 1, 3, 1, 1, 3, 1, 1, 1, 3, 2, 1, 2, 1, 1, 1, 1, 1, 3, 1, 1, 3, 3, 1, 3, 3, 3};
        int[] alt      = {3, 2, 2, 0, 0, 2, 2, 0, 2, 0, 0, 2, 2, 2, 2, 2, 3, 2, 2, 0, 0, 0, 0, 0, 0, 3, 0, 2, 2, 2, 2, 2, 0, 3, 3, 3, 2, 0, 0, 2, 0, 3, 2, 2, 2, 2, 0, 0, 2, 0, 0, 2, 2, 2, 2, 3, 2, 0, 2, 0, 2, 0, 0, 0, 0, 0, 2, 2, 3, 2, 2, 0, 0, 2, 2, 2, 2, 2, 2, 0, 0, 2, 3, 2, 0, 3, 3, 0, 2, 0, 2, 0, 0, 2, 3, 3, 3, 0, 3, 2, 0, 0, 2, 0, 0, 0, 3, 2, 2, 3, 2, 2, 0, 2, 2, 0, 0, 0, 2, 3, 3, 3, 2, 2, 2, 2, 0, 0, 0, 2, 2, 2, 3, 2, 2, 2, 2, 2, 2, 0, 2, 0, 2, 2, 0, 0, 2, 1, 2, 0, 0, 0, 0, 2, 2, 3, 2, 1, 2, 0, 0, 2, 0, 3, 2, 0, 0, 2, 2, 0, 0, 0, 0, 0, 2, 0, 2, 0, 2, 0, 0, 0, 0, 2, 0, 0, 0, 2, 3, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 2, 2, 0, 2, 2, 2};
        RandIndex instance = new RandIndex();
        double expResult = 0.9651;
        double result = instance.measure(clusters, alt);
        assertEquals(expResult, result, 1E-4);
    }

}