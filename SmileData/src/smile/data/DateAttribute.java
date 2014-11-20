/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/
package smile.data;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Data attribute. The default format string accepts the ISO-8601 combined date
 * and time format: "yyyy-MM-dd'T'HH:mm:ss".
 *
 * @author Haifeng Li
 */
public class DateAttribute extends Attribute {

    /**
     * Date string format.
     */
    private DateFormat format;

    /**
     * Constructor.
     */
    public DateAttribute(String name) {
        this(name, 1.0);
    }

    /**
     * Constructor.
     */
    public DateAttribute(String name, double weight) {
        this(name, null, weight);
    }

    /**
     * Constructor.
     */
    public DateAttribute(String name, String description, double weight) {
        super(Type.DATE, name, description, weight);
        format = new SimpleDateFormat();
    }

    /**
     * Constructor.
     * @param name the name of attribute.
     * @param format the date format.
     */
    public DateAttribute(String name, String description, String format) {
        super(Type.DATE, name, description);
        this.format = new SimpleDateFormat(format);
    }

    /**
     * Constructor.
     * @param name the name of attribute.
     * @param format the date format.
     */
    public DateAttribute(String name, String description, double weight, String format) {
        super(Type.DATE, name, description, weight);
        this.format = new SimpleDateFormat(format);
    }

    /**
     * Returns the date string formatter.
     * @return the date string formatter.
     */
    public DateFormat getFormat() {
        return format;
    }
    
    /**
     * Sets the date format.
     * @param format the date format string.
     */
    public void setFormat(String format) {
        this.format = new SimpleDateFormat(format);
    }
    
    /**
     * Sets the date string formatter.
     * @param format the date string formatter.
     */
    public void setFormat(DateFormat format) {
        this.format = format;
    }
    
    /**
     * Generate the date string.
     */
    public String toString(Date date) {
        return format.format(date);
    }

    /**
     * Returns the double value representation of a data object.
     */
    public double valueOf(Date date) {
        return Double.longBitsToDouble(date.getTime());
    }
    
    @Override
    public String toString(double x) {
        if (Double.isNaN(x)) {
            return null;
        }
        
        return format.format(Double.doubleToRawLongBits(x));
    }

    @Override
    public double valueOf(String s) throws ParseException {
        Date d = format.parse(s);
        return Double.longBitsToDouble(d.getTime());
    }
}
