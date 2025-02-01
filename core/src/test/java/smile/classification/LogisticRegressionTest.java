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

import smile.datasets.*;
import smile.io.Read;
import smile.io.Write;
import smile.math.MathEx;
import smile.validation.*;
import smile.validation.metric.Error;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng
 */
public class LogisticRegressionTest {

    public LogisticRegressionTest() {
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
    public void testIris() throws Exception {
        System.out.println("Iris");
        var iris = new Iris();
        ClassificationMetrics metrics = LOOCV.classification(iris.x(), iris.y(), LogisticRegression::fit);

        System.out.println(metrics);
        assertEquals(0.9667, metrics.accuracy(), 1E-4);
    }

    @Test
    public void testWeather() throws Exception {
        System.out.println("Weather");
        var weather = new WeatherNominal();
        ClassificationMetrics metrics = LOOCV.classification(weather.dummy(), weather.y(), LogisticRegression::fit);

        System.out.println(metrics);
        assertEquals(0.7143, metrics.accuracy(), 1E-4);
    }

    @Test
    public void testPenDigits() throws Exception {
        System.out.println("Pen Digits");
        MathEx.setSeed(19650218); // to get repeatable results.
        var pen = new PenDigits();
        var result = CrossValidation.classification(10, pen.x(), pen.y(), LogisticRegression::fit);

        System.out.println(result);
        assertEquals(0.9548, result.avg().accuracy(), 0.001);
    }

    @Test
    public void testLibrasMovement() throws Exception {
        System.out.println("Libras Movement");

        MathEx.setSeed(19650218); // to get repeatable results.
        var libras = new LibrasMovement();
        var result = CrossValidation.classification(10, libras.x(), libras.y(), LogisticRegression::fit);

        System.out.println(result);
        assertEquals(0.7361, result.avg().accuracy(), 1E-4);
    }

    @Test
    public void testBreastCancer() throws Exception {
        System.out.println("Breast Cancer");

        MathEx.setSeed(19650218); // to get repeatable results.
        var cancer = new BreastCancer();
        var result = CrossValidation.classification(10, cancer.x(), cancer.y(),
                LogisticRegression::fit);

        System.out.println(result);
        assertEquals(0.9495, result.avg().accuracy(), 0.01);
    }

    @Test
    public void testSegment() throws Exception {
        System.out.println("Segment");
        var segment = new ImageSegmentation();
        double[][] x = segment.x();
        int[] y = segment.y();
        double[][] testx = segment.testx();
        int[] testy = segment.testy();
        LogisticRegression model = LogisticRegression.fit(x, y, new LogisticRegression.Options(0.05, 1E-3, 1000));

        int[] prediction = model.predict(testx);
        int error = Error.of(testy, prediction);
        System.out.println("Error = " + error);
        assertEquals(50, error, 1);

        int t = x.length;
        int round = (int) Math.round(Math.log(testx.length));
        for (int loop = 0; loop < round; loop++) {
            double eta = 0.1 / t;
            System.out.format("Set learning rate at %.5f%n", eta);
            model.setLearningRate(eta);
            for (int i = 0; i < testx.length; i++) {
                model.update(testx[i], testy[i]);
            }
            t += testx.length;
        }

        prediction = model.predict(testx);
        error = Error.of(testy, prediction);
        System.out.println("Error after online update = " + error);
        assertEquals(39, error, 3);
    }

    @Test
    public void testUSPS() throws Exception {
        System.out.println("USPS");
        var usps = new USPS();
        double[][] x = usps.x();
        int[] y = usps.y();
        double[][] testx = usps.testx();
        int[] testy = usps.testy();
        LogisticRegression model = LogisticRegression.fit(x, y, new LogisticRegression.Options(0.3, 1E-3, 1000));

        int[] prediction = model.predict(testx);
        int error = Error.of(testy, prediction);
        System.out.println("Error = " + error);
        assertEquals(189, error);

        int t = x.length;
        int round = (int) Math.round(Math.log(testx.length));
        for (int loop = 0; loop < round; loop++) {
            double eta = 0.1 / t;
            System.out.format("Set learning rate at %.5f%n", eta);
            model.setLearningRate(eta);
            for (int i = 0; i < testx.length; i++) {
                model.update(testx[i], testy[i]);
            }
            t += testx.length;
        }

        prediction = model.predict(testx);
        error = Error.of(testy, prediction);
        System.out.println("Error after online update = " + error);
        assertEquals(188, error);

        java.nio.file.Path temp = Write.object(model);
        Read.object(temp);
    }
}
