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
 * A sentence splitter segments text into sentences (a string of words
 * satisfying the grammatical rules of a language).
 *
 * @author Haifeng Li
 */
public interface SentenceSplitter {
    /**
     * Split text into sentences.
     */
    public String[] split(String text);
}
