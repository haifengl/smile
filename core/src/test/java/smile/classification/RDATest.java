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

package smile.classification;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.data.BreastCancer;
import smile.data.Iris;
import smile.data.PenDigits;
import smile.data.USPS;
import smile.math.MathEx;
import smile.validation.*;
import smile.validation.metric.Error;

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
            ClassificationMetrics metrics = LOOCV.classification(Iris.x, Iris.y, (x, y) -> RDA.fit(x, y, alpha));

            System.out.format("alpha = %.1f, metrics = %s%n", alpha, metrics);
            assertEquals(1.0 - expected[i]/150.0, metrics.accuracy, 1E-4);
        }
    }

    @Test
    public void testPenDigits() {
        System.out.println("Pen Digits");

        MathEx.setSeed(19650218); // to get repeatable results.
        ClassificationValidations<RDA> result = CrossValidation.classification(10, PenDigits.x, PenDigits.y,
                (x, y) -> RDA.fit(x, y, 0.9));

        System.out.println(result);
        assertEquals(0.9863, result.avg.accuracy, 1E-4);
    }

    @Test
    public void testBreastCancer() {
        System.out.println("Breast Cancer");

        MathEx.setSeed(19650218); // to get repeatable results.
        ClassificationValidations<RDA> result = CrossValidation.classification(10, BreastCancer.x, BreastCancer.y,
                (x, y) -> RDA.fit(x, y, 0.9));

        System.out.println(result);
        assertEquals(0.9461, result.avg.accuracy, 1E-4);
    }

    @Test
    public void testUSPS() throws Exception {
        System.out.println("USPS");

        RDA model = RDA.fit(USPS.x, USPS.y, 0.7);

        int[] prediction = model.predict(USPS.testx);
        int error = Error.of(USPS.testy, prediction);

        System.out.println("Error = " + error);
        assertEquals(235, error);

        java.nio.file.Path temp = smile.data.Serialize.write(model);
        smile.data.Serialize.read(temp);
    }
}
