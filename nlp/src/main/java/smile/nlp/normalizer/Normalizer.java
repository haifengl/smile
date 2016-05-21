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

package smile.nlp.normalizer;

/**
 * Normalization transforms text into a canonical form by removing unwanted
 * variations. Normalization may range from light textual cleanup such as
 * compressing whitespace to more aggressive and knowledge-intensive forms
 * like standardizing date formats or expanding abbreviations. The nature and
 * extent of normalization, as well as whether it is most appropriate to apply
 * on the document, sentence, or token level, must be determined in the context
 * of a specific application.
 *
 * @author Mark Arehart
 */
public interface Normalizer {

    /**
     * Normalize the given string.
     */
    public String normalize(String text);
}
