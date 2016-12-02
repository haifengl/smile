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
 * Nominal variable to binary dummy variables feature generator.
 * This is also called one-hot encoding. Although some
 * method such as decision trees can handle nominal variable directly, other
 * methods generally require nominal variables converted to multiple binary
 * dummy variables to indicate the presence or absence of a characteristic.
 * 
 * @author Haifeng Li
 */
public class Nominal2Binary implements Feature<double[]> {
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
     * A map from feature id to nominal attribute value.
     */
    private int[] value;

    /**
     * Constructor.
     * @param attributes the variable attributes. Of which, nominal variables
     * will be converted to binary dummy variables.
     */
    public Nominal2Binary(Attribute[] attributes) {
        this.attributes = attributes;
        
        int p = 0;
        for (Attribute attribute : attributes) {
            if (attribute instanceof NominalAttribute) {
                NominalAttribute nominal = (NominalAttribute) attribute;
                p += nominal.size();
            }
        }
        
        features = new Attribute[p];
        map = new int[p];
        value = new int[p];
        for (int i = 0, j = 0; j < attributes.length; j++) {
            Attribute attribute = attributes[j];
            if (attribute instanceof NominalAttribute) {
                NominalAttribute nominal = (NominalAttribute) attribute;
                double weight = nominal.getWeight();
                String name = nominal.getName();
                String description = nominal.getDescription();
                
                for (int k = 0; k < nominal.size(); k++, i++) {
                    features[i] = new NumericAttribute(name + "_" + k, description, weight);
                    map[i] = j;
                    value[i] = k;
                }
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
            throw new IllegalArgumentException(String.format("Invalid object size %d, expected %d", object.length, attributes.length));
        }
        
        if (id < 0 || id >= features.length) {
            throw new IllegalArgumentException("Invalid feature id: " + id);
        }
        
        if (object[map[id]] == value[id]) {
            return 1;
        } else {
            return 0;
        }
    }    
}
