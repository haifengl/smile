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

import smile.data.*;
import smile.math.MathEx;
import smile.validation.*;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.validation.Error;

import static org.junit.Assert.*;

/**
 *
 * @author Haifeng
 */
public class AdaBoostTest {
    
    public AdaBoostTest() {
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

    @Test(expected = Test.None.class)
    public void testWeather() throws Exception {
        System.out.println("Weather");

        MathEx.setSeed(19650218); // to get repeatable results.
        AdaBoost model = AdaBoost.fit(WeatherNominal.formula, WeatherNominal.data, 200, 20, 4, 1);

        double[] importance = model.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().fieldName(i), importance[i]);
        }

        int[] prediction = LOOCV.classification(WeatherNominal.formula, WeatherNominal.data, (f, x) -> AdaBoost.fit(f, x, 200, 20, 4, 1));
        int error = Error.of(WeatherNominal.y, prediction);

        System.out.println("Error = " + error);
        assertEquals(6, error);

        java.nio.file.Path temp = smile.data.Serialize.write(model);
        smile.data.Serialize.read(temp);
    }

    @Test
    public void testIris() {
        System.out.println("Iris");

        MathEx.setSeed(19650218); // to get repeatable results.
        AdaBoost model = AdaBoost.fit(Iris.formula, Iris.data, 200, 20, 4, 5);

        double[] importance = model.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().fieldName(i), importance[i]);
        }

        int[] prediction = LOOCV.classification(Iris.formula, Iris.data, (f, x) -> AdaBoost.fit(f, x, 200, 20, 4, 1));
        int error = Error.of(Iris.y, prediction);
        System.out.println("Error = " + error);
        assertEquals(7, error);
    }

    @Test
    public void testPenDigits() {
        System.out.println("Pen Digits");

        MathEx.setSeed(19650218); // to get repeatable results.
        int[] prediction = CrossValidation.classification(10, PenDigits.formula, PenDigits.data, (f, x) -> AdaBoost.fit(f, x, 200, 20, 4, 1));
        int error = Error.of(PenDigits.y, prediction);

        System.out.println("Error = " + error);
        assertEquals(356, error);
    }

    @Test
    public void testBreastCancer() {
        System.out.println("Breast Cancer");

        MathEx.setSeed(19650218); // to get repeatable results.
        int[] prediction = CrossValidation.classification(10, BreastCancer.formula, BreastCancer.data, (f, x) -> AdaBoost.fit(f, x, 200, 20, 4, 1));
        int error = Error.of(BreastCancer.y, prediction);

        System.out.println("Error = " + error);
        assertEquals(19, error);
    }

    @Test
    public void testSegment() {
        System.out.println("Segment");

        MathEx.setSeed(19650218); // to get repeatable results.
        AdaBoost model = AdaBoost.fit(Segment.formula, Segment.train, 200, 20, 6, 1);

        double[] importance = model.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().fieldName(i), importance[i]);
        }

        int[] prediction = Validation.test(model, Segment.test);
        int error = Error.of(Segment.testy, prediction);

        System.out.println("Error = " + error);
        assertEquals(30, error);

        System.out.println("----- Progressive Accuracy -----");
        int[][] test = model.test(Segment.test);
        for (int i = 0; i < test.length; i++) {
            System.out.format("Accuracy with %3d trees: %.4f%n", i + 1, Accuracy.of(Segment.testy, test[i]));
        }
    }

    @Test
    public void testUSPS() {
        System.out.println("USPS");

        MathEx.setSeed(19650218); // to get repeatable results.
        AdaBoost model = AdaBoost.fit(USPS.formula, USPS.train, 200, 20, 64, 1);

        double[] importance = model.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().fieldName(i), importance[i]);
        }

        int[] prediction = Validation.test(model, USPS.test);
        int error = Error.of(USPS.testy, prediction);

        System.out.println("Error = " + error);
        assertEquals(152, error);

        System.out.println("----- Progressive Accuracy -----");
        int[][] test = model.test(USPS.test);
        for (int i = 0; i < test.length; i++) {
            System.out.format("Accuracy with %3d trees: %.4f%n", i+1, Accuracy.of(USPS.testy, test[i]));
        }
    }
}
