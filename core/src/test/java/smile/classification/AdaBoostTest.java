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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.classification;

import java.lang.ref.WeakReference;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import smile.data.type.StructField;
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
 * @author Haifeng
 */
public class AdaBoostTest {
    static class TrainingStatusSubscriber implements Subscriber<AdaBoost.TrainingStatus> {
        private WeakReference<IterativeAlgorithmController<AdaBoost.TrainingStatus>> controller;
        private Subscription subscription;

        TrainingStatusSubscriber(IterativeAlgorithmController<AdaBoost.TrainingStatus> controller) {
            this.controller = new WeakReference<>(controller);
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(AdaBoost.TrainingStatus status) {
            System.out.format("Tree %d: weighted error = %.2f%%, validation metrics = %s%n",
                    status.tree(), 100 * status.weightedError(), status.metrics());
            if (status.tree() == 100) controller.get().stop();
            subscription.request(1);
        }

        @Override
        public void onError(Throwable throwable) {
            System.err.println("Controller receives an exception: " + throwable.getMessage());
        }

        @Override
        public void onComplete() {
            System.out.println("Training is done");
            controller.clear();
            controller = null;
        }
    }

    public AdaBoostTest() {
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
        try (var controller = new IterativeAlgorithmController<AdaBoost.TrainingStatus>()) {
            controller.subscribe(new TrainingStatusSubscriber(controller));
            var options = new AdaBoost.Options(20, 5, 8, 1, weather.data(), controller);
            AdaBoost model = AdaBoost.fit(weather.formula(), weather.data(), options);
            int error = Error.of(weather.y(), model.predict(weather.data()));
            System.out.println("Training Error = " + error);
            assertEquals(0, error);

            String[] fields = model.schema().names();
            double[] importance = model.importance();
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

        var options = new AdaBoost.Options(20, 5, 8, 1, null, null);
        ClassificationMetrics metrics = LOOCV.classification(weather.formula(), weather.data(),
                (f, x) -> AdaBoost.fit(f, x, options));
        System.out.println(metrics);
        assertEquals(0.6429, metrics.accuracy(), 1E-4);
    }

    @Test
    public void testIris() throws Exception {
        System.out.println("Iris");

        MathEx.setSeed(19650218); // to get repeatable results.
        var iris = new Iris();
        var options = new AdaBoost.Options(200, 20, 4, 1, null, null);
        AdaBoost model = AdaBoost.fit(iris.formula(), iris.data(), options);

        double[] importance = model.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().names()[i], importance[i]);
        }

        ClassificationMetrics metrics = LOOCV.classification(iris.formula(), iris.data(),
                (f, x) -> AdaBoost.fit(f, x, options));
        System.out.println(metrics);
        assertEquals(0.9533, metrics.accuracy(), 1E-4);
    }

    @Test
    public void testPenDigits() throws Exception {
        System.out.println("Pen Digits");
        MathEx.setSeed(19650218); // to get repeatable results.
        var pen = new PenDigits();
        var options = new AdaBoost.Options(200, 20, 4, 1, null, null);
        var result = CrossValidation.classification(10, pen.formula(), pen.data(),
                (f, x) -> AdaBoost.fit(f, x, options));
        System.out.println(result);
        assertEquals(0.9525, result.avg().accuracy(), 1E-4);
    }

    @Test
    public void testBreastCancer() throws Exception {
        System.out.println("Breast Cancer");

        MathEx.setSeed(19650218); // to get repeatable results.
        var cancer = new BreastCancer();
        var options = new AdaBoost.Options(100, 20, 4, 1, null, null);
        var result = CrossValidation.classification(10, cancer.formula(), cancer.data(),
                (f, x) -> AdaBoost.fit(f, x, options));

        System.out.println(result);
        int error = result.rounds().stream().mapToInt(round -> round.metrics().error()).sum();
        assertEquals(15, error);
    }

    @Test
    public void testSegment() throws Exception {
        System.out.println("Segment");
        MathEx.setSeed(19650218); // to get repeatable results.
        var segment = new ImageSegmentation();
        var testy = segment.testy();

        try (var controller = new IterativeAlgorithmController<AdaBoost.TrainingStatus>()) {
            controller.subscribe(new TrainingStatusSubscriber(controller));
            var options = new AdaBoost.Options(200, 20, 6, 1, segment.test(), controller);
            AdaBoost model = AdaBoost.fit(segment.formula(), segment.train(), options);

            double[] importance = model.importance();
            for (int i = 0; i < importance.length; i++) {
                System.out.format("%-15s %.4f%n", model.schema().names()[i], importance[i]);
            }

            int error = Error.of(testy, model.predict(segment.test()));
            System.out.println("Error = " + error);
            assertEquals(26, error, 3);

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
        var options = new AdaBoost.Options(200, 20, 64, 1, null, null);
        AdaBoost model = AdaBoost.fit(usps.formula(), usps.train(), options);

        double[] importance = model.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().names()[i], importance[i]);
        }

        int error = Error.of(testy, model.predict(usps.test()));
        System.out.println("Error = " + error);
        assertEquals(152, error);

        System.out.println("----- Progressive Accuracy -----");
        int[][] test = model.test(usps.test());
        for (int i = 0; i < test.length; i++) {
            System.out.format("Accuracy with %3d trees: %.4f%n", i+1, Accuracy.of(testy, test[i]));
        }
    }

    @Test
    public void testShap() throws Exception {
        System.out.println("SHAP");

        MathEx.setSeed(19650218); // to get repeatable results.
        var iris = new Iris();
        var options = new AdaBoost.Options(200, 20, 4, 5, null, null);
        AdaBoost model = AdaBoost.fit(iris.formula(), iris.data(), options);
        String[] fields = model.schema().fields().stream().map(StructField::name).toArray(String[]::new);
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
