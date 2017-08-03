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

/**
 * @author Ernest DeFoy
 */
public class Calculus {

    public static String diff(String expression) throws InvalidExpressionException {

        ExpressionTree expTree = parseToTree(expression);
expTree.printTree();
        expTree.derive();
expTree.printTree();
        expTree.reduce();

        return expTree.toString();
    }

    public static final double diff(String expression, double val) throws InvalidExpressionException {

        ExpressionTree expTree = parseToTree(expression);

        expTree.derive();
        expTree.reduce();

        return expTree.getVal();
    }

    public static String diffReadable(String expression) throws InvalidExpressionException {

        ExpressionParser p = new ExpressionParser();

        return p.format(diff(expression));
    }

    public static String rewrite(String expression) throws InvalidExpressionException {

        ExpressionTree expTree = parseToTree(expression);

        expTree.reduce();

        return expTree.toString();
    }

    public static final double evaluate(String expression) throws InvalidExpressionException {

        return 0.0;
    }

    private static final ExpressionTree parseToTree(String expression) throws InvalidExpressionException {

        ExpressionParser parser = new ExpressionParser();
        parser.parse(expression);

        return new ExpressionTree(parser.getVar(), parser.getTokens());
    }
}
