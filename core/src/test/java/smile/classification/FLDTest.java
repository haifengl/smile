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

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Arrays;

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
import smile.util.Paths;
import smile.validation.*;
import smile.validation.metric.Error;

import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class FLDTest {

    public FLDTest() {
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

        ClassificationMetrics metrics = LOOCV.classification(Iris.x, Iris.y, FLD::fit);

        System.out.println(metrics);
        assertEquals(0.98, metrics.accuracy, 1E-4);
    }

    @Test
    public void testPenDigits() {
        System.out.println("Pen Digits");

        MathEx.setSeed(19650218); // to get repeatable results.
        ClassificationValidations<FLD> result = CrossValidation.classification(10, PenDigits.x, PenDigits.y,
                FLD::fit);

        System.out.println(result);
        assertEquals(0.8771, result.avg.accuracy, 1E-4);
    }

    @Test
    public void testBreastCancer() {
        System.out.println("Breast Cancer");

        MathEx.setSeed(19650218); // to get repeatable results.
        ClassificationValidations<FLD> result = CrossValidation.classification(10, BreastCancer.x, BreastCancer.y, FLD::fit);

        System.out.println(result);
        assertEquals(0.9655, result.avg.accuracy, 1E-4);
    }

    @Test
    public void testUSPS() throws Exception {
        System.out.println("USPS");

        ClassificationValidation<FLD> result = ClassificationValidation.of(USPS.x, USPS.y, USPS.testx, USPS.testy, FLD::fit);

        System.out.println(result);
        assertEquals(262, result.metrics.error);

        java.nio.file.Path temp = smile.data.Serialize.write(result.model);
        FLD model = (FLD) smile.data.Serialize.read(temp);

        int error = Error.of(USPS.testy, model.predict(USPS.testx));
        assertEquals(262, error);
    }

    @Test
    public void testColon() throws IOException {
        System.out.println("Colon");

        BufferedReader reader = Paths.getTestDataReader("microarray/colon.txt");
        int[] y = Arrays.stream(reader.readLine().split(" ")).mapToInt(s -> Integer.parseInt(s) > 0 ? 1 : 0).toArray();

        double[][] x = new double[62][2000];
        for (int i = 0; i < 2000; i++) {
            String[] tokens = reader.readLine().split(" ");
            for (int j = 0; j < 62; j++) {
                x[j][i] = Double.parseDouble(tokens[j]);
            }
        }

        MathEx.setSeed(19650218); // to get repeatable results.
        ClassificationValidations<FLD> result = CrossValidation.classification(5, x, y, FLD::fit);

        System.out.println(result);
        assertEquals(0.8524, result.avg.accuracy, 1E-4);
    }
}