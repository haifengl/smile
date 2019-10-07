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

import smile.base.cart.SplitRule;
import smile.data.Iris;
import smile.data.USPS;
import smile.data.WeatherNominal;
import smile.validation.Error;
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
public class DecisionTreeTest {
    
    public DecisionTreeTest() {
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

        DecisionTree model = DecisionTree.fit(WeatherNominal.formula, WeatherNominal.data);
        System.out.println(model);

        double[] importance = model.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().get().fieldName(i), importance[i]);
        }

        int[] prediction = LOOCV.classification(WeatherNominal.data, x -> DecisionTree.fit(WeatherNominal.formula, x));
        int error = Error.apply(WeatherNominal.y, prediction);
        System.out.println("Error = " + error);
        assertEquals(5, error);
    }

    @Test
    public void testIris() {
        System.out.println("Iris");

        DecisionTree model = DecisionTree.fit(Iris.formula, Iris.data);
        System.out.println(model);

        double[] importance = model.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().get().fieldName(i), importance[i]);
        }

        int[] prediction = LOOCV.classification(Iris.data, x -> DecisionTree.fit(Iris.formula, x));
        int error = Error.apply(Iris.y, prediction);
        System.out.println("Error = " + error);
        assertEquals(9, error);
    }

    @Test
    public void testUSPS() {
        System.out.println("USPS");

        DecisionTree model = DecisionTree.fit(USPS.formula, USPS.train, SplitRule.ENTROPY, 350, 5);
        System.out.println(model);

        double[] importance = model.importance();
        for (int i = 0; i < importance.length; i++) {
            System.out.format("%-15s %.4f%n", model.schema().get().fieldName(i), importance[i]);
        }

        int[] prediction = Validation.test(model, USPS.test);
        int error = Error.apply(USPS.testy, prediction);

        System.out.println("Error = " + error);
        assertEquals(331, error);
    }
}
