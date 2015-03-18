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
}
