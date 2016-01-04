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

package smile.nlp.pos;

/**
 * Part-of-speech tagging (POS tagging) is the process of marking up the words
 * in a sentence as corresponding to a particular part of speech. Part-of-speech
 * tagging is hard because some words can represent more than one part of speech
 * at different times, and because some parts of speech are complex or unspoken.
 *
 * @author Haifeng Li
 */
public interface POSTagger {

    /**
     * Tags the sentence in the form of a sequence of words
     */
    public PennTreebankPOS[] tag(String[] sentence);
}
