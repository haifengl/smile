package smile.symbolic.internal;

/**
 * @author Ernest DeFoy
 */
public class Secant extends UnaryNode {

    public Secant(Expression exp) {

        super(exp, UnaryOperator.SEC);
    }

    @Override
    public Expression derive() {

        if(exp instanceof Constant)
            return new Constant(0);

        // chain rule
        return new Product(
                exp.derive(),
                new Product(
                        this,
                        new Tangent(exp)
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
