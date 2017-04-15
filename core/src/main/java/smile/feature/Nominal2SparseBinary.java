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

/**
 * Nominal variables to sparse binary representation convertor. Here we assume
 * that all variables are nominal attributes and will be converted to binary
 * dummy variables in a compact representation in which only indices of nonzero
 * elements are stored in an integer array. In Maximum Entropy Classifier, 
 * the data are expected to store in this format.
 * 
 * @author Haifeng Li
 */
public class Nominal2SparseBinary {
    /**
     * The variable attributes.
     */
    private NominalAttribute[] attributes;
    /**
     * Starting index for each nominal attribute.
     */
    private int[] base;

    /**
     * Constructor.
     * @param attributes the variable attributes. All of them have to be
     * nominal attributes.
     */
    public Nominal2SparseBinary(Attribute[] attributes) {        
        int p = attributes.length;
        this.attributes = new NominalAttribute[p];
        base = new int[p];
        for (int i = 0; i < p; i++) {
            Attribute attribute = attributes[i];
            if (attribute instanceof NominalAttribute) {
                NominalAttribute nominal = (NominalAttribute) attribute;
                this.attributes[i] = nominal;
                if (i < p-1) {
                    base[i+1] = base[i] + nominal.size();
                }
            } else {
                throw new IllegalArgumentException("Non-nominal attribute: " + attribute);
            }
        }
    }
    
    /**
     * Generates the compact representation of sparse binary features for given object.
     * @param object an object of interest.
     * @return an integer array of nonzero binary features.
     */
    public int[] f(double[] object) {
        if (object.length != attributes.length) {
            throw new IllegalArgumentException(String.format("Invalide object size %d, expected %d", object.length, attributes.length));            
        }
        
        int[] features = new int[attributes.length];
        for (int i = 0; i < features.length; i++) {
            int f = (int) object[i];
            if (Math.floor(object[i]) != object[i] || f < 0 || f >= attributes[i].size()) {
                throw new IllegalArgumentException(String.format("Invalid value of attribute %s: %d", attributes[i].toString(), f));
            }
            
            features[i] = f + base[i];
        }
        
        return features;
    }    
}
