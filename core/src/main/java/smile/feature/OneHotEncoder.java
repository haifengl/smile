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

import smile.data.Attribute;
import smile.data.NominalAttribute;
import smile.data.NumericAttribute;

/**
 * Encode categorical integer features using a one-hot aka one-of-K scheme.
 * Although some method such as decision trees can handle nominal variable
 * directly, other methods generally require nominal variables converted to
 * multiple binary dummy variables to indicate the presence or absence of
 * a characteristic.
 * 
 * @author Haifeng Li
 */
public class OneHotEncoder implements FeatureGenerator<double[]> {
    /**
     * The variable attributes.
     */
    private Attribute[] attributes;
    /**
     * The attributes of generated binary dummy variables.
     */
    private Attribute[] features;

    /**
     * Constructor.
     * @param attributes the variable attributes. Of which, nominal variables
     * will be converted to binary dummy variables.
     */
    public OneHotEncoder(Attribute[] attributes) {
        this.attributes = attributes;
        
        int p = 0;
        for (Attribute attribute : attributes) {
            if (attribute instanceof NominalAttribute) {
                NominalAttribute nominal = (NominalAttribute) attribute;
                p += nominal.size();
            } else {
                p++;
            }
        }
        
        features = new Attribute[p];
        for (int i = 0, j = 0; j < attributes.length; j++) {
            Attribute attribute = attributes[j];
            if (attribute instanceof NominalAttribute) {
                NominalAttribute nominal = (NominalAttribute) attribute;
                double weight = nominal.getWeight();
                String name = nominal.getName();
                String description = nominal.getDescription();
                
                for (int k = 0; k < nominal.size(); k++, i++) {
                    features[i] = new NumericAttribute(name + "_" + k, description, weight);
                }
            } else {
                features[i++] = attribute;
            }
        }
    }
    
    @Override
    public Attribute[] attributes() {
        return features;
    }
    
    @Override
    public double[] feature(double[] x) {
        if (x.length != attributes.length) {
            throw new IllegalArgumentException(String.format("Invalid vector size %d, expected %d", x.length, attributes.length));
        }

        double[] y = new double[features.length];
        for (int i = 0, j = 0; j < attributes.length; j++) {
            Attribute attribute = attributes[j];
            if (attribute instanceof NominalAttribute) {
                NominalAttribute nominal = (NominalAttribute) attribute;
                y[i + (int)x[j]] = 1.0;
                i += nominal.size();
            } else {
                y[i++] = x[j];
            }
        }

        return y;
    }    
}
