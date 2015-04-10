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
package smile.nlp.dictionary;

import java.util.Iterator;

/**
 * A dictionary is a set of words in some natural language.
 *
 * @author Haifeng Li
 */
public interface Dictionary {

    /**
     * Returns true if this dictionary contains the specified word.
     */
    public boolean contains(String word);

    /**
     * Returns the number of elements in this dictionary.
     */
    public int size();

    /**
     * Returns an iterator over the elements in this dictionary.
     */
    public Iterator<String> iterator();
}
