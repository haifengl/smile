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

import java.util.Arrays;
import java.util.concurrent.Flow;
import smile.classification.RandomForest.Options;
import smile.data.DataFrame;
import smile.datasets.*;
import smile.io.Read;
import smile.io.Write;
import smile.math.MathEx;
import smile.model.cart.SplitRule;
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
public class RandomForestTest {
    long[] seeds = {
            342317953, 521642753, 72070657, 577451521, 266953217, 179976193,
            374603777, 527788033, 303395329, 185759582, 261518209, 461300737,
            483646580, 532528741, 159827201, 284796929, 655932697, 26390017,
            454330473, 867526205, 824623361, 719082324, 334008833, 699933293,
            823964929, 155216641, 150210071, 249486337, 713508520, 558398977,
            886227770, 74062428, 670528514, 701250241, 363339915, 319216345,
            757017601, 459643789, 170213767, 434634241, 414707201, 153100613,
            753882113, 546490145, 412517763, 888761089, 628632833, 565587585,
            175885057, 594903553, 78450978, 212995578, 710952449, 835852289,
            415422977, 832538705, 624345857, 839826433, 260963602, 386066438,
            530942946, 261866663, 269735895, 798436064, 379576194, 251582977,
            349161809, 179653121, 218870401, 415292417, 86861523, 570214657,
            701581299, 805955890, 358025785, 231452966, 584239408, 297276298,
            371814913, 159451160, 284126095, 896291329, 496278529, 556314113,
            31607297, 726761729, 217004033, 390410146, 70173193, 661580775,
            633589889, 389049037, 112099159, 54041089, 80388281, 492196097,
            912179201, 699398161, 482080769, 363844609, 286008078, 398098433,
            339855361, 189583553, 697670495, 709568513, 98494337, 99107427,
            433350529, 266601473, 888120086, 243906049, 414781441, 154685953,
            601194298, 292273153, 212413697, 568007473, 666386113, 712261633,
            802026964, 783034790, 188095005, 742646355, 550352897, 209421313,
            175672961, 242531185, 157584001, 201363231, 760741889, 852924929,
            60158977, 774572033, 311159809, 407214966, 804474160, 304456514,
            54251009, 504009638, 902115329, 870383757, 487243777, 635554282,
            564918017, 636074753, 870308031, 817515521, 494471884, 562424321,
            81710593, 476321537, 595107841, 418699893, 315560449, 773617153,
            163266399, 274201241, 290857537, 879955457, 801949697, 669025793,
            753107969, 424060977, 661877468, 433391617, 222716929, 334154852,
            878528257, 253742849, 480885528, 99773953, 913761493, 700407809,
            483418083, 487870398, 58433153, 608046337, 475342337, 506376199,
            378726401, 306604033, 724646374, 895195218, 523634541, 766543466,
            190068097, 718704641, 254519245, 393943681, 796689751, 379497473,
            50014340, 489234689, 129556481, 178766593, 142540536, 213594113,
            870440184, 277912577};

    static class TrainingStatusSubscriber implements Flow.Subscriber<RandomForest.TrainingStatus> {
        private Flow.Subscription subscription;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(RandomForest.TrainingStatus status) {
            System.out.format("Tree %d: validation metrics = %s%n", status.tree(), status.metrics());
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

    public RandomForestTest() {
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
        MathEx.setSeed(19650218); // to get repeatable results for cross validation.
        var weather = new WeatherNominal();
        var options = new Options(20, 2, SplitRule.GINI, 8, 10, 1, 1.0, null, seeds, null);
        RandomForest model = RandomForest.fit(weather.formula(), weather.data(), options);
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
                (f, x) -> RandomForest.fit(f, x, options));

        System.out.println(metrics);
        assertEquals(0.5714, metrics.accuracy(), 1E-4);

        java.nio.file.Path temp = Write.object(model);
        Read.object(temp);

        // Test with data without response variable.
        DataFrame test = weather.data().drop("play");
        model.predict(test);
    }

    @Test
    public void testIris() throws Exception {
        System.out.println("Iris");

        MathEx.setSeed(19650218); // to get repeatable results for cross validation.
        var iris = new Iris();
        var options = new Options(100, 3, SplitRule.GINI, 20, 100, 5, 1.0, null, seeds, null);
        RandomForest model = RandomForest.fit(iris.formula(), iris.data(), options);

        double[] importance = model.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().names()[i], importance[i]);
        }

        ClassificationMetrics metrics = LOOCV.classification(iris.formula(), iris.data(),
                (f, x) -> RandomForest.fit(f, x, options));
        System.out.println(metrics);
        assertEquals(0.9467, metrics.accuracy(), 1E-4);
    }

    @Test
    public void testPenDigits() throws Exception {
        System.out.println("Pen Digits");
        MathEx.setSeed(19650218); // to get repeatable results for cross validation.
        var pen = new PenDigits();
        var options = new Options(100, 4, SplitRule.GINI, 20, 100, 5, 1.0, null, seeds, null);
        var result = CrossValidation.classification(10, pen.formula(), pen.data(),
                (f, x) -> RandomForest.fit(f, x, options));

        System.out.println(result);
        assertEquals(0.9706, result.avg().accuracy(), 1E-4);
    }

    @Test
    public void testBreastCancer() throws Exception {
        System.out.println("Breast Cancer");

        MathEx.setSeed(19650218); // to get repeatable results for cross validation.
        var cancer = new BreastCancer();
        var options = new Options(100, 5, SplitRule.GINI, 20, 100, 5, 1.0, null, seeds, null);
        var result = CrossValidation.classification(10, cancer.formula(), cancer.data(),
                (f, x) -> RandomForest.fit(f, x, options));

        System.out.println(result);
        assertEquals(0.9550, result.avg().accuracy(), 1E-4);
    }

    @Test
    public void testSegment() throws Exception {
        System.out.println("Segment");
        var segment = new ImageSegmentation();
        int[] testy = segment.testy();
        var options = new Options(200, 16, SplitRule.GINI, 20, 100, 5, 1.0, null, seeds, null);
        RandomForest model = RandomForest.fit(segment.formula(), segment.train(), options);

        double[] importance = model.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().names()[i], importance[i]);
        }

        int[] prediction = model.predict(segment.test());
        int error = Error.of(testy, prediction);

        System.out.println("Error = " + error);
        assertEquals(34, error);

        System.out.println("----- Progressive Accuracy -----");
        int[][] test = model.test(segment.test());
        for (int i = 0; i < test.length; i++) {
            System.out.format("Accuracy with %3d trees: %.4f%n", i+1, Accuracy.of(testy, test[i]));
        }
    }

    @Test
    public void testUSPS() throws Exception {
        System.out.println("USPS");
        var usps = new USPS();
        int[] testy = usps.testy();

        try (var controller = new IterativeAlgorithmController<RandomForest.TrainingStatus>()) {
            controller.subscribe(new TrainingStatusSubscriber());
            var options = new Options(200, 16, SplitRule.GINI, 20, 200, 5, 1.0, null, seeds, controller);
            RandomForest model = RandomForest.fit(usps.formula(), usps.train(), options);

            double[] importance = model.importance();
            for (int i = 0; i < importance.length; i++) {
                System.out.format("%-15s %.4f%n", model.schema().names()[i], importance[i]);
            }

            int[] prediction = model.predict(usps.test());
            int error = Error.of(testy, prediction);

            System.out.println("Error = " + error);
            assertEquals(151, error);

            System.out.println("----- Progressive Accuracy -----");
            int[][] test = model.test(usps.test());
            for (int i = 0; i < test.length; i++) {
                System.out.format("Accuracy with %3d trees: %.4f%n", i + 1, Accuracy.of(testy, test[i]));
            }
        }
    }

    @Test
    public void testTrim() throws Exception {
        System.out.println("trim");
        var segment = new ImageSegmentation();
        int[] testy = segment.testy();
        var options = new Options(200, 16, SplitRule.GINI, 20, 100, 5, 1.0, null, seeds, null);
        RandomForest model = RandomForest.fit(segment.formula(), segment.train(), options);
        System.out.println(model.metrics());
        assertEquals(200, model.size());

        int[] prediction = model.predict(segment.test());
        int error = Error.of(testy, prediction);

        System.out.println("Error = " + error);
        assertEquals(34, error);

        RandomForest trimmed = model.trim(100);
        assertEquals(200, model.size());
        assertEquals(100, trimmed.size());

        double weight1 = Arrays.stream(model.models()).mapToDouble(RandomForest.Model::weight).min().orElse(0);
        double weight2 = Arrays.stream(trimmed.models()).mapToDouble(RandomForest.Model::weight).min().orElse(0);
        assertTrue(weight2 > weight1);

        prediction = trimmed.predict(segment.test());
        error = Error.of(testy, prediction);

        System.out.println("Error after trim = " + error);
        assertEquals(32, error);
    }

    @Test
    public void testMerge() throws Exception {
        System.out.println("merge");
        var segment = new ImageSegmentation();
        double[][] testx = segment.testx();
        int[] testy = segment.testy();
        var options = new Options(100, 16, SplitRule.GINI, 20, 100, 5, 1.0, null, seeds, null);
        RandomForest forest1 = RandomForest.fit(segment.formula(), segment.train(), options);
        RandomForest forest2 = RandomForest.fit(segment.formula(), segment.train(), options);
        RandomForest forest = forest1.merge(forest2);

        int error1 = Error.of(testy, forest1.predict(segment.test()));
        int error2 = Error.of(testy, forest2.predict(segment.test()));
        int error  = Error.of(testy, forest.predict(segment.test()));
        System.out.format("Forest 1 Error = %d%n", error1);
        System.out.format("Forest 2 Error = %d%n", error2);
        System.out.format("Merged   Error = %d%n", error);
        assertEquals(33, error1);
        assertEquals(33, error2);
        assertEquals(33, error);
    }

    @Test
    public void testPrune() throws Exception {
        System.out.println("prune");

        // Overfitting with very large maxNodes and small nodeSize
        var usps = new USPS();
        int[] testy = usps.testy();
        var options = new Options(200, 16, SplitRule.GINI, 20, 2000, 1, 1.0, null, seeds, null);
        RandomForest model = RandomForest.fit(usps.formula(), usps.train(), options);

        double[] importance = model.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().names()[i], importance[i]);
        }

        int[] prediction = model.predict(usps.test());
        int error = Error.of(testy, prediction);

        System.out.println("Error = " + error);
        assertEquals(115, error);

        RandomForest lean = model.prune(usps.test());

        importance = lean.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", lean.schema().names()[i], importance[i]);
        }

        // The old model should not be modified.
        prediction = model.predict(usps.test());
        error = Error.of(testy, prediction);

        System.out.println("Error of old model after pruning = " + error);
        assertEquals(115, error);

        prediction = lean.predict(usps.test());
        error = Error.of(testy, prediction);

        System.out.println("Error of pruned model after pruning = " + error);
        assertEquals(87, error);
    }

    @Test
    public void testShap() throws Exception {
        MathEx.setSeed(19650218); // to get repeatable results.
        var iris = new Iris();
        var options = new Options(10, 2, SplitRule.GINI, 20, 100, 5, 1.0, null, seeds, null);
        RandomForest model = RandomForest.fit(iris.formula(), iris.data(), options);
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
