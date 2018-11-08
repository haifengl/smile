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
package smile.data.type;

import java.text.ParseException;

/**
 * Double data type.
 *
 * @author Haifeng Li
 */
public class DoubleType implements DataType {

    /** Singleton instance. */
    static DoubleType instance = new DoubleType();

    /**
     * Private constructor for singleton design pattern.
     */
    private DoubleType() {
    }

    @Override
    public String name() {
        return "double";
    }

    @Override
    public String toString() {
        return name();
    }

    @Override
    public Double valueOf(String s) throws ParseException {
        return Double.valueOf(s);
    }
}
