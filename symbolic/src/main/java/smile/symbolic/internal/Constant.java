

package smile.symbolic.internal;

/**
 *
 *
 * @author Ernest DeFoy
 */
public class Constant extends Expression {

    double value;

    public Constant(double value) {

        this.value = value;
    }

    @Override
    public Expression derive() {

        return new Constant(0);
    }

    @Override
    public Constant reduce() {

        return this;
    }

    @Override
    public String getType() {

        // int type
        if ((value == Math.floor(value)) && !Double.isInfinite(value)) {
            return String.valueOf((int)value);
        }

        return String.valueOf(value);
    }

    public double getValue() {
        return value;
    }

    @Override
    public Expression getLeftChild() {
        return null;
    }

    @Override
    public Expression getRightChild() {
        return null;
    }
}
