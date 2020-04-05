/*******************************************************************************
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
 ******************************************************************************/

package smile.feature;

import smile.base.cart.Loss;
import smile.data.*;
import smile.math.MathEx;
import smile.regression.GradientTreeBoost;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ray
 */
public class TreeShapTest {

    public TreeShapTest() {
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
    public void test() {
        List<String> sortedFields = new ArrayList<>();
        MathEx.setSeed(19650218); // to get repeatable results.
        GradientTreeBoost model = GradientTreeBoost.fit(BostonHousing.formula, BostonHousing.data, Loss.ls(), 100, 3, 10, 5, 0.01, 0.7);

        EnsembleTreeSHAP shapExplainer = new EnsembleTreeSHAP(model.trees());
        double[] shap = shapExplainer.shapImportance(model.schema(), BostonHousing.data);
        String[] fields = java.util.Arrays.stream(model.schema().fields()).map(field -> field.name).toArray(String[]::new);
        smile.sort.QuickSort.sort(shap, fields);

        System.out.println("----- SHAP importance -----");
        for (int i = shap.length - 1; i >= 0; i--) {
            System.out.format("%-15s %.4f%n", fields[i], shap[i]);
        }

        assertTrue(sortedFields.get(0).equals("LSTAT"));
        assertEquals(2.3119, shap[shap.length - 1], 1E-4);
        assertTrue(sortedFields.get(1).equals("RM"));
        assertEquals(1.5829, shap[shap.length - 2], 1E-4);
        assertTrue(sortedFields.get(2).equals("NOX"));
        assertEquals(0.2043, shap[shap.length - 3], 1E-4);
    }
}
