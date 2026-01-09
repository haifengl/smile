/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.classification;

import smile.classification.DecisionTree.Options;
import smile.datasets.BreastCancer;
import smile.datasets.*;
import smile.io.Read;
import smile.io.Write;
import smile.math.MathEx;
import smile.model.cart.SplitRule;
import smile.validation.*;
import smile.validation.metric.Error;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng
 */
public class DecisionTreeTest {
    
    public DecisionTreeTest() {
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
    }
    
    @BeforeEach
    public void setUp() {
    }
    
    @AfterEach
    public void tearDown() {
    }

    @Test
    public void testWeather() throws Exception {
        System.out.println("Weather");
        var weather = new WeatherNominal();
        var options = new Options(SplitRule.GINI, 8, 10, 1);
        DecisionTree model = DecisionTree.fit(weather.formula(), weather.data(), options);
        System.out.println(model);
        String[] fields = model.schema().names();

        double[] importance = model.importance();
        System.out.println("----- importance -----");
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", fields[i], importance[i]);
        }

        double[] shap = model.shap(weather.data());
        System.out.println("----- SHAP -----");
        for (int i = 0; i < fields.length; i++) {
            System.out.format("%-15s %.4f    %.4f%n", fields[i], shap[2*i], shap[2*i+1]);
        }

        java.nio.file.Path temp = Write.object(model);
        Read.object(temp);

        ClassificationMetrics metrics = LOOCV.classification(weather.formula(), weather.data(), (f, x) -> DecisionTree.fit(f, x, options));

        System.out.println(metrics);
        assertEquals(0.5, metrics.accuracy(), 1E-4);
    }

    @Test
    public void testIris() throws Exception {
        System.out.println("Iris");

        var iris = new Iris();
        DecisionTree model = DecisionTree.fit(iris.formula(), iris.data());
        System.out.println(model);

        double[] importance = model.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().names()[i], importance[i]);
        }

        ClassificationMetrics metrics = LOOCV.classification(iris.formula(), iris.data(), DecisionTree::fit);

        System.out.println(metrics);
        assertEquals(0.94, metrics.accuracy(), 1E-4);
    }

    @Test
    public void testPenDigits() throws Exception {
        System.out.println("Pen Digits");
        MathEx.setSeed(19650218); // to get repeatable results.
        var pen = new PenDigits();
        var options = new Options(SplitRule.GINI, 20, 100, 5);
        var result = CrossValidation.classification(10, pen.formula(), pen.data(),
                (f, x) -> DecisionTree.fit(f, x, options));

        System.out.println(result);
        assertEquals(0.9532, result.avg().accuracy(), 1E-4);
    }

    @Test
    public void testBreastCancer() throws Exception {
        System.out.println("Breast Cancer");

        MathEx.setSeed(19650218); // to get repeatable results.
        var cancer = new BreastCancer();
        var options = new Options(SplitRule.GINI, 20, 100, 5);
        var result = CrossValidation.classification(10, cancer.formula(), cancer.data(),
                (f, x) -> DecisionTree.fit(f, x, options));

        System.out.println(result);
        assertEquals(0.9275, result.avg().accuracy(), 1E-4);
    }

    @Test
    public void testSegment() throws Exception {
        System.out.println("Segment");
        var segment = new ImageSegmentation();
        var options = new Options(SplitRule.ENTROPY, 20, 100, 5);
        DecisionTree model = DecisionTree.fit(segment.formula(), segment.train(), options);
        System.out.println(model);

        double[] importance = model.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().names()[i], importance[i]);
        }

        int[] prediction = model.predict(segment.test());
        int error = Error.of(segment.testy(), prediction);

        System.out.println("Error = " + error);
        assertEquals(43, error, 1E-4);
    }

    @Test
    public void testUSPS() throws Exception {
        System.out.println("USPS");
        var usps = new USPS();
        var options = new Options(SplitRule.ENTROPY, 20, 500, 5);
        DecisionTree model = DecisionTree.fit(usps.formula(), usps.train(), options);
        System.out.println(model);

        double[] importance = model.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().names()[i], importance[i]);
        }

        int[] prediction = model.predict(usps.test());
        int error = Error.of(usps.testy(), prediction);

        System.out.println("Error = " + error);
        assertEquals(332, error);
    }

    @Test
    public void testPrune() throws Exception {
        System.out.println("USPS");
        var usps = new USPS();
        int[] testy = usps.testy();
        // Overfitting with very large maxNodes and small nodeSize
        var options = new Options(SplitRule.ENTROPY, 20, 3000, 1);
        DecisionTree model = DecisionTree.fit(usps.formula(), usps.train(), options);
        System.out.println(model);

        double[] importance = model.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().names()[i], importance[i]);
        }

        int[] prediction = model.predict(usps.test());
        int error = Error.of(testy, prediction);

        System.out.println("Error = " + error);
        assertEquals(897, model.size());
        assertEquals(324, error);

        DecisionTree lean = model.prune(usps.test());
        System.out.println(lean);

        importance = lean.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", lean.schema().names()[i], importance[i]);
        }

        // The old model should not be modified.
        prediction = model.predict(usps.test());
        error = Error.of(testy, prediction);

        System.out.println("Error of old model after pruning = " + error);
        assertEquals(897, model.size());
        assertEquals(324, error);

        prediction = lean.predict(usps.test());
        error = Error.of(testy, prediction);

        System.out.println("Error of pruned model after pruning = " + error);
        assertEquals(743, lean.size());
        assertEquals(274, error);
    }

    @Test
    public void testShap() throws Exception {
        MathEx.setSeed(19650218); // to get repeatable results.
        var iris = new Iris();
        var options = new Options(SplitRule.GINI, 20, 100, 5);
        DecisionTree model = DecisionTree.fit(iris.formula(), iris.data(), options);
        String[] fields = model.schema().names();
        double[] importance = model.importance();
        double[] shap = model.shap(iris.data());

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
