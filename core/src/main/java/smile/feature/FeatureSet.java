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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import smile.data.Attribute;
import smile.data.AttributeDataset;
import smile.data.Dataset;
import smile.data.Datum;

/**
 * A set of feature generators.
 * 
 * @param <T> the type of input object.
 * 
 * @author Haifeng Li
 */
public class FeatureSet <T> {
    /**
     * Feature generators.
     */
    List<Feature<T>> features = new ArrayList<>();
    /**
     * The variable attributes of generated features.
     */
    List<Attribute> attributes = new ArrayList<>();
    
    /**
     * Constructor.
     */
    public FeatureSet() {
        
    }
    
    /**
     * Adds a feature generator.
     * @param feature feature generator.
     */
    public void add(Feature<T> feature) {
        features.add(feature);
        attributes.addAll(Arrays.asList(feature.attributes()));
    }
    
    /**
     * Removes a feature generator.
     * @param feature feature generator.
     */
    public void remove(Feature<T> feature) {
        if (features.remove(feature)) {
            attributes.clear();
            for (Feature<T> f : features) {
                attributes.addAll(Arrays.asList(f.attributes()));
            }
        }
    }
    
    /**
     * Returns the variable attributes of generated features.
     * @return the variable attributes of generated features
     */
    public Attribute[] attributes() {
        return attributes.toArray(new Attribute[attributes.size()]);
    }
    
    /**
     * Returns generated feature values.
     * @param datum input data object.
     * @return generated feature values.
     */
    public double[] f(T datum) {
        int i = 0;
        double[] x = new double[attributes.size()];
        for (Feature<T> feature : features) {
            int k = feature.attributes().length;
            for (int j = 0; j < k; i++, j++) {
                x[i] = feature.f(datum, j);
            }
        }
        
        return x;
    }
    
    /**
     * Returns a dataset with generated features.
     * @param data input dataset.
     * @return a dataset with generated features 
     */
    public double[][] f(T[] data) {
        int n = data.length;
        
        double[][] x = new double[n][];
        for (int i = 0; i < n; i++) {
            x[i] = f(data[i]);
        }
        
        return x;
    }
    
    /**
     * Returns an attribute dataset with generated features.
     * @param data input dataset.
     * @return an attribute dataset with generated features 
     */
    public AttributeDataset f(Dataset<T> data) {
        AttributeDataset dataset = new AttributeDataset(data.getName(), attributes(), data.response()); 
        dataset.setDescription(data.getDescription());

        for (int i = 0; i < data.size(); i++) {
            Datum<T> datum = data.get(i);
            Datum<double[]> x = new Datum<>(f(datum.x), datum.y, datum.weight);
            x.name = datum.name;
            x.description = datum.description;
            x.timestamp = datum.timestamp;
            dataset.add(x);
        }

        return dataset;
    }
}
