

package smile.symbolic.internal;

/**
 * @author Ernest DeFoy
 */
public class Cosine extends UnaryNode {

    public Cosine(Expression exp) {

        super(exp, UnaryOperator.COS);
    }

    @Override
    public Expression derive() {

        if(exp instanceof Constant)
            return new Constant(0);

        // chain rule
        return new Product(
                exp.derive(),
                new Sine(exp)
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
