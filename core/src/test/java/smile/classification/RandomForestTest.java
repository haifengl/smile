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

import java.util.Optional;
import java.util.stream.LongStream;
import smile.base.cart.SplitRule;
import smile.data.Iris;
import smile.data.Segment;
import smile.data.USPS;
import smile.data.WeatherNominal;
import smile.math.MathEx;
import smile.validation.Accuracy;
import smile.validation.Error;
import smile.validation.LOOCV;
import smile.validation.Validation;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng
 */
public class RandomForestTest {
    
    public RandomForestTest() {
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
        RandomForest model = RandomForest.fit(WeatherNominal.formula, WeatherNominal.data, 100, 2, SplitRule.GINI, 100, 5, 1.0);

        double[] importance = model.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().get().fieldName(i), importance[i]);
        }

        int[] prediction = LOOCV.classification(WeatherNominal.data, x -> RandomForest.fit(WeatherNominal.formula, x, 100, 2, SplitRule.GINI, 100, 5, 1.0));
        int error = Error.apply(WeatherNominal.y, prediction);

        System.out.println("Error = " + error);
        assertEquals(5, error);
    }

    @Test
    public void testIris() {
        System.out.println("Iris");

        // to get repeatable results.
        MathEx.setSeed(19650218);
        RandomForest model = RandomForest.fit(Iris.formula, Iris.data, 100, 2, SplitRule.GINI, 100, 5, 1.0);

        double[] importance = model.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().get().fieldName(i), importance[i]);
        }

        int[] prediction = LOOCV.classification(Iris.data, x -> RandomForest.fit(Iris.formula, x, 100, 3, SplitRule.GINI, 100, 5, 1.0));
        int error = Error.apply(Iris.y, prediction);
        System.out.println("Error = " + error);
        assertEquals(7, error);
    }

    @Test
    public void testSegment() {
        System.out.println("Segment");

        // to get repeatable results.
        MathEx.setSeed(19650218);
        LongStream seeds = LongStream.generate(() -> MathEx.probablePrime(19650218L, 256));
        RandomForest model = RandomForest.fit(Segment.formula, Segment.train, 200, 16, SplitRule.GINI, 100, 5, 1.0, Optional.empty(), Optional.of(seeds));

        double[] importance = model.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().get().fieldName(i), importance[i]);
        }

        int[] prediction = Validation.test(model, Segment.test);
        int error = Error.apply(Segment.testy, prediction);

        System.out.println("Error = " + error);
        assertEquals(34, error);

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
        LongStream seeds = LongStream.generate(() -> MathEx.probablePrime(19650218L, 256));
        RandomForest model = RandomForest.fit(USPS.formula, USPS.train, 200, 16, SplitRule.GINI, 200, 5, 1.0, Optional.empty(), Optional.of(seeds));

        double[] importance = model.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().get().fieldName(i), importance[i]);
        }

        int[] prediction = Validation.test(model, USPS.test);
        int error = Error.apply(USPS.testy, prediction);

        System.out.println("Error = " + error);
        assertEquals(152, error);

        System.out.println("----- Progressive Accuracy -----");
        int[][] test = model.test(USPS.test);
        for (int i = 0; i < test.length; i++) {
            System.out.format("Accuracy with %3d trees: %.4f%n", i+1, Accuracy.apply(USPS.testy, test[i]));
        }
    }
}
