/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.feature.selection;

import smile.datasets.Abalone;
import org.junit.jupiter.api.*;
import smile.math.MathEx;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class FRegressionTest {

    public FRegressionTest() {
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
    public void testAbalone() throws Exception {
        System.out.println("Abalone");
        MathEx.setSeed(19650218); // to get repeatable results.

        var abalone = new Abalone();
        FRegression[] f = FRegression.fit(abalone.train(), "rings");
        assertEquals(8, f.length);
        assertEquals(382.6936, f[0].statistic(), 1E-4);
        assertEquals(1415.9152, f[1].statistic(), 1E-4);
        assertEquals(1569.9083, f[2].statistic(), 1E-4);
        assertEquals(2104.4944, f[7].statistic(), 1E-4);
    }
}
