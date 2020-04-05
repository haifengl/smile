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

import smile.base.cart.Loss;
import smile.data.*;
import smile.data.formula.Formula;
import smile.feature.EnsembleTreeSHAP;
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

	public double[] test(Loss loss, String name, Formula formula,
			DataFrame data, List<String> shapSortedFields, int ntrees,
			double shrinkage, double subsample) {
		System.out.println(name + "\t" + loss);

		MathEx.setSeed(19650218); // to get repeatable results.
		GradientTreeBoost model = GradientTreeBoost.fit(formula, data, loss, ntrees, 3, 10, 5, shrinkage, subsample);

		EnsembleTreeSHAP shapExplainer = new EnsembleTreeSHAP(model.trees());
		double[] meanShap = shapExplainer.shapImportance(model.schema(), data);
		int[] shapAsc = smile.sort.QuickSort.sort(meanShap);
		System.out.println("----- GradientTreeBoost regression model shap importance (sorted) -----");
		for (int i = meanShap.length - 1; i >= 0; i--) {
			String fn = model.schema().fieldName(shapAsc[i]);
			System.out.format("%-15s %.4f%n", fn, meanShap[i]);
			shapSortedFields.add(fn);
		}
		return meanShap;
	}

	@Test
	public void testBostonHousing() {
		List<String> sortedFields = new ArrayList<String>();
		double[] shaps = test(Loss.ls(), "Boston Housing", BostonHousing.formula, BostonHousing.data, sortedFields, 100, 0.01, 1);
		assertTrue(sortedFields.get(0).equals("LSTAT"));
		assertEquals(2.3119, shaps[shaps.length - 1], 1E-4);
		assertTrue(sortedFields.get(1).equals("RM"));
		assertEquals(1.5829, shaps[shaps.length - 2], 1E-4);
		assertTrue(sortedFields.get(2).equals("NOX"));
		assertEquals(0.2043, shaps[shaps.length - 3], 1E-4);
	}
}
