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

package smile.feature;

import smile.math.Math;
import smile.data.Attribute;
import smile.data.NumericAttribute;
import smile.sort.QuickSelect;

/**
 * Numeric attribute normalization/standardization feature generator.
 * Many machine learning methods such as Neural Networks and SVM with Gaussian
 * kernel also require the features properly scaled/standardized. For example,
 * each variable is scaled into interval [0, 1] or to have mean 0 and standard
 * deviation 1. 
 * 
 * @author Haifeng Li
 */
public class NumericAttributeFeature implements Feature<double[]> {
    /**
     * The types of data scaling.
     */
    public static enum Scaling {
        /**
         * No scaling at all.
         */
        NONE,
        /**
         * Takes logarithms of input data when they contain order-of-magnitude
         * larger and smaller values. Note logarithms are defined only for
         * positive values.
         */
        LOGARITHM,
        /**
         * Normalization scales all numeric variables in the range [0, 1].
         * If the dataset has outliers, normalization will certainly scale
         * the "normal" data to a very small interval. In this case, the
         * Winsorization procedure should be applied: values greater than the
         * specified upper limit are replaced with the upper limit, and those
         * below the lower limit are replace with the lower limit. Often, the
         * specified range is indicate in terms of percentiles of the original
         * distribution (like the 5th and 95th percentile).
         */
        NORMALIZATION,
        /**
         * Standardization transforms a variable to have zero mean and unit
         * variance. Standardization makes an assumption that the data follows
         * a Gaussian distribution and are also not robust when outliers present.
         * A robust alternative is to subtract the median and divide by the IQR.
         */
        STANDARDIZATION
    }
    
    /**
     * The variable attributes.
     */
    private Attribute[] attributes;
    /**
     * The attributes of generated binary dummy variables.
     */
    private Attribute[] features;
    /**
     * A map from feature id to original attribute index.
     */
    private int[] map;
    /**
     * The types of scaling.
     */
    private Scaling scaling;
    /**
     * For normalization, this is min or lower limit.
     * For standardization, this is mean or median.
     */
    private double[] a;
    /**
     * For normalization, this is max - min or upper limit - lower limit.
     * For standardization, this is standard deviation or IQR.
     */
    private double[] b;

    /**
     * Constructor. Scales numeric attributes into proper range. For logarithm
     * scaling, the attributes must have positive values.
     * @param attributes the variable attributes. Of which, numeric variables
     * will be scaled.
     * @param scaling the way of scaling. The scaling type must be NONE or
     * LOGARITHM because they do not need training data.
     */
    public NumericAttributeFeature(Attribute[] attributes, Scaling scaling) {
        if (scaling != Scaling.NONE && scaling != Scaling.LOGARITHM) {
            throw new IllegalArgumentException("Invalid scaling operation without training data: " + scaling);
        }
        
        this.attributes = attributes;
        this.scaling = scaling;
        
        int p = 0;
        for (Attribute attribute : attributes) {
            if (attribute instanceof NumericAttribute) {
                p++;
            }
        }
        
        features = new Attribute[p];
        map = new int[p];
        for (int i = 0, j = 0; j < attributes.length; j++) {
            Attribute attribute = attributes[j];
            if (attribute instanceof NumericAttribute) {
                if (scaling == Scaling.NONE) {
                    features[i] = attribute;
                } else {
                    features[i] = new NumericAttribute(attribute.getName() + "_" + scaling, attribute.getDescription(), attribute.getWeight());
                }
                
                map[i++] = j;
            }            
        }
    }
    
    /**
     * Constructor. Scales numeric attributes into proper range. For logarithm
     * scaling, the attributes must have positive values. In case of
     * normalization, the min and max values of attributes are used as lower
     * and upper limits. For standardization, variables are scaled to have zero
     * mean and unit variance.
     * @param attributes the variable attributes. Of which, numeric variables
     * will be scaled.
     * @param scaling the way of scaling.
     * @param data the training data to learn scaling parameters.
     */
    public NumericAttributeFeature(Attribute[] attributes, Scaling scaling, double[][] data) {
        this.attributes = attributes;
        this.scaling = scaling;
        
        int n = data.length;
        int p = 0;
        for (Attribute attribute : attributes) {
            if (attribute instanceof NumericAttribute) {
                p++;
            }
        }
        
        features = new Attribute[p];
        map = new int[p];
        a = new double[p];
        b = new double[p];
        double[] x = new double[n];
                
        for (int i = 0, j = 0; j < attributes.length; j++) {
            Attribute attribute = attributes[j];
            if (attribute instanceof NumericAttribute) {
                if (scaling == Scaling.NONE) {
                    features[i] = attribute;
                } else {
                    features[i] = new NumericAttribute(attribute.getName() + "_" + scaling, attribute.getDescription(), attribute.getWeight());
                    if (scaling == Scaling.NORMALIZATION || scaling == Scaling.STANDARDIZATION) {
                        for (int k = 0; k < n; k++) {
                            x[k] = data[k][j];
                        }
                        
                        if (scaling == Scaling.NORMALIZATION) {
                            a[i] = Math.min(x);
                            b[i] = Math.max(x) - a[i];
                            if (b[i] == 0.0) {
                                throw new IllegalArgumentException("Attribute " + attribute + " has constant values.");
                            }
                        }

                        if (scaling == Scaling.STANDARDIZATION) {
                            a[i] = Math.mean(x);
                            b[i] = Math.sd(x);
                            if (b[i] == 0.0) {
                                throw new IllegalArgumentException("Attribute " + attribute + " has constant values.");
                            }
                        }
                    }

                }
                
                map[i++] = j;
            }            
        }
    }
    
    /**
     * Constructor. Normalizes numeric attributes with Winsorization: values
     * greater than the specified upper limit are replaced with the upper
     * limit, and those below the lower limit are replace with the lower limit.
     * The specified lower/upper limits are indicate in terms of percentiles of
     * the original distribution.
     * @param attributes the variable attributes. Of which, numeric variables
     * will be normalized.
     * @param lower the lower limit in terms of percentiles of the original
     * distribution (say 5th percentile).
     * @param upper the upper limit in terms of percentiles of the original
     * distribution (say 95th percentile).
     * @param data the training data to learn scaling parameters.
     */
    public NumericAttributeFeature(Attribute[] attributes, double lower, double upper, double[][] data) {
        if (lower < 0.0 || lower > 0.5) {
            throw new IllegalArgumentException("Invalid lower limit: " + lower);
        }
        
        if (upper < 0.5 || lower > 1.0) {
            throw new IllegalArgumentException("Invalid upper limit: " + upper);
        }
        
        if (upper <= lower) {
            throw new IllegalArgumentException("Invalid lower and upper limit pair: " + lower + " >= " + upper);
        }
        
        this.attributes = attributes;
        this.scaling = Scaling.NORMALIZATION;
        
        int n = data.length;
        int p = 0;
        for (Attribute attribute : attributes) {
            if (attribute instanceof NumericAttribute) {
                p++;
            }
        }
        
        int i1 = (int) Math.round(lower * n);
        int i2 = (int) Math.round(upper * n);
        if (i2 == n) {
            i2 = n - 1;
        }
        
        features = new Attribute[p];
        map = new int[p];
        a = new double[p];
        b = new double[p];
        double[] x = new double[n];
        
        for (int i = 0, j = 0; j < attributes.length; j++) {
            Attribute attribute = attributes[j];
            if (attribute instanceof NumericAttribute) {
                features[i] = new NumericAttribute(attribute.getName() + "_" + scaling, attribute.getDescription(), attribute.getWeight());
                for (int k = 0; k < n; k++) {
                    x[k] = data[k][j];
                }

                a[i] = QuickSelect.select(x, i1);
                b[i] = QuickSelect.select(x, i2) - a[i];
                if (b[i] == 0.0) {
                    throw new IllegalArgumentException("Attribute " + attribute + " has constant values in the given range.");
                }

                map[i++] = j;
            }            
        }
    }
    
    /**
     * Constructor. Robustly standardizes numeric attributes by subtracting
     * the median and dividing by the IQR.
     * @param attributes the variable attributes. Of which, numeric variables
     * will be standardized.
     * @param data the training data to learn scaling parameters.
     */
    public NumericAttributeFeature(Attribute[] attributes, double[][] data) {
        this.attributes = attributes;
        this.scaling = Scaling.STANDARDIZATION;
        
        int n = data.length;
        int p = 0;
        for (Attribute attribute : attributes) {
            if (attribute instanceof NumericAttribute) {
                p++;
            }
        }
        
        features = new Attribute[p];
        map = new int[p];
        a = new double[p];
        b = new double[p];
        double[] x = new double[n];
                
        for (int i = 0, j = 0; j < attributes.length; j++) {
            Attribute attribute = attributes[j];
            if (attribute instanceof NumericAttribute) {
                features[i] = new NumericAttribute(attribute.getName() + "_" + scaling, attribute.getDescription(), attribute.getWeight());
                for (int k = 0; k < n; k++) {
                    x[k] = data[k][j];
                }

                a[i] = QuickSelect.median(x);
                b[i] = QuickSelect.q3(x) - QuickSelect.q1(x);
                if (b[i] == 0.0) {
                    throw new IllegalArgumentException("Attribute " + attribute + " has constant values between Q1 and Q3.");
                }

                map[i++] = j;
            }
        }
    }
    
    @Override
    public Attribute[] attributes() {
        return features;
    }
    
    @Override
    public double f(double[] object, int id) {
        if (object.length != attributes.length) {
            throw new IllegalArgumentException(String.format("Invalide object size %d, expected %d", object.length, attributes.length));            
        }
        
        if (id < 0 || id >= features.length) {
            throw new IllegalArgumentException("Invalide feature id: " + id);
        }
        
        double x = object[map[id]];
        switch (scaling) {
            case NONE:
                return x;
            case LOGARITHM:
                if (x <= 0.0) {
                    throw new IllegalArgumentException("Invalid value for logarithm: " + x);
                }
                return Math.log(x);
            case NORMALIZATION:
                double y = (x - a[id]) / b[id];
                if (y < 0.0) y = 0.0;
                if (y > 1.0) y = 1.0;
                return y;
            case STANDARDIZATION:
                return (x - a[id]) / b[id];
        }
        
        throw new IllegalStateException("Impossible to reach here.");
    }    
}
