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

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.data.*;
import smile.data.formula.Formula;
import smile.validation.CrossValidation;
import smile.validation.RMSE;
import smile.validation.Validation;

import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

/**
 *
 * @author Sam Erickson
 */
public class RLSTest {
    
    
    public RLSTest() {
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

    @Test(expected = Test.None.class)
    public void testLongley() throws Exception {
        System.out.println("longley");

        int n = Longley.data.size();
        DataFrame batch = Longley.data.of(IntStream.range(0, n/2).toArray());
        DataFrame online = Longley.data.of(IntStream.range(n/2, n).toArray());
        LinearModel model = OLS.fit(Longley.formula, batch);
        double[] prediction = Validation.test(model, online);
        double rmse = RMSE.of(Longley.formula.y(online).toDoubleArray(), prediction);
        System.out.println("Batch RMSE = " + rmse);
        assertEquals(6.229663, rmse, 1E-4);

        model.update(online);
        prediction = Validation.test(model, online);
        rmse = RMSE.of(Longley.formula.y(online).toDoubleArray(), prediction);
        System.out.println("Online RMSE = " + rmse);
        assertEquals(0.973663, rmse, 1E-4);

        java.nio.file.Path temp = smile.data.Serialize.write(model);
        smile.data.Serialize.read(temp);
    }

    /**
     * Test of learn method, of class LinearRegression.
     */
    @Test
    public void testProstate() {
        System.out.println("Prostate");

        LinearModel model = OLS.fit(Prostate.formula, Prostate.train);
        System.out.println(model);

        double[] prediction = Validation.test(model, Prostate.test);
        double rmse = RMSE.of(Prostate.testy, prediction);
        System.out.println("RMSE on test data = " + rmse);
        assertEquals(0.721993, rmse, 1E-4);

        model.update(Prostate.test);
        prediction = Validation.test(model, Prostate.test);
        rmse = RMSE.of(Prostate.testy, prediction);
        System.out.println("RMSE after online = " + rmse);
        assertEquals(0.643182, rmse, 1E-4);
    }

    /**
     * Test of online learn method of class OLS.
     */
    public void testOnlineLearn(String name, Formula formula, DataFrame data){
        System.out.println(name);

        double[] prediction = CrossValidation.regression(10, formula, data, (f, x) -> {
            int n = x.size();
            DataFrame batch = x.of(IntStream.range(0, n/2).toArray());
            DataFrame online = x.of(IntStream.range(n/2, n).toArray());
            LinearModel model = OLS.fit(f, batch);
            model.update(online);
            return model;
        });
        double rmse = RMSE.of(formula.y(data).toDoubleArray(), prediction);
        System.out.format("10-CV RMSE = %.4f%n", rmse);
    }
    
    @Test
    public void testOnlineLearn() {
        testOnlineLearn("CPU", CPU.formula, CPU.data);
        testOnlineLearn("2dplanes", Planes.formula, Planes.data);
        testOnlineLearn("abalone", Abalone.formula, Abalone.train);
        testOnlineLearn("bank32nh", Bank32nh.formula, Bank32nh.data);
        testOnlineLearn("cal_housing", CalHousing.formula, CalHousing.data);
        testOnlineLearn("puma8nh", Puma8NH.formula, Puma8NH.data);
        testOnlineLearn("kin8nm", Kin8nm.formula, Kin8nm.data);
    }
}
