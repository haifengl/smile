

package smile.symbolic;

/**
 * Calculus computes symbolic derivatives and rewrites expressions.
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
expTree.printTree();
        expTree.derive();
expTree.printTree();
        expTree.reduce();

        return expTree.toString();
    }

    /**
     * Compute numeric derivative
     * @param expression the mathematical expression
     * @param val the value for which to evaluate the expression at
     * @return
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
