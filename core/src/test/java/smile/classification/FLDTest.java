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
import smile.data.BreastCancer;
import smile.data.Iris;
import smile.data.PenDigits;
import smile.data.USPS;
import smile.math.MathEx;
import smile.util.Paths;
import smile.validation.CrossValidation;
import smile.validation.Error;
import smile.validation.LOOCV;
import smile.validation.Validation;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Arrays;

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

        int[] prediction = LOOCV.classification(Iris.x, Iris.y, (x, y) -> FLD.fit(x, y));
        int error = Error.of(Iris.y, prediction);
        System.out.println("Error = " + error);
        assertEquals(5, error);
    }

    @Test
    public void testPenDigits() {
        System.out.println("Pen Digits");

        MathEx.setSeed(19650218); // to get repeatable results.
        int[] prediction = CrossValidation.classification(10, PenDigits.x, PenDigits.y, (x, y) -> FLD.fit(x, y));
        int error = Error.of(PenDigits.y, prediction);

        System.out.println("Error = " + error);
        assertEquals(1502, error);
    }

    @Test
    public void testBreastCancer() {
        System.out.println("Breast Cancer");

        MathEx.setSeed(19650218); // to get repeatable results.
        int[] prediction = CrossValidation.classification(10, BreastCancer.x, BreastCancer.y, (x, y) -> FLD.fit(x, y));
        int error = Error.of(BreastCancer.y, prediction);

        System.out.println("Error = " + error);
        assertEquals(64, error);
    }

    @Test(expected = Test.None.class)
    public void testUSPS() throws Exception {
        System.out.println("USPS");

        FLD model = FLD.fit(USPS.x, USPS.y);

        int[] prediction = Validation.test(model, USPS.testx);
        int error = Error.of(USPS.testy, prediction);

        System.out.println("Error = " + error);
        assertEquals(561, error);

        java.nio.file.Path temp = smile.data.Serialize.write(model);
        smile.data.Serialize.read(temp);
    }

    @Test(expected = Test.None.class)
    public void testColon() throws IOException {
        System.out.println("Colon");

        BufferedReader reader = Paths.getTestDataReader("microarray/colon.txt");
        int[] y = Arrays.stream(reader.readLine().split(" ")).mapToInt(s -> Integer.valueOf(s) > 0 ? 1 : 0).toArray();

        double[][] x = new double[62][2000];
        for (int i = 0; i < 2000; i++) {
            String[] tokens = reader.readLine().split(" ");
            for (int j = 0; j < 62; j++) {
                x[j][i] = Double.valueOf(tokens[j]);
            }
        }

        MathEx.setSeed(19650218); // to get repeatable results.
        int[] prediction = CrossValidation.classification(5, x, y, (xi, yi) -> FLD.fit(xi, yi));
        int error = Error.of(y, prediction);
        System.out.println("Error = " + error);
        assertEquals(9, error);
    }
}