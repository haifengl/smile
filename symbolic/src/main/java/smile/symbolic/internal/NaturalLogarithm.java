package smile.symbolic.internal;

/**
 * @author Ernest DeFoy
 */
public class NaturalLogarithm extends UnaryNode {

    public NaturalLogarithm(Expression exp) {

        super(exp, UnaryOperator.NATURALLOG);
    }

    @Override
    public Expression derive() {

        if(exp instanceof Constant)
            return new Constant(0);

        return new Quotient(
                exp.derive(),
                this
        );
    }

    @Override
    public Expression reduce() {
        return this;
    }

    @Override
    public double getValue() {
        return 0;
    }
}
