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

package smile.regression;

import smile.base.cart.Loss;
import smile.data.*;
import smile.data.formula.Formula;
import smile.math.MathEx;
import smile.regression.treeshap.TreeShapImportance;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 *
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

    public void test(Loss loss, String name, Formula formula, DataFrame data, String mostImportantByShap, 
    		         int ntrees, double shrinkage, double subsample) {
        System.out.println(name + "\t" + loss);

        MathEx.setSeed(19650218); // to get repeatable results.
        GradientTreeBoost model = GradientTreeBoost.fit(formula, data, loss, ntrees, 3, 10, 5, shrinkage, subsample);
        
        TreeShapImportance shapExplainer = new TreeShapImportance(model.trees(), true);        
        double[] meanShap = shapExplainer.shapImportance(model.schema(), data);
		int[] shapAsc = smile.sort.QuickSort.sort(meanShap);
        System.out.println("----- tree shap importance (sorted) -----");
        for (int i = meanShap.length - 1;i >= 0;i--) {
             System.out.format("%-15s %.4f%n", model.schema().fieldName(shapAsc[i]), (meanShap[i] / data.size()));
        }
        assertTrue(model.schema().fieldName(shapAsc[meanShap.length - 1]).equalsIgnoreCase(mostImportantByShap));

        // as comparison, also check split importance
        double[] importance = model.importance();
        System.out.println("----- tree split importance (sorted) -----");
        int[] splitImportanceAsc = smile.sort.QuickSort.sort(importance);
        for (int i = importance.length - 1;i >= 0;i--) {
            System.out.format("%-15s %.4f%n", model.schema().fieldName(splitImportanceAsc[i]), importance[i]);
        }        
    }

    @Test
    public void testLS() {
        test(Loss.ls(), "cervicalCancer", CervicalCancer.formula, CervicalCancer.data, "Hormonal Contraceptives (years)", 5000, 0.001, 0.5);
        test(Loss.ls(), "NHANES-I", NHANES.formula, NHANES.data, "Age", 5000, 0.001, 0.5);
        test(Loss.ls(), "Boston Housing", BostonHousing.formula, BostonHousing.data, "LSTAT", 100, 0.01, 1);
    }
}
