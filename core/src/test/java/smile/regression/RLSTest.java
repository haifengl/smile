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

package smile.regression;

import java.util.stream.IntStream;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.datasets.Abalone;
import smile.datasets.Bank32nh;
import smile.datasets.CalHousing;
import smile.io.Read;
import smile.io.Write;
import smile.test.data.*;
import smile.util.Index;
import smile.validation.CrossValidation;
import smile.validation.RegressionValidations;
import smile.validation.metric.RMSE;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Sam Erickson
 */
public class RLSTest {
    
    
    public RLSTest() {
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
    public void testLongley() throws Exception {
        System.out.println("longley");

        int n = Longley.data.size();
        DataFrame batch = Longley.data.get(Index.of(IntStream.range(0, n/2).toArray()));
        DataFrame online = Longley.data.get(Index.of(IntStream.range(n/2, n).toArray()));
        LinearModel model = OLS.fit(Longley.formula, batch);
        double[] prediction = model.predict(online);
        double rmse = RMSE.of(Longley.formula.y(online).toDoubleArray(), prediction);
        System.out.println("Batch RMSE = " + rmse);
        assertEquals(6.229663, rmse, 1E-4);

        model.update(online);
        prediction = model.predict(online);
        rmse = RMSE.of(Longley.formula.y(online).toDoubleArray(), prediction);
        System.out.println("Online RMSE = " + rmse);
        assertEquals(0.973663, rmse, 1E-4);

        java.nio.file.Path temp = Write.object(model);
        Read.object(temp);
    }

    /**
     * Test of learn method, of class LinearRegression.
     */
    @Test
    public void testProstate() {
        System.out.println("Prostate");

        LinearModel model = OLS.fit(Prostate.formula, Prostate.train);
        System.out.println(model);

        double[] prediction = model.predict(Prostate.test);
        double rmse = RMSE.of(Prostate.testy, prediction);
        System.out.println("RMSE on test data = " + rmse);
        assertEquals(0.721993, rmse, 1E-4);

        model.update(Prostate.test);
        prediction = model.predict(Prostate.test);
        rmse = RMSE.of(Prostate.testy, prediction);
        System.out.println("RMSE after online = " + rmse);
        assertEquals(0.643182, rmse, 1E-4);
    }

    /**
     * Test of online learn method of class OLS.
     */
    public void testOnlineLearn(String name, Formula formula, DataFrame data){
        System.out.println(name);

        RegressionValidations<LinearModel> result = CrossValidation.regression(10, formula, data, (f, x) -> {
            int n = x.size();
            DataFrame batch = x.get(Index.of(IntStream.range(0, n/2).toArray()));
            DataFrame online = x.get(Index.of(IntStream.range(n/2, n).toArray()));
            LinearModel model = OLS.fit(f, batch);
            model.update(online);
            return model;
        });

        System.out.println(result.avg);
    }
    
    @Test
    public void testOnlineLearn() throws Exception {
        var abalone = new Abalone();
        var bank32nh = new Bank32nh();
        var calHousing = new CalHousing();

        testOnlineLearn("CPU", CPU.formula, CPU.data);
        testOnlineLearn("2dplanes", Planes.formula, Planes.data);
        testOnlineLearn("abalone", abalone.formula(), abalone.train());
        testOnlineLearn("bank32nh", bank32nh.formula(), bank32nh.data());
        testOnlineLearn("cal_housing", calHousing.formula(), calHousing.data());
        testOnlineLearn("puma8nh", Puma8NH.formula, Puma8NH.data);
        testOnlineLearn("kin8nm", Kin8nm.formula, Kin8nm.data);
    }
}
