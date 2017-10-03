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

import java.util.Date;
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

    public class Row extends Datum<double[]> {
        /**
         * Constructor.
         * @param x the datum.
         */
        public Row(double[] x) {
            super(x);
        }

        /**
         * Constructor.
         * @param x the datum.
         * @param y the class label or real-valued response.
         */
        public Row(double[] x, double y) {
            super(x, y);
        }

        /**
         * Constructor.
         * @param x the datum.
         * @param y the class label or real-valued response.
         * @param weight the weight of datum. The particular meaning of weight
         * depends on applications and machine learning algorithms. Although there
         * are on explicit requirements on the weights, in general, they should be
         * positive.
         */
        public Row(double[] x, double y, double weight) {
            super(x, y, weight);
        }

        /** Returns the class label in string format. */
        public String label() {
            if (response.getType() != Attribute.Type.NOMINAL) {
                throw new IllegalStateException("The response is not of nominal type");
            }
            return response.toString(y);
        }

        /**
         * Returns an element value in string format.
         * @param i the element index.
         */
        public String string(int i) {
            return attributes[i].toString(x[i]);
        }

        /**
         * Returns a date element.
         * @param i the element index.
         */
        public Date date(int i) {
            if (attributes[i].getType() != Attribute.Type.DATE) {
                throw new IllegalStateException("Attribute is not of date type");
            }
            return ((DateAttribute) attributes[i]).toDate(x[i]);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();

            // Header
            if (name != null) {
                sb.append('\t');
            }

            if (response != null) {
                sb.append(response.getName());
            }

            int p = attributes.length;
            for (int j = 0; j < p; j++) {
                sb.append('\t');
                sb.append(attributes[j].getName());
            }

            sb.append(System.getProperty("line.separator"));

            // Data
            if (name != null) {
                sb.append(name);
                sb.append('\t');
            }

            if (response != null) {
                if (response.getType() == Attribute.Type.NUMERIC)
                    sb.append(String.format("%1.4f", y));
                else
                    sb.append(response.toString(y));
            }

            for (int j = 0; j < p; j++) {
                sb.append('\t');
                Attribute attr = attributes[j];
                if (attr.getType() == Attribute.Type.NUMERIC)
                    sb.append(String.format("%1.4f", x[j]));
                else
                    sb.append(attr.toString(x[j]));
            }

            return sb.toString();
        }
    }

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
    public Datum<double[]> add(Datum<double[]> x) {
        if (!(x instanceof Row)) {
            throw new IllegalArgumentException("The added Datum is not of type AttributeDataset.Row");
        }

        return super.add(x);
    }

    /**
     * Add a datum item into the dataset.
     * @param x a datum item.
     * @return the added datum item.
     */
    public Row add(Row x) {
        data.add(x);
        return x;
    }

    @Override
    public Row add(double[] x) {
        return add(new Row(x));
    }

    @Override
    public Row add(double[] x, int y) {
        if (response == null) {
            throw new IllegalArgumentException(DATASET_HAS_NO_RESPONSE);
        }

        if (response.getType() != Attribute.Type.NOMINAL) {
            throw new IllegalArgumentException(RESPONSE_NOT_NOMINAL);
        }

        return add(new Row(x, y));
    }

    @Override
    public Row add(double[] x, int y, double weight) {
        if (response == null) {
            throw new IllegalArgumentException(DATASET_HAS_NO_RESPONSE);
        }

        if (response.getType() != Attribute.Type.NOMINAL) {
            throw new IllegalArgumentException(RESPONSE_NOT_NOMINAL);
        }

        return add(new Row(x, y, weight));
    }

    @Override
    public Row add(double[] x, double y) {
        if (response == null) {
            throw new IllegalArgumentException(DATASET_HAS_NO_RESPONSE);
        }

        return add(new Row(x, y));
    }

    @Override
    public Row add(double[] x, double y, double weight) {
        if (response == null) {
            throw new IllegalArgumentException(DATASET_HAS_NO_RESPONSE);
        }

        return add(new Row(x, y, weight));
    }

    @Override
    public String toString() {
        int n = 10;
        String s = toString(0, n);
        if (size() <= n) return s;
        else return s + "\n" + (size() - n) + " more rows...";
    }

    /** returns the first few rows. */
    public AttributeDataset head(int n) {
        return range(0, n);
    }

    /** Returns the last few rows. */
    public AttributeDataset tail(int n) {
        return range(size() - n, size());
    }

    /** Returns the rows in the given range [from, to). */
    public AttributeDataset range(int from, int to) {
        AttributeDataset sub = new AttributeDataset(name+'['+from+", "+to+']', attributes, response);
        sub.description = description;

        for (int i = from; i < to; i++) {
            sub.add(get(i));
        }

        return sub;
    }

    /**
     * Stringify dataset.
     * @param from starting row (inclusive)
     * @param to ending row (exclusive)
     */
    public String toString(int from, int to) {
        StringBuilder sb = new StringBuilder();

        if (name != null && !name.isEmpty()) {
            sb.append(name);
            sb.append(System.getProperty("line.separator"));
        }

        if (description != null && !description.isEmpty()) {
            sb.append(description);
            sb.append(System.getProperty("line.separator"));
        }

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

    /** Returns a dataset with selected columns. */
    public AttributeDataset columns(String... cols) {
        Attribute[] attrs = new Attribute[cols.length];
        int[] index = new int[cols.length];
        for (int k = 0; k < cols.length; k++) {
            for (int j = 0; j < attributes.length; j++) {
                if (attributes[j].getName().equals(cols[k])) {
                    index[k] = j;
                    attrs[k] = attributes[j];
                    break;
                }
            }

            if (attrs[k] == null) {
                throw new IllegalArgumentException("Unknown column: " + cols[k]);
            }
        }

        AttributeDataset sub = new AttributeDataset(name, attrs, response);
        for (Datum<double[]> datum : data) {
            double[] x = new double[index.length];
            for (int i = 0; i < x.length; i++) {
                x[i] = datum.x[index[i]];
            }
            Row row = response == null ? sub.add(x) : sub.add(x, datum.y);
            row.name = datum.name;
            row.weight = datum.weight;
            row.description = datum.description;
            row.timestamp = datum.timestamp;
        }

        return sub;
    }

    /** Returns a new dataset without given columns. */
    public AttributeDataset remove(String... cols) {
        Attribute[] attrs = new Attribute[cols.length];
        int[] index = new int[cols.length];
        for (int j = 0, i = 0; j < attributes.length; j++) {
            boolean hit = false;
            for (int k = 0; k < cols.length; k++) {
                if (attributes[j].getName().equals(cols[k])) {
                    hit = true;
                    break;
                }
            }

            if (!hit) {
                index[i] = j;
                attrs[i] = attributes[j];
                i++;
            }
        }

        AttributeDataset sub = new AttributeDataset(name, attrs, response);
        for (Datum<double[]> datum : data) {
            double[] x = new double[index.length];
            for (int i = 0; i < x.length; i++) {
                x[i] = datum.x[index[i]];
            }
            Row row = response == null ? sub.add(x) : sub.add(x, datum.y);
            row.name = datum.name;
            row.weight = datum.weight;
            row.description = datum.description;
            row.timestamp = datum.timestamp;
        }

        return sub;
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
            Row datum = new Row(s);
            datum.name = attributes[i].getName();
            datum.description = attributes[i].getDescription();
            stat.add(datum);
        }

        return stat;
    }
}
