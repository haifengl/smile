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

import smile.data.Iris;
import smile.data.USPS;
import smile.data.WeatherNominal;
import smile.validation.LOOCV;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.validation.Validation;

import static org.junit.Assert.*;

/**
 *
 * @author Haifeng
 */
public class AdaBoostTest {
    
    public AdaBoostTest() {
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
    public void testWeather() {
        System.out.println("Weather");

        AdaBoost model = AdaBoost.fit(WeatherNominal.formula, WeatherNominal.data, 200, 4, 1);

        double[] importance = model.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().get().fieldName(i), importance[i]);
        }

        int error = LOOCV.classification(WeatherNominal.data, x -> AdaBoost.fit(WeatherNominal.formula, x, 200, 4, 1));
        System.out.println("Error = " + error);
        assertEquals(4, error);
    }

    @Test
    public void testIris() {
        System.out.println("Iris");

        AdaBoost model = AdaBoost.fit(Iris.formula, Iris.data, 200, 4, 5);

        double[] importance = model.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().get().fieldName(i), importance[i]);
        }

        int error = LOOCV.classification(Iris.data, x -> AdaBoost.fit(Iris.formula, x, 200, 4, 1));
        System.out.println("Error = " + error);
        assertEquals(7, error);
    }

    @Test
    public void testUSPS() {
        System.out.println("USPS");

        AdaBoost model = AdaBoost.fit(USPS.formula, USPS.train, 200, 64, 1);

        double[] importance = model.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().get().fieldName(i), importance[i]);
        }

        double accuracy = Validation.test(model, USPS.test);
        System.out.format("Accuracy = %.4f%n", accuracy);
        assertEquals(0.8236, accuracy, 1E-3);
    }
}
