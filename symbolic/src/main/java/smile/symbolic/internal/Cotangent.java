

package smile.symbolic.internal;

/**
 * @author Ernest DeFoy
 */
public class Cotangent extends UnaryNode {

    public Cotangent(Expression exp) {

        super(exp, UnaryOperator.COT);
    }

    @Override
    public Expression derive() {

        if(exp instanceof Constant)
            return new Constant(0);

        // chain rule
        return new Product(
                exp.derive(),
                new Negation(
                        new Exponent(
                                new Cosecant(exp),
                                new Constant(2)
                        )
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
