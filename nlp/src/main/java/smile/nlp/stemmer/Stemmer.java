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

package smile.nlp.stemmer;

/**
 * A Stemmer transforms a word into its root form. The stemming is a process
 * for removing the commoner morphological and inflexional endings from words
 * (in English). Its main use is as part of a term normalisation process.
 *
 * @author Haifeng Li
 */
public interface Stemmer {
    /**
     * Transforms a word into its root form.
     */
    public String stem(String word);
}
