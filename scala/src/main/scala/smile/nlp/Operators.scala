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

import smile.math.Math

import scala.collection.JavaConverters._
import smile.nlp.collocation._
import smile.nlp.pos.{HMMPOSTagger, PennTreebankPOS}
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
    * Each n-gram of words may then be scored according to some association measure,
    * in order to determine the relative likelihood of each n-gram being a
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
          sentence.words("none").map { word =>
            porter.stripPluralParticiple(word).toLowerCase
          }
        }
      }

      println(sentences)
      val ngrams = phrase.extract(sentences.asJava, maxNGramSize, minFreq)
      ngrams.asScala.map(_.asScala)
    }
  }

  /** Part-of-speech taggers.
    *
    * @param sentence a sentence that is already segmented to words.
    * @return the pos tags.
    */
  def postag(sentence: Array[String]): Array[PennTreebankPOS] = {
    time {
      HMMPOSTagger.getDefault.tag(sentence)
    }
  }

  /** Converts a bag of words to a feature vector.
    *
    * @param terms the token list used as features.
    * @param bag the bag of words.
    * @return a vector of frequency of feature tokens in the bag.
    */
  def vectorize(terms: Array[String], bag: Map[String, Int]): Array[Double] = {
    terms.map(bag.getOrElse(_, 0).toDouble)
  }

  /** Converts a binary bag of words to a sparse feature vector.
    *
    * @param terms the token list used as features.
    * @param bag the bag of words.
    * @return an integer vector, which elements are the indices of presented
    *         feature tokens in ascending order.
    */
  def vectorize(terms: Array[String], bag: Set[String]): Array[Int] = {
    terms.zipWithIndex.filter { case (w, _) => bag.contains(w)}.map(_._2)
  }

  /** Returns the document frequencies, i.e. the number of documents that contain term.
    *
    * @param terms the token list used as features.
    * @param corpus the training corpus.
    * @return the array of document frequencies.
    */
  def df(terms: Array[String], corpus: Array[Map[String, Int]]): Array[Int] = {
    terms.map { term =>
      corpus.filter(_.contains(term)).size
    }
  }

  /** TF-IDF relevance score between a term and a document based on a corpus.
    *
    * @param tf    the frequency of searching term in the document to rank.
    * @param maxtf the maximum frequency over all terms in the document.
    * @param n     the number of documents in the corpus.
    * @param df    the number of documents containing the given term in the corpus.
    */
  private def tfidf(tf: Double, maxtf: Double, n: Int, df: Int): Double = {
    (tf / maxtf) * Math.log((1.0 + n) / (1.0 + df))
  }

  /** Converts a corpus to TF-IDF feature vectors, which
    * are normalized to L2 norm 1.
    *
    * @param corpus the corpus of documents in bag-of-words representation.
    * @return a matrix of which each row is the TF-IDF feature vector.
    */
  def tfidf(corpus: Array[Array[Double]]): Array[Array[Double]] = {
    val n = corpus.size
    val df = new Array[Int](corpus(0).length)
    corpus.foreach { bag =>
      for (i <- 0 until df.length) {
        if (bag(i) > 0) df(i) = df(i) + 1
      }
      df
    }

    corpus.map { bag =>
      tfidf(bag, n, df)
    }
  }

  /** Converts a bag of words to a feature vector by TF-IDF, which
    * is normalized to L2 norm 1.
    *
    * @param bag the bag-of-words feature vector of a document.
    * @param n the number of documents in training corpus.
    * @param df the number of documents containing the given term in the corpus.
    * @return TF-IDF feature vector
    */
  def tfidf(bag: Array[Double], n: Int, df: Array[Int]): Array[Double] = {
    val maxtf = bag.max
    val features = new Array[Double](bag.length)

    for (i <- 0 until features.length) {
      features(i) = tfidf(bag(i), maxtf, n, df(i))
    }

    val norm = Math.norm(features)
    for (i <- 0 until features.length) {
      features(i) = features(i) / norm
    }

    features
  }
}