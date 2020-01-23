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

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.data.*;
import smile.math.MathEx;
import smile.validation.*;
import smile.validation.Error;

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

        int[] prediction = LOOCV.classification(Iris.x, Iris.y, (x, y) -> LogisticRegression.fit(x, y));
        int error = Error.of(Iris.y, prediction);
        System.out.println("Error = " + error);
        assertEquals(5, error);
    }

    @Test
    public void testWeather() {
        System.out.println("Weather");

        int[] prediction = LOOCV.classification(WeatherNominal.x, WeatherNominal.y, (x, y) -> LogisticRegression.fit(x, y));
        int error = Error.of(WeatherNominal.y, prediction);
        System.out.println("Error = " + error);
        assertEquals(8, error);
    }

    @Test
    public void testPenDigits() {
        System.out.println("Pen Digits");

        MathEx.setSeed(19650218); // to get repeatable results.
        int[] prediction = CrossValidation.classification(10, PenDigits.x, PenDigits.y, (x, y) -> LogisticRegression.fit(x, y));
        int error = Error.of(PenDigits.y, prediction);

        System.out.println("Error = " + error);
        assertEquals(339, error);
    }

    @Test
    public void testBreastCancer() {
        System.out.println("Breast Cancer");

        MathEx.setSeed(19650218); // to get repeatable results.
        int[] prediction = CrossValidation.classification(10, BreastCancer.x, BreastCancer.y, (x, y) -> LogisticRegression.fit(x, y));
        int error = Error.of(BreastCancer.y, prediction);

        System.out.println("Error = " + error);
        assertEquals(28, error);
    }

    @Test
    public void testSegment() {
        System.out.println("Segment");

        LogisticRegression model = LogisticRegression.fit(Segment.x, Segment.y, 0.05, 1E-3, 1000);

        int[] prediction = Validation.test(model, Segment.testx);
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

        prediction = Validation.test(model, Segment.testx);
        error = Error.of(Segment.testy, prediction);
        System.out.println("Error after online update = " + error);
        assertEquals(39, error);
    }

    @Test(expected = Test.None.class)
    public void testUSPS() throws Exception {
        System.out.println("USPS");

        LogisticRegression model = LogisticRegression.fit(USPS.x, USPS.y, 0.3, 1E-3, 1000);

        int[] prediction = Validation.test(model, USPS.testx);
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

        prediction = Validation.test(model, USPS.testx);
        error = Error.of(USPS.testy, prediction);
        System.out.println("Error after online update = " + error);
        assertEquals(184, error);

        java.nio.file.Path temp = smile.data.Serialize.write(model);
        smile.data.Serialize.read(temp);
    }
}