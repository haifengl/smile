package smile.symbolic.internal;

/**
 * @author Ernest DeFoy
 */
public class Sum extends BinaryNode {

    public Sum(Expression left, Expression right) {

        super(left, right, BinaryOperator.PLUS);
    }

    @Override
    public Expression derive() {
        return new Sum(left.derive(), right.derive());
    }

    @Override
    public Expression reduce() {

        Expression l = left.reduce();
        Expression r = right.reduce();

        if(l instanceof Constant || r instanceof Constant) {
            if(l.getType().equals("0")) {
                return r;
            }
            if(r.getType().equals("0")) {
                return l;
            }
        }

        return new Sum(l, r);
    }

    @Override
    public double getValue() {
        return 0;
    }
}
