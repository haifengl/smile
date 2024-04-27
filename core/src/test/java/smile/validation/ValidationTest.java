/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.validation;

import smile.classification.DecisionTree;
import smile.test.data.Abalone;
import smile.test.data.USPS;
import smile.regression.RegressionTree;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng
 */
public class ValidationTest {
    
    public ValidationTest() {
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
    public void testUSPS() {
        System.out.println("USPS");
        ClassificationValidation<DecisionTree> result = ClassificationValidation.of(USPS.formula, USPS.train, USPS.test,
                (formula, data) -> DecisionTree.fit(formula, data));

        System.out.println(result);
        assertEquals(0.8340, result.metrics.accuracy, 1E-4);
    }

    @Test
    public void testAbalone() {
        System.out.println("Abalone");
        RegressionValidation<RegressionTree> result = RegressionValidation.of(Abalone.formula, Abalone.train, Abalone.test,
                (formula, data) -> RegressionTree.fit(formula, data));

        System.out.println(result);
        assertEquals(2.5567, result.metrics.rmse, 1E-4);
        assertEquals(1.8666, result.metrics.mad, 1E-4);
    }
}
