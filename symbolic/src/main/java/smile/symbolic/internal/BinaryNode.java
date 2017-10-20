

package smile.symbolic.internal;

/**
 * A node for binary operations.
 *
 * @author Ernest DeFoy
 */
abstract class BinaryNode extends Expression {

    protected Expression left;
    protected Expression right;
    protected BinaryOperator type;

    BinaryNode(Expression left, Expression right, BinaryOperator type) {

        this.left = left;
        this.right = right;
        this.type = type;
    }

    public String getType() {

        return type.toString();
    }

    @Override
    public Expression getLeftChild() {
        return left;
    }

    @Override
    public Expression getRightChild() {
        return right;
    }
}

