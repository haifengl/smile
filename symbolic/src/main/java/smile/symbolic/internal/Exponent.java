package smile.symbolic.internal;

/**
 * @author Ernest DeFoy
 */
public class Exponent extends BinaryNode {

    public Exponent(Expression left, Expression right) {

        super(left, right, BinaryOperator.POWER);
    }

    @Override
    public Expression derive() {

        if(left instanceof Constant)
            return new Constant(0);

        // chain rule
        return new Product(
                right,
                new Product(
                        left.derive(),
                        new Exponent(
                                left,
                                new Difference(
                                        right,
                                        new Constant(1)
                                )
                        )
                )
        );
    }

    @Override
    public Expression reduce() {

        Expression l = left.reduce();
        Expression r = right.reduce();

        if(r instanceof Constant) {
            if(r.getValue() == 0)
                return new Constant(1);
            if(r.getValue() == 1)
                return left;
        }

        return new Exponent(l, r);
    }

    @Override
    public double getValue() {
        return 0;
    }
}
