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

package smile.feature.selection;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import smile.data.DataFrame;
import smile.data.vector.IntVector;
import smile.test.data.BreastCancer;
import smile.test.data.Default;
import smile.test.data.Iris;

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
    public void testIris() {
        System.out.println("Iris");

        int[] y = new int[Iris.data.size()];
        for (int i = 0; i < y.length; i++) {
            if (Iris.y[i] < 2) y[i] = 0;
            else y[i] = 1;
        }

        DataFrame data = Iris.data.drop("class").merge(IntVector.of("y", y));
        SignalNoiseRatio[] s2n = SignalNoiseRatio.fit(data, "y");
        assertEquals(4, s2n.length);
        assertEquals(0.8743107, s2n[0].s2n, 1E-7);
        assertEquals(0.1502717, s2n[1].s2n, 1E-7);
        assertEquals(1.3446912, s2n[2].s2n, 1E-7);
        assertEquals(1.4757334, s2n[3].s2n, 1E-7);
    }

    @Test
    public void testDefault() {
        System.out.println("Default");

        SignalNoiseRatio[] s2n = SignalNoiseRatio.fit(Default.data, "default");
        assertEquals(2, s2n.length);
        assertEquals(1.1832, s2n[0].s2n, 1E-4);
        assertEquals(0.0545, s2n[1].s2n, 1E-4);
    }

    @Test
    public void testBreastCancer() {
        System.out.println("BreastCancer");

        SignalNoiseRatio[] s2n = SignalNoiseRatio.fit(BreastCancer.data, "diagnosis");
        assertEquals(30, s2n.length);
        assertEquals(1.0666, s2n[0].s2n, 1E-4);
        assertEquals(0.4746, s2n[1].s2n, 1E-4);
        assertEquals(1.1078, s2n[2].s2n, 1E-4);
    }
}
