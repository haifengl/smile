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
 * A set of stop words in some language. Stop words is the name given to word
 * s which are filtered out prior to, or after, processing of natural language
 * text. There is no definite list of stop words which all NLP
 * tools incorporate. Not all NLP tools use a stoplist. Some
 * tools specifically avoid using them to support phrase search.
 * <p>
 * Stop words can cause problems when using a search engine to search for
 * phrases that include them, particularly in names such as 'The Who',
 * 'The The', or 'Take That'.
 * 
 * @author Haifeng Li
 */
public interface StopWords extends Dictionary {

}
