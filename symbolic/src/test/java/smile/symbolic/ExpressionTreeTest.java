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

import java.util.ArrayList;
import smile.symbolic.BaseTest;
import smile.symbolic.InvalidExpressionException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author Ernest DeFoy
 */
public class ExpressionTreeTest extends BaseTest {

    @Test
    void constructTree() throws InvalidExpressionException {

        parser.parse("sin(x) / 5*x");
        final ArrayList<String> QUERY = parser.getTokens();
        expressionTree.init("x", QUERY);
        final String EXPECTED = "x";
        final String actual = expressionTree.getRoot().getLeftChild().getRightChild().getType();

        Assertions.assertEquals(EXPECTED, actual);
    }

    @Test
    void constructTree2() throws InvalidExpressionException {

        parser.parse("sin(x) / (5+x)");
        final ArrayList<String> QUERY = parser.getTokens();
        expressionTree.init("x", QUERY);
        final String EXPECTED = "+";
        final String actual = expressionTree.getRoot().getRightChild().getType();

        Assertions.assertEquals(EXPECTED, actual);
    }

    @Test
    void treeToString() {
    }

    @Test
    void createInfix() throws InvalidExpressionException {

        final String QUERY = "sin(x) / 5*x";
        final String EXPECTED = "sin(x)/5*x";
        parser.parse(QUERY);
        expressionTree.init(parser.getVar(), parser.getTokens());
        final String actual = expressionTree.toString();

        Assertions.assertEquals(EXPECTED, actual);
    }

    @Test
    void createInfix2() throws InvalidExpressionException {

        final String QUERY = "sin(x) / (5+x)";
        final String EXPECTED = "sin(x)/(5+x)";
        parser.parse(QUERY);
        expressionTree.init(parser.getVar(), parser.getTokens());
        final String actual = expressionTree.toString();

        Assertions.assertEquals(EXPECTED, actual);
    }
}
