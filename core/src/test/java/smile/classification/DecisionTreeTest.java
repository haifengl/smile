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

import smile.base.cart.SplitRule;
import smile.data.*;
import smile.math.MathEx;
import smile.validation.CrossValidation;
import smile.validation.Error;
import smile.validation.LOOCV;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.validation.Validation;

import static org.junit.Assert.*;

/**
 *
 * @author Haifeng
 */
public class DecisionTreeTest {
    
    public DecisionTreeTest() {
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

        DecisionTree model = DecisionTree.fit(WeatherNominal.formula, WeatherNominal.data);
        System.out.println(model);

        double[] importance = model.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().fieldName(i), importance[i]);
        }

        int[] prediction = LOOCV.classification(WeatherNominal.formula, WeatherNominal.data, (f, x) -> DecisionTree.fit(f, x));
        int error = Error.of(WeatherNominal.y, prediction);
        System.out.println("Error = " + error);
        assertEquals(5, error);

        java.nio.file.Path temp = smile.data.Serialize.write(model);
        smile.data.Serialize.read(temp);
    }

    @Test
    public void testIris() {
        System.out.println("Iris");

        DecisionTree model = DecisionTree.fit(Iris.formula, Iris.data);
        System.out.println(model);

        double[] importance = model.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().fieldName(i), importance[i]);
        }

        int[] prediction = LOOCV.classification(Iris.formula, Iris.data, (f, x) -> DecisionTree.fit(f, x));
        int error = Error.of(Iris.y, prediction);
        System.out.println("Error = " + error);
        assertEquals(9, error);
    }

    @Test
    public void testPenDigits() {
        System.out.println("Pen Digits");

        MathEx.setSeed(19650218); // to get repeatable results.
        int[] prediction = CrossValidation.classification(10, PenDigits.formula, PenDigits.data, (f, x) -> DecisionTree.fit(f, x, SplitRule.GINI, 20, 100, 5));
        int error = Error.of(PenDigits.y, prediction);

        System.out.println("Error = " + error);
        assertEquals(351, error);
    }

    @Test
    public void testBreastCancer() {
        System.out.println("Breast Cancer");

        MathEx.setSeed(19650218); // to get repeatable results.
        int[] prediction = CrossValidation.classification(10, BreastCancer.formula, BreastCancer.data, (f, x) -> DecisionTree.fit(f, x, SplitRule.GINI, 20, 100, 5));
        int error = Error.of(BreastCancer.y, prediction);

        System.out.println("Error = " + error);
        assertEquals(42, error);
    }

    @Test
    public void testSegment() {
        System.out.println("Segment");

        DecisionTree model = DecisionTree.fit(Segment.formula, Segment.train, SplitRule.ENTROPY, 20, 100, 5);
        System.out.println(model);

        double[] importance = model.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().fieldName(i), importance[i]);
        }

        int[] prediction = Validation.test(model, Segment.test);
        int error = Error.of(Segment.testy, prediction);

        System.out.println("Error = " + error);
        assertEquals(43, error);
    }

    @Test
    public void testUSPS() {
        System.out.println("USPS");

        DecisionTree model = DecisionTree.fit(USPS.formula, USPS.train, SplitRule.ENTROPY, 20, 500, 5);
        System.out.println(model);

        double[] importance = model.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().fieldName(i), importance[i]);
        }

        int[] prediction = Validation.test(model, USPS.test);
        int error = Error.of(USPS.testy, prediction);

        System.out.println("Error = " + error);
        assertEquals(331, error);
    }

    @Test
    public void testPrune() {
        System.out.println("USPS");

        // Overfitting with very large maxNodes and small nodeSize
        DecisionTree model = DecisionTree.fit(USPS.formula, USPS.train, SplitRule.ENTROPY, 20, 3000, 1);
        System.out.println(model);

        double[] importance = model.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().fieldName(i), importance[i]);
        }

        int[] prediction = Validation.test(model, USPS.test);
        int error = Error.of(USPS.testy, prediction);

        System.out.println("Error = " + error);
        assertEquals(897, model.size());
        assertEquals(324, error);

        DecisionTree lean = model.prune(USPS.test);
        System.out.println(lean);

        importance = lean.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", lean.schema().fieldName(i), importance[i]);
        }

        // The old model should not be modified.
        prediction = Validation.test(model, USPS.test);
        error = Error.of(USPS.testy, prediction);

        System.out.println("Error of old model after pruning = " + error);
        assertEquals(897, model.size());
        assertEquals(324, error);

        prediction = Validation.test(lean, USPS.test);
        error = Error.of(USPS.testy, prediction);

        System.out.println("Error of pruned model after pruning = " + error);
        assertEquals(743, lean.size());
        assertEquals(273, error);
    }
}
