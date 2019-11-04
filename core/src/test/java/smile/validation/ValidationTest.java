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

package smile.validation;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;
import smile.classification.DecisionTree;
import smile.data.Abalone;
import smile.data.USPS;
import smile.regression.RegressionTree;

/**
 *
 * @author Haifeng
 */
public class ValidationTest {
    
    public ValidationTest() {
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
    public void testUSPS() {
        System.out.println("USPS");
        DecisionTree model = DecisionTree.fit(USPS.formula, USPS.train);
        int[] prediction = Validation.test(model, USPS.test);
        double accuracy = Accuracy.of(USPS.testy, prediction);
        System.out.println("accuracy = " + accuracy);
        assertEquals(0.8340, accuracy, 1E-4);
    }

    @Test
    public void testCPU() {
        System.out.println("Abalone");
        RegressionTree model = RegressionTree.fit(Abalone.formula, Abalone.train);
        double[] prediction = Validation.test(model, Abalone.test);
        double rmse = RMSE.of(Abalone.testy, prediction);
        double mad = MeanAbsoluteDeviation.of(Abalone.testy, prediction);
        System.out.println("RMSE = " + rmse);
        System.out.println("MAD = " + mad);
        assertEquals(2.5567, rmse, 1E-4);
        assertEquals(1.8666, mad, 1E-4);
    }
}
