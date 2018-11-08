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

import smile.data.type.StructType;

/**
 * A dataset of fixed number of attributes. All attribute values are stored as
 * double even if the attribute may be nominal, ordinal, string, or date.
 * The dataset is stored row-wise internally, which is fast for frequently
 * accessing instances of dataset.
 *
 * @author Haifeng Li
 */
public class AttributeDataset /*extends Dataset<double[]>*/ {

    /**
     * The schema of data.
     */
    private StructType schema;

    /**
     * Constructor.
     * @param name the name of dataset.
     * @param x the data in this dataset.
     * @param y the response data.
     */
    /*
    public AttributeDataset(String name, double[][] x, double[] y) {
        this(name, IntStream.range(0, x[0].length).mapToObj(i -> new NumericAttribute("Var " + (i + 1))).toArray(NumericAttribute[]::new),
                x, new NumericAttribute("response"), y);
    }
       */

    /**
     * Constructor.
     * @param name the name of dataset.
     * @param attributes the list of attributes in this dataset.
     * @param x the data in this dataset.
     * @param response the attribute of response variable.
     * @param y the response data.
     */
    /*
    public AttributeDataset(String name, Attribute[] attributes, double[][] x, Attribute response, double[] y) {
        this(name, attributes, response);
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }
        for (int i = 0; i < x.length; i++) {
            add(x[i], y[i]);
        }
    }
    */

    /**
     * Returns the list of attributes in this dataset.
     */
    public StructType schema() {
        return schema;
    }

    /** Returns statistic summary. */
    /*
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
    */
}
