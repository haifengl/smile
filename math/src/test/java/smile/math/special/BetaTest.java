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

package smile.math.special;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BetaTest {
    /**
     * Test that a value of x above the upper boundary throws exception
     */
    @Test(expected = IllegalArgumentException.class)
    public void testRegularizedIncompleteBetaFunctionXBeyondUpperBoundary() {
        Beta.regularizedIncompleteBetaFunction(1, 1, 1.00001);
    }

    /**
     * Test that a value of x below the lower boundary throws exception
     */
    @Test(expected = IllegalArgumentException.class)
    public void testRegularizedIncompleteBetaFunctionXBeyondLowerBoundary() {
        Beta.regularizedIncompleteBetaFunction(1, 1, -0.00001);
    }

    /**
     * Test that values of x within a reasonable distance from 0 and 1 are treated as 0 and 1,
     * to account for floating point errors.
     */
    @Test
    public void testRegularizedIncompleteBetaFunctionXWithinBoundaries() {
        double pHigh = Beta.regularizedIncompleteBetaFunction(1, 1, 1.000000000000001);
        assertEquals(1.0, pHigh, 0.0);

        double pLow = Beta.regularizedIncompleteBetaFunction(1, 1, -0.000000000000001);
        assertEquals(0.0, pLow, 0.0);
    }
}