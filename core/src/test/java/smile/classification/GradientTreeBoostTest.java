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

import smile.data.Iris;
import smile.data.Segment;
import smile.data.USPS;
import smile.data.WeatherNominal;
import smile.math.MathEx;
import smile.validation.Accuracy;
import smile.validation.Validation;
import smile.validation.Error;
import smile.validation.LOOCV;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
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


    @Test
    public void testWeather() {
        System.out.println("Weather");

        // to get repeatable results.
        MathEx.setSeed(19650218);
        GradientTreeBoost model = GradientTreeBoost.fit(WeatherNominal.formula, WeatherNominal.data, 100, 6, 5, 0.05, 0.7);

        double[] importance = model.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().get().fieldName(i), importance[i]);
        }

        int[] prediction = LOOCV.classification(WeatherNominal.data, x -> GradientTreeBoost.fit(WeatherNominal.formula, x, 100, 6, 5, 0.05, 0.7));
        int error = Error.apply(WeatherNominal.y, prediction);

        System.out.println("Error = " + error);
        assertEquals(6, error);
    }

    @Test
    public void testIris() {
        System.out.println("Iris");

        // to get repeatable results.
        MathEx.setSeed(19650218);
        GradientTreeBoost model = GradientTreeBoost.fit(Iris.formula, Iris.data, 100, 6, 5, 0.05, 0.7);

        double[] importance = model.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().get().fieldName(i), importance[i]);
        }

        int[] prediction = LOOCV.classification(Iris.data, x -> GradientTreeBoost.fit(Iris.formula, x, 100, 6, 5, 0.05, 0.7));
        int error = Error.apply(Iris.y, prediction);
        System.out.println("Error = " + error);
        assertEquals(8, error);
    }

    @Test
    public void testSegment() {
        System.out.println("Segment");

        // to get repeatable results.
        MathEx.setSeed(19650218);
        GradientTreeBoost model = GradientTreeBoost.fit(Segment.formula, Segment.train, 100, 6, 5, 0.05, 0.7);

        double[] importance = model.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().get().fieldName(i), importance[i]);
        }

        int[] prediction = Validation.test(model, Segment.test);
        int error = Error.apply(Segment.testy, prediction);

        System.out.println("Error = " + error);
        assertEquals(20, error);

        System.out.println("----- Progressive Accuracy -----");
        int[][] test = model.test(Segment.test);
        for (int i = 0; i < test.length; i++) {
            System.out.format("Accuracy with %3d trees: %.4f%n", i+1, Accuracy.apply(Segment.testy, test[i]));
        }
    }

    @Test
    public void testUSPS() {
        System.out.println("USPS");

        // to get repeatable results.
        MathEx.setSeed(19650218);
        GradientTreeBoost model = GradientTreeBoost.fit(USPS.formula, USPS.train, 100, 100, 5, 0.05, 0.7);

        double[] importance = model.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().get().fieldName(i), importance[i]);
        }

        int[] prediction = Validation.test(model, USPS.test);
        int error = Error.apply(USPS.testy, prediction);

        System.out.println("Error = " + error);
        assertEquals(147, error);

        System.out.println("----- Progressive Accuracy -----");
        int[][] test = model.test(USPS.test);
        for (int i = 0; i < test.length; i++) {
            System.out.format("Accuracy with %3d trees: %.4f%n", i+1, Accuracy.apply(USPS.testy, test[i]));
        }
    }
}
