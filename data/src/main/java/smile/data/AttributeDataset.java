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
 * A dataset of fixed number of attributes. All attribute values are stored as
 * double even if the attribute may be nominal, ordinal, string, or date.
 * The dataset is stored row-wise internally, which is fast for frequently
 * accessing instances of dataset.
 *
 * @author Haifeng Li
 */
public class AttributeDataset extends Dataset<double[]> {

    /**
     * The list of attributes.
     */
    private Attribute[] attributes;

    /**
     * Constructor.
     * @param name the name of dataset.
     * @param attributes the list of attributes in this dataset.
     */
    public AttributeDataset(String name, Attribute[] attributes) {
        super(name);
        this.attributes = attributes;
    }

    /**
     * Constructor.
     * @param name the name of dataset.
     * @param attributes the list of attributes in this dataset.
     * @param response the attribute of response variable.
     */
    public AttributeDataset(String name, Attribute[] attributes, Attribute response) {
        super(name, response);
        this.attributes = attributes;
    }

    /**
     * Returns the list of attributes in this dataset.
     */
    public Attribute[] attributes() {
        return attributes;
    }

    /** Returns the array of data items. */
    public double[][] x() {
        double[][] x = new double[size()][];
        toArray(x);
        return x;
    }

    @Override
    public String toString() {
        int n = 10;
        String s = head(n);
        if (size() <= n) return s;
        else return s + "\n" + (size() - n) + " more rows...";
    }

    /** Shows the first few rows. */
    public String head(int n) {
        return toString(0, n);
    }

    /** Shows the last few rows. */
    public String tail(int n) {
        return toString(size() - n, size());
    }

    /**
     * Stringify dataset.
     * @param from starting row (inclusive)
     * @param to ending row (exclusive)
     */
    public String toString(int from, int to) {
        StringBuilder sb = new StringBuilder();

        sb.append('\t');

        if (response != null) {
            sb.append(response.getName());
        }

        int p = attributes.length;
        for (int j = 0; j < p; j++) {
            sb.append('\t');
            sb.append(attributes[j].getName());
        }

        int end = Math.min(data.size(), to);
        for (int i = from; i < end; i++) {
            sb.append(System.getProperty("line.separator"));

            Datum<double[]> datum = data.get(i);
            if (datum.name != null) {
                sb.append(datum.name);
            } else {
                sb.append('[');
                sb.append(i + 1);
                sb.append(']');
            }
            sb.append('\t');

            if (response != null) {
                double y = data.get(i).y;
                if (response.getType() == Attribute.Type.NUMERIC)
                    sb.append(String.format("%1.4f", y));
                else
                    sb.append(response.toString(y));
            }

            double[] x = datum.x;
            for (int j = 0; j < p; j++) {
                sb.append('\t');
                Attribute attr = attributes[j];
                if (attr.getType() == Attribute.Type.NUMERIC)
                    sb.append(String.format("%1.4f", x[j]));
                else
                    sb.append(attr.toString(x[j]));
            }
        }

        return sb.toString();
    }

    /** Returns a column. */
    public AttributeVector column(int i) {
        if (i < 0 || i >= attributes.length) {
            throw new IllegalArgumentException("Invalid column index: " + i);
        }

        double[] vector = new double[size()];
        for (int j = 0; j < vector.length; j++) {
            vector[j] = data.get(j).x[i];
        }

        return new AttributeVector(attributes[i], vector);
    }

    /** Returns a column. */
    public AttributeVector column(String col) {
        int i = -1;
        for (int j = 0; j < attributes.length; j++) {
            if (attributes[j].getName().equals(col)) {
                i = j;
                break;
            }
        }

        if (i == -1) {
            throw new IllegalArgumentException("Invalid column name: " + col);
        }

        return column(i);
    }

    /** Returns statistic summary. */
    public AttributeDataset summary() {
        Attribute[] attr = {
                new NumericAttribute("min"),
                new NumericAttribute("q1"),
                new NumericAttribute("median"),
                new NumericAttribute("mean"),
                new NumericAttribute("q3"),
                new NumericAttribute("max"),
        };

        AttributeDataset stat = new AttributeDataset(name + " Summary", attr);

        for (int i = 0; i < attributes.length; i++) {
            double[] x = column(i).vector();
            double[] s = new double[attr.length];
            s[0] = Math.min(x);
            s[1] = Math.q1(x);
            s[2] = Math.median(x);
            s[3] = Math.mean(x);
            s[4] = Math.q3(x);
            s[5] = Math.max(x);
            Datum<double[]> datum = new Datum<>(s);
            datum.name = attributes[i].getName();
            datum.description = attributes[i].getDescription();
            stat.add(datum);
        }

        return stat;
    }
}
