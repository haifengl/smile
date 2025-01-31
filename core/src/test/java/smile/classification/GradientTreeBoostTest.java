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

import smile.classification.GradientTreeBoost.Options;
import smile.datasets.*;
import smile.io.Read;
import smile.io.Write;
import smile.math.MathEx;
import smile.validation.*;
import smile.validation.metric.Accuracy;
import smile.validation.metric.Error;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class GradientTreeBoostTest {
    
    public GradientTreeBoostTest() {
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
        MathEx.setSeed(19650218); // to get repeatable results.
        var weather = new WeatherNominal();
        var options = new Options(100, 20, 6, 5, 0.05, 0.7);
        GradientTreeBoost model = GradientTreeBoost.fit(weather.formula(), weather.data(), options);
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

        ClassificationMetrics metrics = LOOCV.classification(weather.formula(), weather.data(),
                (f, x) -> GradientTreeBoost.fit(f, x, options));

        System.out.println(metrics);
        assertEquals(0.5714, metrics.accuracy(), 1E-4);

        java.nio.file.Path temp = Write.object(model);
        Read.object(temp);
    }

    @Test
    public void testIris() throws Exception {
        System.out.println("Iris");

        MathEx.setSeed(19650218); // to get repeatable results.
        var iris = new Iris();
        var options = new Options(100, 20, 6, 5, 0.05, 0.7);
        GradientTreeBoost model = GradientTreeBoost.fit(iris.formula(), iris.data(), options);

        double[] importance = model.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().names()[i], importance[i]);
        }

        ClassificationMetrics metrics = LOOCV.classification(iris.formula(), iris.data(),
                (f, x) -> GradientTreeBoost.fit(f, x, options));

        System.out.println(metrics);
        assertEquals(0.9467, metrics.accuracy(), 1E-4);
    }

    @Test
    public void testPenDigits() throws Exception {
        System.out.println("Pen Digits");
        MathEx.setSeed(19650218); // to get repeatable results.
        var pen = new PenDigits();
        var options = new Options(100, 20, 6, 5, 0.05, 0.7);
        var result = CrossValidation.classification(10, pen.formula(), pen.data(),
                (f, x) -> GradientTreeBoost.fit(f, x, options));

        System.out.println(result);
        assertEquals(0.9831, result.avg().accuracy(), 1E-4);
    }

    @Test
    public void testBreastCancer() throws Exception {
        System.out.println("Breast Cancer");

        MathEx.setSeed(19650218); // to get repeatable results.
        var cancer = new BreastCancer();
        var options = new Options(100, 20, 6, 5, 0.05, 0.7);
        var result = CrossValidation.classification(10, cancer.formula(), cancer.data(),
                (f, x) -> GradientTreeBoost.fit(f, x, options));

        System.out.println(result);
        assertEquals(0.9589, result.avg().accuracy(), 0.003);
    }

    @Test
    public void testSegment() throws Exception {
        System.out.println("Segment");
        MathEx.setSeed(19650218); // to get repeatable results.
        var segment = new ImageSegmentation();
        int[] testy = segment.testy();
        var options = new Options(100, 20, 6, 5, 0.05, 0.7);
        GradientTreeBoost model = GradientTreeBoost.fit(segment.formula(), segment.train(), options);

        double[] importance = model.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().names()[i], importance[i]);
        }

        int[] prediction = model.predict(segment.test());
        int error = Error.of(testy, prediction);

        System.out.println("Error = " + error);
        assertEquals(23, error, 1);

        System.out.println("----- Progressive Accuracy -----");
        int[][] test = model.test(segment.test());
        for (int i = 0; i < test.length; i++) {
            System.out.format("Accuracy with %3d trees: %.4f%n", i+1, Accuracy.of(testy, test[i]));
        }
    }

    @Test
    public void testUSPS() throws Exception {
        System.out.println("USPS");
        MathEx.setSeed(19650218); // to get repeatable results.
        var usps = new USPS();
        int[] testy = usps.testy();
        var options = new Options(100, 20, 100, 5, 0.05, 0.7);
        GradientTreeBoost model = GradientTreeBoost.fit(usps.formula(), usps.train(), options);

        double[] importance = model.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().names()[i], importance[i]);
        }

        int[] prediction = model.predict(usps.test());
        int error = Error.of(testy, prediction);

        System.out.println("Error = " + error);
        assertEquals(133, error, 3);

        System.out.println("----- Progressive Accuracy -----");
        int[][] test = model.test(usps.test());
        for (int i = 0; i < test.length; i++) {
            System.out.format("Accuracy with %3d trees: %.4f%n", i+1, Accuracy.of(testy, test[i]));
        }
    }

    @Test
    public void testShap() throws Exception {
        MathEx.setSeed(19650218); // to get repeatable results.
        var iris = new Iris();
        var options = new Options(100, 20, 6, 5, 0.05, 0.7);
        GradientTreeBoost model = GradientTreeBoost.fit(iris.formula(), iris.data(), options);
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
