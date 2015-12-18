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
package smile.nlp;

public interface TextTerms {
    
    /**
     * Returns the number of words.
     */
    public int size();

    /**
     * Returns the iterator of the words of the document.
     * The stop words and punctuations may be removed.
     */
    public Iterable<String> words();

    /**
     * Returns the iterator of unique words.
     */
    public Iterable<String> unique();

    /**
     * Returns the term frequency.
     */
    public int tf(String term);

    /**
     * Returns the maximum term frequency over all terms in the document.
     */
    public int maxtf();

}
