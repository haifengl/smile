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

import smile.symbolic.Calculus;
import smile.symbolic.InvalidExpressionException;
import smile.symbolic.BaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

/**
 * @author Ernest DeFoy
 */
public class CalcTest extends BaseTest {

    @Test
    void diffReadable() throws InvalidExpressionException {

        final String QUERY = "0*x^2+4*1*x^(2-1)+1*x^(3-1)*cot(x^3)";
        final String EXPECTED = "4*x+x^2*cot(x^3)";
        String actual = Calculus.diffReadable(QUERY);

        Assertions.assertEquals(EXPECTED, actual);
    }

    @Test
    void reduce() throws InvalidExpressionException {
        final String QUERY = "0*x^2+4*1*x^(2-1)+1*x^(3-1)*cot(x^3)";
        final String EXPECTED = "4*x+x^2*cot(x^3)";
        String actual = Calculus.rewrite(QUERY);

        Assertions.assertEquals(EXPECTED, actual);
    }

    //rewrite SOMETHING
    @Test
    void reduce2() throws InvalidExpressionException {

        final String QUERY = "4*5*x";
        final String EXPECTED = "20*x";
        String actual = Calculus.rewrite(QUERY);

        Assertions.assertEquals(EXPECTED, actual);
    }

    // Power Rule
    @Test
    void diff() throws InvalidExpressionException {

        final String QUERY = "4*x^2";
        final String EXPECTED = "8*x";
        String actual = Calculus.diff(QUERY);

        Assertions.assertEquals(EXPECTED, actual);
    }

    // Cosecant Identity & Chain Rule
    @Test
    void diff2() throws InvalidExpressionException {

        final String QUERY = "-csc(x^3)";
        final String EXPECTED = "3*x^2*cot(x^3)*csc(x^3)";
        String actual = Calculus.diff(QUERY);

        Assertions.assertEquals(EXPECTED, actual);
    }

    // Negative Exponent
    @Test
    void diff4() throws InvalidExpressionException {

        final String QUERY = "1 / (x+2)";
        final String EXPECTED = "-1/(x+2)(x+2)";
        String actual = calculus.diff(QUERY);

        Assertions.assertEquals(EXPECTED, actual);
    }

    // Negative Exponent
    @Test
    void diff5() throws InvalidExpressionException {

        final String QUERY = "1 / (x^2)";
        final String EXPECTED = "-2/x^3";
        String actual = calculus.diff(QUERY);

        Assertions.assertEquals(EXPECTED, actual);
    }

    @Test
    void evaluate() {
    }
}

