package smile.symbolic.internal;

/**
 * @author Ernest DeFoy
 */
public class Sine extends UnaryNode {

    public Sine(Expression exp) {

        super(exp, UnaryOperator.SIN);
    }

    @Override
    public Expression derive() {

        if(exp instanceof Constant)
            return new Constant(0);

        // chain rule
        return new Product(
                exp.derive(),
                new Cosine(exp)
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
