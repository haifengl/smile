

package smile.symbolic.internal;

/**
 * Represents the trigonometric function Cosecant.
 *
 * @author Ernest DeFoy
 */
public class Cosecant extends UnaryNode {

    public Cosecant(Expression exp) {

        super(exp, UnaryOperator.CSC);
    }

    @Override
    public Expression derive() {

        if(exp instanceof Constant)
            return new Constant(0);

        // chain rule
        return new Negation(new Product(
                exp.derive(),
                new Product(
                        new Cotangent(exp),
                        this)
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
