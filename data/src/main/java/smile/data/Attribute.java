/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package smile.data;

import java.io.Serializable;
import java.text.ParseException;

/**
 * Generic class to represent a named attribute/variable.
 *
 * @author Haifeng Li
 */
public abstract class Attribute implements Serializable {
    /**
     * The type of attributes.
     */
    public enum Type {
        /**
         * Numeric attribute.
         */
        NUMERIC,
        /**
         * Nominal attribute. Variables assessed on a nominal scale are called
         * categorical variables.
         */
        NOMINAL,
        /**
         * String attribute. Note that strings should not be treated as nominal
         * attribute because one may use some part of strings (e.g. suffix or
         * prefix) instead of the original string as features.
         */
        STRING,
        /**
         * Date attribute. A date type also optionally specifies the format
         * in which the date is presented, with the default being in ISO-8601 format.
         */
        DATE
    }

    /**
     * The type of attribute.
     */
    private Type type;
    
    /**
     * Optional weight of this attribute. By default, it is 1.0. The particular
     * meaning of weight depends on applications and machine learning algorithms.
     * Although there are on explicit requirements on the weights, in general,
     * they should be positive.
     */
    private double weight;

    /**
     * The name of attribute.
     */
    private String name;

    /**
     * The detailed description of the attribute.
     */
    private String description;

    /**
     * Constructor.
     */
    public Attribute(Type type, String name) {
        this(type, name, 1.0);
    }

    /**
     * Constructor.
     */
    public Attribute(Type type, String name, double weight) {
        this(type, name, null, weight);
    }

    /**
     * Constructor.
     */
    public Attribute(Type type, String name, String description) {
        this(type, name, description, 1.0);
    }

    /**
     * Constructor.
     */
    public Attribute(Type type, String name, String description, double weight) {
        this.type = type;
        this.name = name;
        this.description = description;
        this.weight = weight;
    }

    public Type getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public Attribute setName(String name) {
        this.name = name;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public Attribute setDescription(String description) {
        this.description = description;
        return this;
    }

    public double getWeight() {
        return weight;
    }

    public Attribute setWeight(double weight) {
        this.weight = weight;
        return this;
    }

    /**
     * Returns the string representation of a double value of this attribute.
     * @param x a double value of this attribute. NaN means missing value.
     * @return the string representation of x. For nominal, date and string
     * attributes, null will be returned for missing values. For numeric
     * attributes, "NaN" will be returned for missing values.
     */
    public abstract String toString(double x);

    /**
     * Returns the double value of a string of this attribute.
     * @param s a string value of this attribute.
     */
    public abstract double valueOf(String s) throws ParseException;

    @Override
    public boolean equals(Object o) {
        if (o instanceof Attribute) {
            Attribute a = (Attribute) o;

            if (name.equals(a.name) && type == a.type) {
                if (description != null && a.description != null) {
                    return description.equals(a.description);
                } else {
                    return description == a.description;
                }
            } else {
                return false;
            }
        }

        return false;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 37 * hash + type.hashCode();
        hash = 37 * hash + (name != null ? name.hashCode() : 0);
        hash = 37 * hash + (description != null ? description.hashCode() : 0);
        return hash;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(type);
        sb.append('[');
        sb.append(name);
        sb.append(']');

        return sb.toString();
    }
}
