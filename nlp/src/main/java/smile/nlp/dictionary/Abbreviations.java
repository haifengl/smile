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

/**
 * A dictionary interface for abbreviations.
 *
 * @author Haifeng Li
 */
public interface Abbreviations extends Dictionary {

    /**
     * Returns the full word for a given abbreviation. If the abbreviation
     * doesn't exist in the dictionary return null.
     */
    public String getFull(String abbr);

    /**
     * Returns the abbreviation for a word. If the word doesn't exist in the
     * dictionary return null.
     */
    public String getAbbreviation(String full);
}
