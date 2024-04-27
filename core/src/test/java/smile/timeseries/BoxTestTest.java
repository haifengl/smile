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

package smile.timeseries;

import smile.test.data.BitcoinPrice;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class BoxTestTest {

    public BoxTestTest() {
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
    public void testPierce() {
        System.out.println("Box-Pierce test");

        // The log return series, log(p_t) - log(p_t-1), is stationary.
        double[] x = TimeSeries.diff(BitcoinPrice.logPrice, 1);
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

        // The log return series, log(p_t) - log(p_t-1), is stationary.
        double[] x = TimeSeries.diff(BitcoinPrice.logPrice, 1);
        BoxTest box = BoxTest.ljung(x, 5);
        System.out.println(box);
        assertEquals( BoxTest.Type.Ljung_Box, box.type);
        assertEquals(5, box.df);
        assertEquals(9.0415, box.q, 1E-4);
        assertEquals(0.1074, box.pvalue, 1E-4);
    }
}
