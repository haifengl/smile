package smile.symbolic.internal;

/**
 * @author Ernest DeFoy
 */
public class Tangent extends UnaryNode {

    public Tangent(Expression exp) {

        super(exp, UnaryOperator.TAN);
    }

    @Override
    public Expression derive() {

        if(exp instanceof Constant)
            return new Constant(0);

        // chain rule
        return new Product(
                exp.derive(),
                new Exponent(
                        new Secant(exp),
                        new Constant(2)
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
