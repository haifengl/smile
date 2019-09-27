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
import smile.data.Abalone;
import smile.data.CPU;
import smile.data.DataFrame;
import smile.data.Planes;
import smile.data.formula.Formula;
import smile.math.MathEx;
import smile.validation.CrossValidation;

import javax.sound.sampled.Line;
import java.util.stream.IntStream;

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

    /**
     * Test of online learn method of class OLS.
     */
    public void testOnlineLearn(String name, Formula formula, DataFrame data){
        System.out.println(name);

        double rss = CrossValidation.test(10, data, x -> {
            int n = x.size();
            DataFrame batch = x.of(IntStream.range(0, n/2).toArray());
            DataFrame online = x.of(IntStream.range(n/2, n).toArray());
            LinearModel model = OLS.fit(formula, batch);
            online.stream().forEach(t -> model.update(t));
            return model;
        });
        System.out.format("10-CV RMSE = %.4f%n", rss);
    }
    
    @Test
    public void testOnlineLearn() {
        testOnlineLearn("CPU", CPU.formula, CPU.data);
        testOnlineLearn("2dplanes", Planes.formula, Planes.data);
        testOnlineLearn("abalone", Abalone.formula, Abalone.train);
        //testOnlineLearn(true, "bank32nh", "weka/regression/bank32nh.arff", 32);
        //testOnlineLearn(true, "cal_housing", "weka/regression/cal_housing.arff", 8);
        //testOnlineLearn(true, "puma8nh", "weka/regression/puma8nh.arff", 8);
        //testOnlineLearn(true, "kin8nm", "weka/regression/kin8nm.arff", 8);
    }
}
