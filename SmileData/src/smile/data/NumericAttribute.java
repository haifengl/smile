/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/
package smile.data;

import java.text.ParseException;

/**
 * Numeric attribute. Numeric attributes can be real or integer numbers.
 *
 * @author Haifeng Li
 */
public class NumericAttribute extends Attribute {

    /**
     * Constructor.
     */
    public NumericAttribute(String name) {
        super(Type.NUMERIC, name);
    }

    /**
     * Constructor.
     */
    public NumericAttribute(String name, double weight) {
        super(Type.NUMERIC, name, weight);
    }

    /**
     * Constructor.
     */
    public NumericAttribute(String name, String description) {
        super(Type.NUMERIC, name, description);
    }

    /**
     * Constructor.
     */
    public NumericAttribute(String name, String description, double weight) {
        super(Type.NUMERIC, name, description, weight);
    }

    @Override
    public String toString(double x) {
        return Double.toString(x);
    }

    @Override
    public double valueOf(String s) throws ParseException {
        return Double.valueOf(s);
    }
}
