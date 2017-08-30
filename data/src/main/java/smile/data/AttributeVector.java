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

import smile.math.Math;

/**
 * A vector with attribute information.
 *
 * @author Haifeng Li
 */
public class AttributeVector {

    /**
     * The attribute.
     */
    private Attribute attribute;

    /**
     * The data vector.
     */
    private double[] vector;

    /**
     * The optional names.
     */
    private String[] names;

    /**
     * Constructor.
     * @param attribute the attribute information.
     * @param vector the data vector.
     */
    public AttributeVector(Attribute attribute, double[] vector) {
        this.attribute = attribute;
        this.vector = vector;
    }

    /**
     * Constructor.
     * @param attribute the attribute information.
     * @param vector the data vector.
     * @param names optional names for each element.
     */
    public AttributeVector(Attribute attribute, double[] vector, String[] names) {
        this.attribute = attribute;
        this.vector = vector;
        this.names = names;
    }

    /**
     * Returns the attribute.
     */
    public Attribute attribute() {
        return attribute;
    }

    /**
     * Returns the data vector.
     */
    public double[] vector() {
        return vector;
    }

    /**
     * Returns the name vector.
     */
    public String[] names() {
        return names;
    }

    /**
     * Returns the vector size.
     * @return
     */
    public int size() {
        return vector.length;
    }
    @Override
    public String toString() {
        int n = 10;
        String s = head(n);
        if (vector.length <= n) return s;
        else return s + "\n" + (vector.length - n) + " more values...";
    }

    /** Shows the first few rows. */
    public String head(int n) {
        return toString(0, n);
    }

    /** Shows the last few rows. */
    public String tail(int n) {
        return toString(vector.length - n, vector.length);
    }

    /**
     * Stringify the vector.
     * @param from starting row (inclusive)
     * @param to ending row (exclusive)
     */
    public String toString(int from, int to) {
        StringBuilder sb = new StringBuilder();

        sb.append('\t');

        sb.append(attribute.getName());

        int end = Math.min(vector.length, to);
        for (int i = from; i < end; i++) {
            sb.append(System.getProperty("line.separator"));

            if (names != null) {
                sb.append(names[i]);
            } else {
                sb.append('[');
                sb.append(i + 1);
                sb.append(']');
            }
            sb.append('\t');

            if (attribute.getType() == Attribute.Type.NUMERIC)
                sb.append(String.format("%1.4f", vector[i]));
            else
                sb.append(attribute.toString(vector[i]));
        }

        return sb.toString();
    }

    /** Returns statistic summary. */
    public AttributeVector summary() {
        Attribute attr = new NumericAttribute(attribute.getName() + " Summary");
        String[] names = {"min", "q1", "median", "mean", "q3", "max"};

        double[] stat = new double[6];
        stat[0] = Math.min(vector);
        stat[1] = Math.q1(vector);
        stat[2] = Math.median(vector);
        stat[3] = Math.mean(vector);
        stat[4] = Math.q3(vector);
        stat[5] = Math.max(vector);

        return new AttributeVector(attr, stat, names);
    }
}
