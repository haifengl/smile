/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/
package smile.data;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * String attribute. String attributes may contain arbitrary textual values.
 * Value 0 is reserved for unknown word.
 *
 * @author Haifeng Li
 */
public class StringAttribute extends Attribute {

    /**
     * True if the string values of the nominal attribute is a open set.
     * The new string values will be added into the attribute when calling
     * the method valueOf(String).
     */
    private boolean open = true;
    /**
     * The list of unique string values of this attribute.
     */
    private List<String> values = new ArrayList<String>();
    /**
     * Map a string to an integer level.
     */
    private Map<String, Integer> map = new HashMap<String, Integer>();

    /**
     * Constructor.
     * @param name the name of attribute.
     */
    public StringAttribute(String name) {
        super(Type.STRING, name);
        values.add(null);
    }

    /**
     * Constructor.
     * @param name the name of attribute.
     */
    public StringAttribute(String name, double weight) {
        super(Type.STRING, name, weight);
        values.add(null);
    }

    /**
     * Constructor.
     * @param name the name of attribute.
     * @param description the detailed description of attribute.
     */
    public StringAttribute(String name, String description, double weight) {
        super(Type.STRING, name, description, weight);
        values.add(null);
    }

    /**
     * Returns the list of string values of this attribute.
     * @return the list of string values of this attribute
     */
    public List<String> values() {
        return values;
    }
    
    /**
     * Returns true if the string values of the nominal attribute is a open set.
     * The new string values will be added into the attribute when calling
     * the method valueOf(String).
     * @return true if the string values of the nominal attribute is a open set.
     */
    public boolean isOpen() {
        return open;
    }
    
    /**
     * Sets if the string values of the nominal attribute is a open set.
     * @param open true if the string values of the nominal attribute is a open set.
     */
    public void setOpen(boolean open) {
        this.open = open;
    }
    
    @Override
    public double valueOf(String s) throws ParseException {
        Integer i = map.get(s);
        if (i == null) {
            if (open) {
                i = values.size();
                map.put(s, i);
                values.add(s);
            } else {
                return 0;
            }
        }
        
        return i;
    }

    @Override
    public String toString(double x) {
        if (Double.isNaN(x)) {
            return null;
        }
        
        if (Math.floor(x) != x) {
            throw new IllegalArgumentException("The input string index is not an integer: " + x);
        }

        if (x < 0 || x >= values.size()) {
            throw new IllegalArgumentException("Invalid string index: " + x);
        }
        
        return values.get((int)x);
    }
}
