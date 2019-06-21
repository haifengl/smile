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

package smile.wavelet;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng
 */
public class DaubechiesWaveletTest {

    public DaubechiesWaveletTest() {
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
     * Test of filter method, of class DaubechiesWavelet.
     */
    @Test
    public void testFilter() {
        System.out.println("filter");
        for (int p = 4; p <= 20; p += 2) {
            System.out.format("p = %d%n", p);
            double[] a = {
                    .2, -.4, -.6, -.5, -.8, -.4, -.9, 0, -.2, .1, -.1, .1, .7, .9, 0, .3,
                    .2, -.4, -.6, -.5, -.8, -.4, -.9, 0, -.2, .1, -.1, .1, .7, .9, 0, .3
            };
            double[] b = a.clone();
            Wavelet instance = new DaubechiesWavelet(p);
            instance.transform(a);
            instance.inverse(a);
            for (int i = 0; i < a.length; i++) {
                assertEquals(b[i], a[i], 1E-7);
            }
        }
    }

}