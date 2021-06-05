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

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.data.*;
import smile.math.MathEx;
import smile.validation.*;
import smile.validation.metric.Error;

import static org.junit.Assert.*;

/**
 *
 * @author Haifeng
 */
public class LogisticRegressionTest {

    public LogisticRegressionTest() {
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
    public void testIris() {
        System.out.println("Iris");

        ClassificationMetrics metrics = LOOCV.classification(Iris.x, Iris.y, LogisticRegression::fit);

        System.out.println(metrics);
        assertEquals(0.9667, metrics.accuracy, 1E-4);
    }

    @Test
    public void testWeather() {
        System.out.println("Weather");

        ClassificationMetrics metrics = LOOCV.classification(WeatherNominal.dummy, WeatherNominal.y, LogisticRegression::fit);

        System.out.println(metrics);
        assertEquals(0.7143, metrics.accuracy, 1E-4);
    }

    @Test
    public void testPenDigits() {
        System.out.println("Pen Digits");

        MathEx.setSeed(19650218); // to get repeatable results.
        ClassificationValidations<LogisticRegression> result = CrossValidation.classification(10, PenDigits.x, PenDigits.y, LogisticRegression::fit);

        System.out.println(result);
        assertEquals(0.9548, result.avg.accuracy, 1E-4);
    }

    @Test
    public void testLibrasMovement() {
        System.out.println("Libras Movement");

        MathEx.setSeed(19650218); // to get repeatable results.
        ClassificationValidations<LogisticRegression> result = CrossValidation.classification(10, LibrasMovement.x, LibrasMovement.y, LogisticRegression::fit);

        System.out.println(result);
        assertEquals(0.7361, result.avg.accuracy, 1E-4);
    }

    @Test
    public void testBreastCancer() {
        System.out.println("Breast Cancer");

        MathEx.setSeed(19650218); // to get repeatable results.
        ClassificationValidations<LogisticRegression> result = CrossValidation.classification(10, BreastCancer.x, BreastCancer.y,
                LogisticRegression::fit);

        System.out.println(result);
        assertEquals(0.9495, result.avg.accuracy, 1E-3);
    }

    @Test
    public void testSegment() {
        System.out.println("Segment");

        LogisticRegression model = LogisticRegression.fit(Segment.x, Segment.y, 0.05, 1E-3, 1000);

        int[] prediction = model.predict(Segment.testx);
        int error = Error.of(Segment.testy, prediction);
        System.out.println("Error = " + error);
        assertEquals(50, error);

        int t = Segment.x.length;
        int round = (int) Math.round(Math.log(Segment.testx.length));
        for (int loop = 0; loop < round; loop++) {
            double eta = 0.1 / t;
            System.out.format("Set learning rate at %.5f%n", eta);
            model.setLearningRate(eta);
            for (int i = 0; i < Segment.testx.length; i++) {
                model.update(Segment.testx[i], Segment.testy[i]);
            }
            t += Segment.testx.length;
        }

        prediction = model.predict(Segment.testx);
        error = Error.of(Segment.testy, prediction);
        System.out.println("Error after online update = " + error);
        assertEquals(39, error);
    }

    @Test
    public void testUSPS() throws Exception {
        System.out.println("USPS");

        LogisticRegression model = LogisticRegression.fit(USPS.x, USPS.y, 0.3, 1E-3, 1000);

        int[] prediction = model.predict(USPS.testx);
        int error = Error.of(USPS.testy, prediction);
        System.out.println("Error = " + error);
        assertEquals(185, error);

        int t = USPS.x.length;
        int round = (int) Math.round(Math.log(USPS.testx.length));
        for (int loop = 0; loop < round; loop++) {
            double eta = 0.1 / t;
            System.out.format("Set learning rate at %.5f%n", eta);
            model.setLearningRate(eta);
            for (int i = 0; i < USPS.testx.length; i++) {
                model.update(USPS.testx[i], USPS.testy[i]);
            }
            t += USPS.testx.length;
        }

        prediction = model.predict(USPS.testx);
        error = Error.of(USPS.testy, prediction);
        System.out.println("Error after online update = " + error);
        assertEquals(184, error);

        java.nio.file.Path temp = smile.data.Serialize.write(model);
        smile.data.Serialize.read(temp);
    }
}