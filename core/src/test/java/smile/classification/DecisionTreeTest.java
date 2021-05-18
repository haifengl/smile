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

import smile.base.cart.SplitRule;
import smile.data.*;
import smile.math.MathEx;
import smile.validation.*;
import smile.validation.metric.Error;
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

    @Test
    public void testWeather() throws Exception {
        System.out.println("Weather");

        DecisionTree model = DecisionTree.fit(WeatherNominal.formula, WeatherNominal.data, SplitRule.GINI, 8, 10, 1);
        System.out.println(model);

        double[] importance = model.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().name(i), importance[i]);
        }

        java.nio.file.Path temp = smile.data.Serialize.write(model);
        smile.data.Serialize.read(temp);

        ClassificationMetrics metrics = LOOCV.classification(WeatherNominal.formula, WeatherNominal.data, (f, x) -> DecisionTree.fit(f, x, SplitRule.GINI, 8, 10, 1));

        System.out.println(metrics);
        assertEquals(0.5, metrics.accuracy, 1E-4);
    }

    @Test
    public void testIris() {
        System.out.println("Iris");

        DecisionTree model = DecisionTree.fit(Iris.formula, Iris.data);
        System.out.println(model);

        double[] importance = model.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().name(i), importance[i]);
        }

        ClassificationMetrics metrics = LOOCV.classification(Iris.formula, Iris.data, DecisionTree::fit);

        System.out.println(metrics);
        assertEquals(0.94, metrics.accuracy, 1E-4);
    }

    @Test
    public void testPenDigits() {
        System.out.println("Pen Digits");

        MathEx.setSeed(19650218); // to get repeatable results.
        ClassificationValidations<DecisionTree> result = CrossValidation.classification(10, PenDigits.formula, PenDigits.data,
                (f, x) -> DecisionTree.fit(f, x, SplitRule.GINI, 20, 100, 5));

        System.out.println(result);
        assertEquals(0.9532, result.avg.accuracy, 1E-4);
    }

    @Test
    public void testBreastCancer() {
        System.out.println("Breast Cancer");

        MathEx.setSeed(19650218); // to get repeatable results.
        ClassificationValidations<DecisionTree> result = CrossValidation.classification(10, BreastCancer.formula, BreastCancer.data,
                (f, x) -> DecisionTree.fit(f, x, SplitRule.GINI, 20, 100, 5));

        System.out.println(result);
        assertEquals(0.9275, result.avg.accuracy, 1E-4);
    }

    @Test
    public void testSegment() {
        System.out.println("Segment");

        DecisionTree model = DecisionTree.fit(Segment.formula, Segment.train, SplitRule.ENTROPY, 20, 100, 5);
        System.out.println(model);

        double[] importance = model.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().name(i), importance[i]);
        }

        int[] prediction = model.predict(Segment.test);
        int error = Error.of(Segment.testy, prediction);

        System.out.println("Error = " + error);
        assertEquals(43, error, 1E-4);
    }

    @Test
    public void testUSPS() {
        System.out.println("USPS");

        DecisionTree model = DecisionTree.fit(USPS.formula, USPS.train, SplitRule.ENTROPY, 20, 500, 5);
        System.out.println(model);

        double[] importance = model.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().name(i), importance[i]);
        }

        int[] prediction = model.predict(USPS.test);
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
            System.out.format("%-15s %.4f%n", model.schema().name(i), importance[i]);
        }

        int[] prediction = model.predict(USPS.test);
        int error = Error.of(USPS.testy, prediction);

        System.out.println("Error = " + error);
        assertEquals(897, model.size());
        assertEquals(324, error);

        DecisionTree lean = model.prune(USPS.test);
        System.out.println(lean);

        importance = lean.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", lean.schema().name(i), importance[i]);
        }

        // The old model should not be modified.
        prediction = model.predict(USPS.test);
        error = Error.of(USPS.testy, prediction);

        System.out.println("Error of old model after pruning = " + error);
        assertEquals(897, model.size());
        assertEquals(324, error);

        prediction = lean.predict(USPS.test);
        error = Error.of(USPS.testy, prediction);

        System.out.println("Error of pruned model after pruning = " + error);
        assertEquals(743, lean.size());
        assertEquals(273, error);
    }

    @Test
    public void testShap() {
        MathEx.setSeed(19650218); // to get repeatable results.
        DecisionTree model = DecisionTree.fit(Iris.formula, Iris.data, SplitRule.GINI, 20, 100, 5);
        String[] fields = java.util.Arrays.stream(model.schema().fields()).map(field -> field.name).toArray(String[]::new);
        double[] importance = model.importance();
        double[] shap = model.shap(Iris.data);

        System.out.println("----- importance -----");
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", fields[i], importance[i]);
        }

        System.out.println("----- SHAP -----");
        for (int i = 0; i < fields.length; i++) {
            System.out.format("%-15s %.4f    %.4f    %.4f%n", fields[i], shap[2*i], shap[2*i+1], shap[2*i+2]);
        }
    }
}
