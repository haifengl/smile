/*******************************************************************************
 * Copyright (c) 2017 Ernest DeFoy
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

package smile.symbolic.mathematicsTest;

import smile.symbolic.InvalidExpressionException;
import smile.symbolic.Calculus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

/**
 * @author Ernest DeFoy
 */
public class CalculusTest {

    @Test
    void diffReadable() throws InvalidExpressionException {

        final String QUERY = "0*x^2+4*1*x^(2-1)+1*x^(3-1)*cot(x^3)";
        final String EXPECTED = "4*x+x^2*cot(x^3)";
        String actual = Calculus.diffReadable(QUERY);

        Assertions.assertEquals(EXPECTED, actual);
    }

    void reduce(final String QUERY, final String EXPECTED) throws InvalidExpressionException {

        String actual = Calculus.rewrite(QUERY);

        Assertions.assertEquals(EXPECTED, actual);
    }

    @Test
    public void testReduce() throws InvalidExpressionException {

        reduce("0*x^2+4*1*x^(2-1)+1*x^(3-1)*cot(x^3)", "4*x+x^2*cot(x^3)");
        reduce("4*5*x", "20*x");
    }

    public void diff(final String QUERY, final String EXPECTED) throws InvalidExpressionException {

        String actual = Calculus.diff(QUERY);

        Assertions.assertEquals(EXPECTED, actual);
    }

    @Test
    public void testDiff() throws InvalidExpressionException {

        // Power Rule
        diff("4*x^2", "8*x");
        // Product Rule
        // Quotient Rule
        diff("sin(x) / cos(x)", "sec(x)^2");
        // Cosecant Identity & Chain Rule
        diff("-csc(x^3)", "3*x^2*cot(x^3)*csc(x^3)");
        // Negative Exponent
        diff("1 / (x+2)", "-1/(x+2)(x+2)");
        // Negative Exponent 2
        diff("1 / (x^2)", "-2/x^3");
    }

    @Test
    void evaluate() {
    }
}
