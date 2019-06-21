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

package smile.sort;

import smile.math.MathEx;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class IQAgentTest {

    public IQAgentTest() {
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
     * Test of add method, of class IQAgent.
     */
    @Test
    public void testAdd() {
        System.out.println("IQAgent");

        double[] data = new double[100000];
        for (int i = 0; i < data.length; i++)
            data[i] = i+1;

        MathEx.permutate(data);
        
        IQAgent instance = new IQAgent();
        for (int i = 0; i < data.length; i++)
            instance.add(data[i]);

        for (int i = 1; i <= 100; i++) {
            System.out.println(i + "%\t" + instance.quantile(i/100.0) + "\t" + Math.abs(1-instance.quantile(i/100.0)/(i*1000)));
            assertTrue(Math.abs(1-instance.quantile(i/100.0)/(i*1000)) < 0.01);
        }
    }
}