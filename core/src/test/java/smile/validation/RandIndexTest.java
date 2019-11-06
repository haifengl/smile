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

    @Test
    public void test() {
        System.out.println("rand index");
        int[] clusters = {2, 3, 3, 1, 1, 3, 3, 1, 3, 1, 1, 3, 3, 3, 3, 3, 2, 3, 3, 1, 1, 1, 1, 1, 1, 4, 1, 3, 3, 3, 3, 3, 1, 4, 4, 4, 3, 1, 1, 3, 1, 4, 3, 3, 3, 3, 1, 1, 3, 1, 1, 3, 3, 3, 3, 4, 3, 1, 3, 1, 3, 1, 1, 1, 1, 1, 3, 3, 2, 3, 3, 1, 1, 3, 3, 3, 3, 3, 3, 1, 1, 3, 2, 3, 2, 2, 4, 1, 3, 1, 3, 1, 1, 3, 4, 4, 4, 1, 2, 3, 1, 1, 3, 1, 1, 1, 4, 3, 3, 2, 3, 3, 1, 3, 3, 1, 1, 1, 3, 4, 4, 2, 3, 3, 3, 3, 1, 1, 1, 3, 3, 3, 2, 3, 3, 3, 2, 3, 3, 1, 3, 1, 3, 3, 1, 1, 3, 3, 3, 1, 1, 1, 1, 3, 3, 4, 3, 2, 3, 1, 1, 3, 1, 2, 3, 1, 1, 3, 3, 1, 1, 1, 1, 1, 3, 1, 3, 1, 3, 1, 3, 1, 1, 3, 1, 1, 1, 3, 2, 1, 2, 1, 1, 1, 1, 1, 3, 1, 1, 3, 3, 1, 3, 3, 3};
        int[] alt      = {3, 2, 2, 0, 0, 2, 2, 0, 2, 0, 0, 2, 2, 2, 2, 2, 3, 2, 2, 0, 0, 0, 0, 0, 0, 3, 0, 2, 2, 2, 2, 2, 0, 3, 3, 3, 2, 0, 0, 2, 0, 3, 2, 2, 2, 2, 0, 0, 2, 0, 0, 2, 2, 2, 2, 3, 2, 0, 2, 0, 2, 0, 0, 0, 0, 0, 2, 2, 3, 2, 2, 0, 0, 2, 2, 2, 2, 2, 2, 0, 0, 2, 3, 2, 0, 3, 3, 0, 2, 0, 2, 0, 0, 2, 3, 3, 3, 0, 3, 2, 0, 0, 2, 0, 0, 0, 3, 2, 2, 3, 2, 2, 0, 2, 2, 0, 0, 0, 2, 3, 3, 3, 2, 2, 2, 2, 0, 0, 0, 2, 2, 2, 3, 2, 2, 2, 2, 2, 2, 0, 2, 0, 2, 2, 0, 0, 2, 1, 2, 0, 0, 0, 0, 2, 2, 3, 2, 1, 2, 0, 0, 2, 0, 3, 2, 0, 0, 2, 2, 0, 0, 0, 0, 0, 2, 0, 2, 0, 2, 0, 0, 0, 0, 2, 0, 0, 0, 2, 3, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 2, 2, 0, 2, 2, 2};
        RandIndex instance = new RandIndex();
        double expResult = 0.9651;
        double result = instance.measure(clusters, alt);
        assertEquals(expResult, result, 1E-4);
    }

}