package smile.symbolic.internal;

/**
 * @author Ernest DeFoy
 */
public class Difference extends BinaryNode {

    public Difference(Expression left, Expression right) {

        super(left, right, BinaryOperator.MINUS);
    }

    @Override
    public Expression derive() {

        return new Difference(left.derive(), right.derive());
    }

    @Override
    public Expression reduce() {

        Expression l = left.reduce();
        Expression r = right.reduce();

        if(l instanceof Constant || r instanceof Constant) {

            if(l.getValue() == 0) { // Example: 0 - sin(x) => -sin(x)
                return new Negation(r);
            }
            if(r.getValue() == 0) { // Example: sin(x) - 0 => sin(x)
                return l;
            }

            if(l instanceof Constant && r instanceof Constant) {
                return new Constant(l.getValue() - r.getValue());
            }
        }

        if(l instanceof Negation) {
            return new Difference(r, l.getRightChild());
        }
        if(r instanceof Negation) {
            return new Sum(l, r.getRightChild());
        }

        return new Difference(l, r);
    }

    @Override
    public double getValue() {
        return 0;
    }
}
