/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.timeseries;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import smile.data.BitcoinPrice;

/**
 *
 * @author Haifeng Li
 */
public class BoxTestTest {

    public BoxTestTest() {
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
    public void testPierce() {
        System.out.println("Box-Pierce test");

        double[] x = BitcoinPrice.logReturn;
        BoxTest box = BoxTest.pierce(x, 5);
        System.out.println(box);
        assertEquals( BoxTest.Type.Box_Pierce, box.type);
        assertEquals(5, box.df);
        assertEquals(9.0098, box.q, 1E-4);
        assertEquals(0.1087, box.pvalue, 1E-4);
    }

    @Test
    public void testLjung() {
        System.out.println("Ljung-Box test");

        double[] x = BitcoinPrice.logReturn;
        BoxTest box = BoxTest.ljung(x, 5);
        System.out.println(box);
        assertEquals( BoxTest.Type.Ljung_Box, box.type);
        assertEquals(5, box.df);
        assertEquals(9.0415, box.q, 1E-4);
        assertEquals(0.1074, box.pvalue, 1E-4);
    }
}
