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

package smile.classification;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.data.Iris;
import smile.data.USPS;
import smile.validation.Error;
import smile.validation.LOOCV;
import smile.validation.Validation;

import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class RDATest {

    public RDATest() {
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

        int[] expected = {22, 24, 20, 19, 16, 12, 11, 9, 6, 3, 4};
        for (int i = 0; i <= 10; i++) {
            double alpha = i * 0.1;
            int[] prediction = LOOCV.classification(Iris.x, Iris.y, (x, y) -> RDA.fit(x, y, alpha));
            int error = Error.apply(Iris.y, prediction);
            System.out.format("alpha = %.1f, error = %d%n", alpha, error);
            assertEquals(expected[i], error);
        }
    }

    @Test
    public void testUSPS() {
        System.out.println("USPS");

        RDA model = RDA.fit(USPS.x, USPS.y, 0.7);

        int[] prediction = Validation.test(model, USPS.testx);
        int error = Error.apply(USPS.testy, prediction);

        System.out.println("Error = " + error);
        assertEquals(235, error);
    }
}
