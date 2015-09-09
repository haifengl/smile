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

import java.text.ParseException;

/**
 * Numeric attribute. Numeric attributes can be real or integer numbers.
 *
 * @author Haifeng Li
 */
public class NumericAttribute extends Attribute {

    /**
     * Constructor.
     */
    public NumericAttribute(String name) {
        super(Type.NUMERIC, name);
    }

    /**
     * Constructor.
     */
    public NumericAttribute(String name, double weight) {
        super(Type.NUMERIC, name, weight);
    }

    /**
     * Constructor.
     */
    public NumericAttribute(String name, String description) {
        super(Type.NUMERIC, name, description);
    }

    /**
     * Constructor.
     */
    public NumericAttribute(String name, String description, double weight) {
        super(Type.NUMERIC, name, description, weight);
    }

    @Override
    public String toString(double x) {
        return Double.toString(x);
    }

    @Override
    public double valueOf(String s) throws ParseException {
        return Double.valueOf(s);
    }
}
