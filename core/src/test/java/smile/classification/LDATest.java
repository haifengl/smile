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
import smile.data.*;
import smile.math.MathEx;
import smile.validation.*;
import smile.validation.metric.Error;

import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class LDATest {

    public LDATest() {
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

        ClassificationMetrics metrics = LOOCV.classification(Iris.x, Iris.y, LDA::fit);

        System.out.println(metrics);
        assertEquals(0.8533, metrics.accuracy, 1E-4);
    }

    @Test
    public void testPenDigits() {
        System.out.println("Pen Digits");

        MathEx.setSeed(19650218); // to get repeatable results.
        ClassificationValidations<LDA> result = CrossValidation.classification(10, PenDigits.x, PenDigits.y, LDA::fit);

        System.out.println(result);
        assertEquals(0.8820, result.avg.accuracy, 1E-4);
    }

    @Test
    public void testBreastCancer() {
        System.out.println("Breast Cancer");

        MathEx.setSeed(19650218); // to get repeatable results.
        ClassificationValidations<LDA> result = CrossValidation.classification(10, BreastCancer.x, BreastCancer.y, LDA::fit);

        System.out.println(result);
        assertEquals(0.9272, result.avg.accuracy, 1E-4);
    }

    @Test
    public void testUSPS() throws Exception {
        System.out.println("USPS");

        LDA model = LDA.fit(USPS.x, USPS.y);

        int[] prediction = model.predict(USPS.testx);
        int error = Error.of(USPS.testy, prediction);

        System.out.println("Error = " + error);
        assertEquals(256, error);

        java.nio.file.Path temp = smile.data.Serialize.write(model);
        smile.data.Serialize.read(temp);
    }
}