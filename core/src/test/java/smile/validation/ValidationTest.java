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
package smile.validation;

import smile.classification.DecisionTree;
import smile.datasets.Abalone;
import smile.datasets.USPS;
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
    public void testUSPS() throws Exception {
        System.out.println("USPS");
        var usps = new USPS();
        var result = ClassificationValidation.of(usps.formula(), usps.train(), usps.test(), DecisionTree::fit);

        System.out.println(result);
        assertEquals(0.8340, result.metrics().accuracy(), 1E-4);
    }

    @Test
    public void testAbalone() throws Exception {
        System.out.println("Abalone");
        var abalone = new Abalone();
        var result = RegressionValidation.of(abalone.formula(), abalone.train(), abalone.test(), RegressionTree::fit);

        System.out.println(result);
        assertEquals(2.3194, result.metrics().rmse(), 1E-4);
        assertEquals(1.6840, result.metrics().mad(), 1E-4);
    }
}
