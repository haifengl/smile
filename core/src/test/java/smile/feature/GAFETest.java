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

package smile.feature;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import smile.classification.DecisionTree;
import smile.classification.LDA;
import smile.data.Abalone;
import smile.data.Segment;
import smile.data.USPS;
import smile.gap.BitString;
import smile.regression.RegressionTree;
import smile.validation.Accuracy;
import smile.math.MathEx;
import smile.validation.RMSE;

/**
 *
 * @author Haifeng Li
 */
public class GAFETest {
    
    public GAFETest() {
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
    public void testLDA() {
        System.out.println("LDA");

        MathEx.setSeed(19650218); // to get repeatable results.

        GAFE selection = new GAFE();
        BitString[] result = selection.apply(100, 20, 256,
                GAFE.fitness(USPS.x, USPS.y, USPS.testx, USPS.testy, new Accuracy(), (x, y) -> LDA.fit(x, y)));
            
        for (BitString bits : result) {
            System.out.format("%.2f%% %s%n", 100*bits.fitness(), bits);
        }

        assertEquals(0.8874, result[result.length-1].fitness(), 1E-4);
    }

    @Test
    public void testDecisionTree() {
        System.out.println("DecisionTree");

        MathEx.setSeed(19650218); // to get repeatable results.

        GAFE selection = new GAFE();
        BitString[] result = selection.apply(100, 20, Segment.train.ncols()-1,
                GAFE.fitness("class", Segment.train, Segment.test, new Accuracy(), (formula, data) -> DecisionTree.fit(formula, data)));

        for (BitString bits : result) {
            System.out.format("%.2f%% %s%n", 100*bits.fitness(), bits);
        }

        assertEquals(0.9654, result[result.length-1].fitness(), 1E-4);
    }

    @Test
    public void testRegressionTree() {
        System.out.println("RegressionTree");

        MathEx.setSeed(19650218); // to get repeatable results.

        GAFE selection = new GAFE();
        BitString[] result = selection.apply(100, 20, Abalone.train.ncols()-1,
                GAFE.fitness("rings", Abalone.train, Abalone.test, new RMSE(), (formula, data) -> RegressionTree.fit(formula, data)));

        for (BitString bits : result) {
            System.out.format("%.4f %s%n", -bits.fitness(), bits);
        }

        assertEquals(2.3840, -result[result.length-1].fitness(), 1E-4);
    }
}
