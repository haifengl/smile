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

package smile.feature;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import smile.data.Iris;

/**
 *
 * @author Haifeng Li
 */
public class SignalNoiseRatioTest {
    
    public SignalNoiseRatioTest() {
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
        System.out.println("SignalNoiseRatio");
        int[] y = new int[Iris.data.size()];
            
        for (int i = 0; i < y.length; i++) {
            if (Iris.y[i] < 2) y[i] = 0;
            else y[i] = 1;
        }

        double[] ratio = SignalNoiseRatio.of(Iris.x, y);
        assertEquals(4, ratio.length);
        assertEquals(0.8743107, ratio[0], 1E-7);
        assertEquals(0.1502717, ratio[1], 1E-7);
        assertEquals(1.3446912, ratio[2], 1E-7);
        assertEquals(1.4757334, ratio[3], 1E-7);
    }
}
