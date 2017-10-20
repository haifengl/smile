package smile.symbolic.internal;

/**
 * @author Ernest DeFoy
 */
public abstract class UnaryNode extends Expression {

    protected Expression exp;
    protected UnaryOperator type;

    public UnaryNode(Expression exp, UnaryOperator type) {
        this.exp = exp;
        this.type = type;
    }

    public String getType() {

        return type.toString();
    }

    @Override
    public Expression getLeftChild() {
        return null;
    }

    @Override
    public Expression getRightChild() {
        return exp;
    }
}
