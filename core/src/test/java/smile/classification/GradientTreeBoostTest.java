/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.classification;

import java.util.concurrent.Flow;
import smile.classification.GradientTreeBoost.Options;
import smile.datasets.*;
import smile.io.Read;
import smile.io.Write;
import smile.math.MathEx;
import smile.util.IterativeAlgorithmController;
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
    static class TrainingStatusSubscriber implements Flow.Subscriber<GradientTreeBoost.TrainingStatus> {
        private Flow.Subscription subscription;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(GradientTreeBoost.TrainingStatus status) {
            System.out.format("Tree %d: loss = %.5f, validation metrics = %s%n",
                    status.tree(), status.loss(), status.metrics());
            subscription.request(1);
        }

        @Override
        public void onError(Throwable throwable) {
            System.err.println("Controller receives an exception: " + throwable.getMessage());
        }

        @Override
        public void onComplete() {
            System.out.println("Training is done");
        }
    }

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

        try (var controller = new IterativeAlgorithmController<GradientTreeBoost.TrainingStatus>()) {
            controller.subscribe(new TrainingStatusSubscriber());
            var options = new Options(100, 20, 6, 5, 0.05, 0.7, weather.data(), controller);
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
                System.out.format("%-15s %.4f    %.4f%n", fields[i], shap[2 * i], shap[2 * i + 1]);
            }

            java.nio.file.Path temp = Write.object(model);
            Read.object(temp);
        }

        var options = new Options(100, 20, 6, 5, 0.05, 0.7, null, null);
        var result = LOOCV.classification(weather.formula(), weather.data(),
                (f, x) -> GradientTreeBoost.fit(f, x, options));

        System.out.println(result);
        assertEquals(0.5714, result.accuracy(), 1E-4);
    }

    @Test
    public void testIris() throws Exception {
        System.out.println("Iris");

        MathEx.setSeed(19650218); // to get repeatable results.
        var iris = new Iris();
        var options = new Options(100, 20, 6, 5, 0.05, 0.7, null, null);
        GradientTreeBoost model = GradientTreeBoost.fit(iris.formula(), iris.data(), options);

        double[] importance = model.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().names()[i], importance[i]);
        }

        var result = CrossValidation.classification(5, iris.formula(), iris.data(),
                (f, x) -> GradientTreeBoost.fit(f, x, options));

        System.out.println(result);
        assertEquals(0.9467, result.avg().accuracy(), 1E-4);
    }

    @Test
    public void testPenDigits() throws Exception {
        System.out.println("Pen Digits");
        MathEx.setSeed(19650218); // to get repeatable results.
        var pen = new PenDigits();
        var options = new Options(100, 20, 6, 5, 0.05, 0.7, null, null);
        var result = CrossValidation.classification(5, pen.formula(), pen.data(),
                (f, x) -> GradientTreeBoost.fit(f, x, options));

        System.out.println(result);
        assertEquals(0.9837, result.avg().accuracy(), 1E-4);
    }

    @Test
    public void testBreastCancer() throws Exception {
        System.out.println("Breast Cancer");

        MathEx.setSeed(19650218); // to get repeatable results.
        var cancer = new BreastCancer();
        var options = new Options(100, 20, 6, 5, 0.05, 0.7, null, null);
        var result = CrossValidation.classification(5, cancer.formula(), cancer.data(),
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

        try (var controller = new IterativeAlgorithmController<GradientTreeBoost.TrainingStatus>()) {
            controller.subscribe(new TrainingStatusSubscriber());
            var options = new Options(100, 20, 6, 5, 0.05, 0.7, segment.test(), controller);
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
                System.out.format("Accuracy with %3d trees: %.4f%n", i + 1, Accuracy.of(testy, test[i]));
            }
        }
    }

    @Test
    public void testUSPS() throws Exception {
        System.out.println("USPS");
        MathEx.setSeed(19650218); // to get repeatable results.
        var usps = new USPS();
        int[] testy = usps.testy();
        var options = new Options(50, 20, 100, 5, 0.05, 0.7, null, null);
        GradientTreeBoost model = GradientTreeBoost.fit(usps.formula(), usps.train(), options);

        double[] importance = model.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().names()[i], importance[i]);
        }

        int[] prediction = model.predict(usps.test());
        int error = Error.of(testy, prediction);

        System.out.println("Error = " + error);
        assertEquals(164, error, 3);

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
        var options = new Options(100, 20, 6, 5, 0.05, 0.7, null, null);
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
