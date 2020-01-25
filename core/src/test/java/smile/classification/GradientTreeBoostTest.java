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

import static org.junit.Assert.assertEquals;

/**
 *
 * @author Haifeng Li
 */
public class GradientTreeBoostTest {
    
    public GradientTreeBoostTest() {
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
        GradientTreeBoost model = GradientTreeBoost.fit(WeatherNominal.formula, WeatherNominal.data, 100, 20, 6, 5, 0.05, 0.7);

        double[] importance = model.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().fieldName(i), importance[i]);
        }

        int[] prediction = LOOCV.classification(WeatherNominal.formula, WeatherNominal.data, (f, x) -> GradientTreeBoost.fit(f, x, 100, 20, 6, 5, 0.05, 0.7));
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
        GradientTreeBoost model = GradientTreeBoost.fit(Iris.formula, Iris.data, 100, 20, 6, 5, 0.05, 0.7);

        double[] importance = model.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().fieldName(i), importance[i]);
        }

        int[] prediction = LOOCV.classification(Iris.formula, Iris.data, (f, x) -> GradientTreeBoost.fit(f, x, 100, 20, 6, 5, 0.05, 0.7));
        int error = Error.of(Iris.y, prediction);
        System.out.println("Error = " + error);
        assertEquals(8, error);
    }

    @Test
    public void testPenDigits() {
        System.out.println("Pen Digits");

        MathEx.setSeed(19650218); // to get repeatable results.
        int[] prediction = CrossValidation.classification(10, PenDigits.formula, PenDigits.data, (f, x) -> GradientTreeBoost.fit(f, x, 100, 20, 6, 5, 0.05, 0.7));
        int error = Error.of(PenDigits.y, prediction);

        System.out.println("Error = " + error);
        assertEquals(143, error);
    }

    @Test
    public void testBreastCancer() {
        System.out.println("Breast Cancer");

        MathEx.setSeed(19650218); // to get repeatable results.
        int[] prediction = CrossValidation.classification(10, BreastCancer.formula, BreastCancer.data, (f, x) -> GradientTreeBoost.fit(f, x, 100, 20, 6, 5, 0.05, 0.7));
        int error = Error.of(BreastCancer.y, prediction);

        System.out.println("Error = " + error);
        assertEquals(21, error);
    }

    @Test
    public void testSegment() {
        System.out.println("Segment");

        MathEx.setSeed(19650218); // to get repeatable results.
        GradientTreeBoost model = GradientTreeBoost.fit(Segment.formula, Segment.train, 100, 20, 6, 5, 0.05, 0.7);

        double[] importance = model.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().fieldName(i), importance[i]);
        }

        int[] prediction = Validation.test(model, Segment.test);
        int error = Error.of(Segment.testy, prediction);

        System.out.println("Error = " + error);
        assertEquals(20, error);

        System.out.println("----- Progressive Accuracy -----");
        int[][] test = model.test(Segment.test);
        for (int i = 0; i < test.length; i++) {
            System.out.format("Accuracy with %3d trees: %.4f%n", i+1, Accuracy.of(Segment.testy, test[i]));
        }
    }

    @Test
    public void testUSPS() {
        System.out.println("USPS");

        MathEx.setSeed(19650218); // to get repeatable results.
        GradientTreeBoost model = GradientTreeBoost.fit(USPS.formula, USPS.train, 100, 20, 100, 5, 0.05, 0.7);

        double[] importance = model.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().fieldName(i), importance[i]);
        }

        int[] prediction = Validation.test(model, USPS.test);
        int error = Error.of(USPS.testy, prediction);

        System.out.println("Error = " + error);
        assertEquals(141, error);

        System.out.println("----- Progressive Accuracy -----");
        int[][] test = model.test(USPS.test);
        for (int i = 0; i < test.length; i++) {
            System.out.format("Accuracy with %3d trees: %.4f%n", i+1, Accuracy.of(USPS.testy, test[i]));
        }
    }
}
