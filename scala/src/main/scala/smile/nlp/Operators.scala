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

package smile.nlp

import scala.collection.JavaConverters._
import smile.nlp.collocation._
import smile.nlp.pos.{PennTreebankPOS, HMMPOSTagger}
import smile.util._

/** High level NLP operators.
  *
  * @author Haifeng Li
  */
trait Operators {

  /** Creates an in-memory text corpus.
   *
   * @param text a set of text.
   */
  def corpus(text: Seq[String]): SimpleCorpus = {
    val corpus = new SimpleCorpus
    text.zipWithIndex.foreach { case (text, i) =>
      corpus.add(i.toString, "", text)
    }
    corpus
  }

  /** Identify bigram collocations (words that often appear consecutively) within
    * corpora. They may also be used to find other associations between word
    * occurrences.
    *
    * Finding collocations requires first calculating the frequencies of words
    * and their appearance in the context of other words. Often the collection
    * of words will then requiring filtering to only retain useful content terms.
    * Each ngram of words may then be scored according to some association measure,
    * in order to determine the relative likelihood of each ngram being a
    * collocation.
    *
    * @param k finds top k bigram.
    * @param minFreq the minimum frequency of collocation.
    * @param text input text.
    * @return significant bigram collocations in descending order
    *         of likelihood ratio.
    */
  def bigram(k: Int, minFreq: Int, text: String*): Array[BigramCollocation] = {
    time {
      val finder = new BigramCollocationFinder(minFreq)
      finder.find(corpus(text), k)
    }
  }

  /** Identify bigram collocations whose p-value is less than
    * the given threshold.
    *
    * @param p the p-value threshold
    * @param minFreq the minimum frequency of collocation.
    * @param text input text.
    * @return significant bigram collocations in descending order
    *         of likelihood ratio.
    */
  def bigram(p: Double, minFreq: Int, text: String*): Array[BigramCollocation] = {
    time {
      val finder = new BigramCollocationFinder(minFreq)
      finder.find(corpus(text), p)
    }
  }

  private val phrase = new AprioriPhraseExtractor

  /** An Apiori-like algorithm to extract n-gram phrases.
    *
    * @param maxNGramSize The maximum length of n-gram
    * @param minFreq The minimum frequency of n-gram in the sentences.
    * @param text input text.
    * @return An array of sets of n-grams. The i-th entry is the set of i-grams.
    */
  def ngram(maxNGramSize: Int, minFreq: Int, text: String*): Seq[Seq[NGram]] = {
    time {
      val sentences = text.flatMap { text =>
        text.sentences.map { sentence =>
          sentence.words.map { word =>
            porter.stripPluralParticiple(word).toLowerCase
          }
        }
      }

      val ngrams = phrase.extract(sentences.asJava, maxNGramSize, minFreq)
      ngrams.asScala.map(_.asScala)
    }
  }

  /** Part-of-speech taggers.
   *
   * @param sentence a sentence.
   * @return the pos tags.
   */
  def postag(sentence: String): Array[PennTreebankPOS] = {
    time {
      HMMPOSTagger.getDefault.tag(sentence.words)
    }
  }
}