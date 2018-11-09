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
 * Char data type.
 *
 * @author Haifeng Li
 */
public class CharType implements DataType {

    /** Singleton instance. */
    static CharType instance = new CharType();

    /**
     * Private constructor for singleton design pattern.
     */
    private CharType() {
    }

    @Override
    public String name() {
        return "char";
    }

    @Override
    public String toString() {
        return name();
    }

    @Override
    public Character valueOf(String s) throws ParseException {
        if (s == null || s.length() == 0) return null;
        return s.charAt(0);
    }
}
