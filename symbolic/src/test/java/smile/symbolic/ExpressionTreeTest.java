/*******************************************************************************
 * Copyright (c) 2017
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

package smile.symbolic;

import java.util.ArrayList;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Ernest DeFoy
 */
public class ExpressionTreeTest {
    private ExpressionParser parser = new ExpressionParser();
    private ExpressionTree expressionTree = new ExpressionTree();

    @Test
    public void constructTree() throws InvalidExpressionException {

        parser.parse("sin(x) / 5*x");
        final ArrayList<String> QUERY = parser.getTokens();
        expressionTree.init("x", QUERY);
        final String EXPECTED = "x";
        final String actual = expressionTree.getRoot().getLeftChild().getRightChild().getType();

        assertEquals(EXPECTED, actual);
    }

    @Test
    public void constructTree2() throws InvalidExpressionException {

        parser.parse("sin(x) / (5+x)");
        final ArrayList<String> QUERY = parser.getTokens();
        expressionTree.init("x", QUERY);
        final String EXPECTED = "+";
        final String actual = expressionTree.getRoot().getRightChild().getType();

        assertEquals(EXPECTED, actual);
    }

    @Test
    public void treeToString() {
    }

    @Test
    public void createInfix() throws InvalidExpressionException {

        final String QUERY = "sin(x) / 5*x";
        final String EXPECTED = "sin(x)/5*x";
        parser.parse(QUERY);
        expressionTree.init(parser.getVar(), parser.getTokens());
        final String actual = expressionTree.toString();

        assertEquals(EXPECTED, actual);
    }

    @Test
    public void createInfix2() throws InvalidExpressionException {

        final String QUERY = "sin(x) / (5+x)";
        final String EXPECTED = "sin(x)/(5+x)";
        parser.parse(QUERY);
        expressionTree.init(parser.getVar(), parser.getTokens());
        final String actual = expressionTree.toString();

        assertEquals(EXPECTED, actual);
    }
}
