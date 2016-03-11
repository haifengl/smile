/*******************************************************************************
 * (C) Copyright 2015 Haifeng Li
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

package smile

import scala.language.implicitConversions
import scala.collection.JavaConverters._

/** Natural language processing.
  *
  * @author Haifeng Li
  */
package object nlp extends Operators {
  implicit def pimpString(string: String) = new PimpedString(string)

  /** Porter's stemming algorithm. The stemmer is based on the idea that the
    * suffixes in the English language are mostly made up of a combination of
    * smaller and simpler suffixes. This is a linear step stemmer.
    * Specifically it has five steps applying rules within each step. Within
    * each step, if a suffix rule matched to a word, then the conditions
    * attached to that rule are tested on what would be the resulting stem,
    * if that suffix was removed, in the way defined by the rule. Once a Rule
    * passes its conditions and is accepted the rule fires and the suffix is
    * removed and control moves to the next step. If the rule is not accepted
    * then the next rule in the step is tested, until either a rule from that
    * step fires and control passes to the next step or there are no more rules
    * in that step whence control moves to the next step.
    */
  val porter = new stemmer.PorterStemmer {
    def apply(word: String): String = stem(word)
  }

  /** The Paice/Husk Lancaster stemming algorithm. The stemmer is a conflation
    * based iterative stemmer. The stemmer, although remaining efficient and
    * easily implemented, is known to be very strong and aggressive. The stemmer
    * utilizes a single table of rules, each of which may specify
    * the removal or replacement of an ending.
    */
  val lancaster = new stemmer.LancasterStemmer {
    def apply(word: String): String = stem(word)
  }
}

package nlp {
  import tokenizer.{SimpleSentenceSplitter, SimpleTokenizer}
  import keyword.CooccurrenceKeywordExtractor

  private[nlp] class PimpedString(text: String) {
    val tokenizer = new SimpleTokenizer(true)
    val keywordExtractor = new CooccurrenceKeywordExtractor

    /** A simple sentence splitter for English. Given a string, assumed to
      * be English text, it returns a list of strings, where each element is an
      * English sentence. By default, it treats occurrences of '.', '?' and '!' as
      * sentence delimiters, but does its best to determine when an occurrence of '.'
      * does not have this role (e.g. in abbreviations, URLs, numbers, etc.).
      *
      * Recognizing the end of a sentence is not an easy task for a computer.
      * In English, punctuation marks that usually appear at the end of a sentence
      * may not indicate the end of a sentence. The period is the worst offender.
      * A period can end a sentence but it can also be part of an abbreviation
      * or acronym, an ellipsis, a decimal number, or part of a bracket of periods
      * surrounding a Roman numeral. A period can even act both as the end of an
      * abbreviation and the end of a sentence at the same time. Other the other
      * hand, some poems may not contain any sentence punctuation at all.
      *
      * Another problem punctuation mark is the single quote, which can introduce
      * a quote or start a contraction such as 'tis. Leading-quote contractions
      * are uncommon in contemporary English texts, but appear frequently in Early
      * Modern English texts.
      *
      * This tokenizer assumes that the text has already been segmented into
      * paragraphs. Any carriage returns will be replaced by whitespace.
      *
      * ====References:====
      *  - Paul Clough. A Perl program for sentence splitting using rules.
      */
    def sentences: Array[String] = {
      SimpleSentenceSplitter.getInstance.split(text)
    }

    /** A word tokenizer that tokenizes English sentences with some differences from
      * TreebankWordTokenizer, noteably on handling not-contractions. If a period
      * serves as both the end of sentence and a part of abbreviation, e.g. etc. at
      * the end of sentence, it will generate tokens of "etc." and "." while
      * TreebankWordTokenizer will generate "etc" and ".".
      *
      * Most punctuation is split from adjoining words. Verb contractions and the
      * Anglo-Saxon genitive of nouns are split into their component morphemes,
      * and each morpheme is tagged separately.
      *
      * This tokenizer assumes that the text has already been segmented into
      * sentences. Any periods -- apart from those at the end of a string or before
      * newline -- are assumed to be part of the word they are attached to (e.g. for
      * abbreviations, etc), and are not separately tokenized.
      */
    def words: Array[String] = {
      tokenizer.split(text)
    }

    /** Keyword extraction from a single document using word co-occurrence statistical information.
      *
      * @param k the number of top keywords to return.
      * @return the top keywords.
      */
    def keywords(k: Int = 10): Seq[NGram] = {
      keywordExtractor.extract(text, k).asScala
    }
  }
}
