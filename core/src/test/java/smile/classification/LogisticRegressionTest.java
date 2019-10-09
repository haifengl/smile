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
import smile.data.Iris;
import smile.data.Segment;
import smile.data.USPS;
import smile.data.WeatherNominal;
import smile.math.MathEx;
import smile.validation.Accuracy;
import smile.validation.Error;
import smile.validation.LOOCV;
import smile.validation.Validation;

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
        int error = Error.apply(Iris.y, prediction);
        System.out.println("Error = " + error);
        assertEquals(7, error);
    }

    @Test
    public void testWeather() {
        System.out.println("Weather");

        int[] prediction = LOOCV.classification(WeatherNominal.x, WeatherNominal.y, (x, y) -> LogisticRegression.fit(x, y));
        int error = Error.apply(Iris.y, prediction);
        System.out.println("Error = " + error);
        assertEquals(7, error);
    }

    @Test
    public void testSegment() {
        System.out.println("Segment");

        int[] prediction = LOOCV.classification(Segment.x, Segment.y, (x, y) -> LogisticRegression.fit(x, y));
        int error = Error.apply(Iris.y, prediction);
        System.out.println("Error = " + error);
        assertEquals(7, error);
    }

    @Test
    public void testUSPS() {
        System.out.println("USPS");

        int[] prediction = LOOCV.classification(USPS.x, USPS.y, (x, y) -> LogisticRegression.fit(x, y));
        int error = Error.apply(USPS.y, prediction);
        System.out.println("Error = " + error);
        assertEquals(7, error);
    }
}