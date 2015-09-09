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

package smile.nlp.tokenizer;

/**
 * A token is a string of characters, categorized according to the rules as a
 * symbol. The process of forming tokens from an input stream of characters
 * is called tokenization.
 * <p>
 * This is not as easy as it sounds. For example, when should a token containing
 * a hypen be split into two or more tokens? When does a period indicate the
 * end of an abbreviation as opposed to a sentence or a number or a
 * Roman numeral? Sometimes a period can act as a sentence terminator and
 * an abbreviation terminator at the same time. When should a single quote be
 * split from a word?
 *
 * @author Haifeng Li
 */
public interface Tokenizer {
    /**
     * Divide the given string into a list of substrings.
     */
    public String[] split(String text);
}
