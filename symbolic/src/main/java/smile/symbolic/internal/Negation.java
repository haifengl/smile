package smile.symbolic.internal;

/**
 * @author Ernest DeFoy
 */
public class Negation extends UnaryNode {

    public Negation(Expression exp) {

        super(exp, UnaryOperator.NEGATE);
    }

    @Override
    public Expression derive() {

        if(exp instanceof Cosine)
            return exp.derive();

        return new Negation(exp.derive());
    }

    @Override
    public Expression reduce() {

        Expression e = exp.reduce();

        if(e instanceof Negation) {
            return e.getRightChild();
        }

        return new Negation(e);
    }

    @Override
    public double getValue() {
        return 0;
    }
}
