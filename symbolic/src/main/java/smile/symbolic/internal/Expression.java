package smile.symbolic.internal;

/**
 * An expression is
 *
 * @author Ernest DeFoy
 */
public abstract class Expression {
    public abstract Expression derive();
    public abstract Expression reduce();
    public abstract String getType();
    public abstract double getValue();
    public abstract Expression getLeftChild();
    public abstract Expression getRightChild();

    enum BinaryOperator {

        PLUS("+"),
                MINUS("-"),
                MULTIPLY("*"),
                DIVIDE("/"),
                POWER("^");

        final private String display;

        BinaryOperator(String display) {
            this.display = display;
        }

        public String toString() {
            return display;
        }

    }

    enum UnaryOperator {

        NEGATE("$"),
        NATURALLOG("ln"),
        LOG("log"),
        SIN("sin"),
        COS("cos"),
        TAN("tan"),
        CSC("csc"),
        SEC("sec"),
        COT("cot");

        final private String display;

        UnaryOperator(String display) {
            this.display = display;
        }

        public String toString() {
            return display;
        }

    }
}



