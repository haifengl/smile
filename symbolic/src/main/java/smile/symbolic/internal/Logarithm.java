package smile.symbolic.internal;

/**
 * @author Ernest DeFoy
 */
public class Logarithm extends UnaryNode {

    public Logarithm(Expression exp) {

        super(exp, UnaryOperator.LOG);
    }

    @Override
    public Expression derive() {

        if(exp instanceof Constant)
            return new Constant(0);

        // chain rule
        // currently parser does not accept base != 10
        return new Quotient(
                exp.derive(),
                new Product(
                        exp,
                        new NaturalLogarithm(new Constant(10))
                )
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
