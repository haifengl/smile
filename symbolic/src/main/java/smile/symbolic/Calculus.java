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
 * <code>Calculus</code> computes symbolic derivatives and rewrites expressions.
 *
 * @author Ernest DeFoy
 */
public class Calculus {

    /**
     * Compute the symbolic derivative.
     * @param expression the mathematical expression
     */
    public static String diff(String expression) throws InvalidExpressionException {

        ExpressionTree expTree = parseToTree(expression);
        expTree.derive();
        expTree.reduce();

        return expTree.toString();
    }

    /**
     * Compute numeric derivative
     * @param expression the mathematical expression
     * @param val the value for which to evaluate the expression at
     * @return numeric derivative
     */
    public static final double diff(String expression, double val) throws InvalidExpressionException {

        ExpressionTree expTree = parseToTree(expression);

        expTree.derive();
        expTree.reduce();

        return expTree.getVal();
    }

    /**
     * Compute the reformatted symbolic derivative.
     * @param expression the mathematical expressino
     * @return "readable" form of the derived expression.  Unnecessary * are deleted and
     *          unary minus is changed from $ to -.
     */
    public static String diffReadable(String expression) throws InvalidExpressionException {

        ExpressionParser p = new ExpressionParser();

        return p.format(diff(expression));
    }

    /**
     * Rewrite the expression to eliminate redundant terms and simplify the expression.
     * @param expression
     * @return
     * @throws InvalidExpressionException
     */
    public static String rewrite(String expression) throws InvalidExpressionException {

        ExpressionTree expTree = parseToTree(expression);

        expTree.reduce();

        return expTree.toString();
    }

    /**
     * Evaluate an expression for a given value
     * @param expression
     * @return
     * @throws InvalidExpressionException
     */
    public static final double evaluate(String expression) throws InvalidExpressionException {

        return 0.0;
    }

    /**
     * Parse a mathematical expression and form a binary expression tree.
     * @param expression
     * @return
     * @throws InvalidExpressionException
     */
    private static final ExpressionTree parseToTree(String expression) throws InvalidExpressionException {

        ExpressionParser parser = new ExpressionParser();
        parser.parse(expression);

        return new ExpressionTree(parser.getVar(), parser.getTokens());
    }
}
