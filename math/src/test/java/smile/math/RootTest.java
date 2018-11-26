/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package smile.math;

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
public class RootTest {

    public RootTest() {
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
     * Test of find method, of class Root.
     */
    @Test
    public void testBrent() {
        System.out.println("Brent");
        double result = Root.getInstance().find(x -> x * x * x + x * x - 5 * x + 3, -4, -2);
        assertEquals(-3, result, 1E-7);
    }

    /**
     * Test of find method, of class Root.
     */
    @Test
    public void testNewton() {
        System.out.println("Newton");
        Function func = new DifferentiableFunction() {

            @Override
            public double f(double x) {
                return x * x * x + x * x - 5 * x + 3;
            }

            @Override
            public double g(double x) {
                return 3 * x * x + 2 * x - 5;
            }
        };
        double result = Root.getInstance().find(func, -4, -2);
        assertEquals(-3, result, 1E-7);
    }
}
