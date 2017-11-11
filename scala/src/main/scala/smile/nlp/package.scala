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
import smile.nlp.dictionary.StopWords

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
  import smile.nlp.dictionary.{EnglishPunctuations, EnglishStopWords}
  import smile.nlp.normalizer.SimpleNormalizer
  import smile.nlp.pos.{HMMPOSTagger, PennTreebankPOS}
  import smile.nlp.stemmer.{PorterStemmer, Stemmer}
  import smile.util.time

  private[nlp] class PimpedString(text: String) {
    val tokenizer = new SimpleTokenizer(true)
    val keywordExtractor = new CooccurrenceKeywordExtractor

    /**
      * Normalize Unicode text:
      * <ul>
      * <li>Apply Unicode normalization form NFKC.</li>
      * <li>Strip, trim, normalize, and compress whitespace.</li>
      * <li>Remove control and formatting characters.</li>
      * <li>Normalize double and single quotes.</li>
      * </ul>
      */
    def normalize: String = {
      SimpleNormalizer.getInstance().normalize(text)
    }

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
      *
      * If the parameter filter is not "none", the method will also filter
      * out stop words and punctuations. There is no definite list of stop
      * words which all tools incorporate. The valid values of the parameter
      * filter include
      *   - "none": no filtering
      *   - "default": the default English stop word list
      *   - "comprehensive": a more comprehensive English stop word list
      *   - "google": the stop words list used by Google search engine
      *   - "mysql": the stop words list used by MySQL FullText feature
      *   - custom stop word list: comma separated stop word list
      */
    def words(filter: String = "default"): Array[String] = {
      val tokens = tokenizer.split(text)

      if (filter == "none") return tokens

      val dict = filter.toLowerCase match {
        case "default" => EnglishStopWords.DEFAULT
        case "comprehensive" => EnglishStopWords.COMPREHENSIVE
        case "google" => EnglishStopWords.GOOGLE
        case "mysql" => EnglishStopWords.MYSQL
        case _ => new StopWords {
          val dict = filter.split(",").toSet

          override def contains(word: String): Boolean = dict.contains(word)

          override def size: Int = dict.size

          override def iterator: java.util.Iterator[String] = dict.iterator.asJava
        }
      }

      val punctuations = EnglishPunctuations.getInstance()
      tokens.filter { word =>
        !(dict.contains(word.toLowerCase) || punctuations.contains(word))
      }
    }

    /** Returns the bag of words. The bag-of-words model is a simple
      * representation of text as the bag of its words, disregarding
      * grammar and word order but keeping multiplicity.
      *
      * @param filter stop list for filtering.
      * @param stemmer stemmer to transform a word into its root form.
      */
    def bag(filter: String = "default", stemmer: Option[Stemmer] = Some(new PorterStemmer())): Map[String, Int] = {
      val words = text.normalize.sentences.flatMap(_.words(filter))

      val tokens = stemmer.map { stemmer =>
        words.map(stemmer.stem(_))
      }.getOrElse(words)

      val map = tokens.map(_.toLowerCase).groupBy(identity)
      map.map { case (k, v) => (k, v.length) }.withDefaultValue(0)
    }

    /** Returns the binary bag of words. Presence/absence is used instead
      * of frequencies.
      */
    def bag2(stemmer: Option[Stemmer] = Some(new PorterStemmer())): Set[String] = {
      val words = text.normalize.sentences.flatMap(_.words())

      val tokens = stemmer.map { stemmer =>
        words.map(stemmer.stem(_))
      }.getOrElse(words)

      tokens.map(_.toLowerCase).toSet
    }

    /** Returns the (word, part-of-speech) pairs.
      * The text should be a single sentence.
      */
    def postag: Array[(String, PennTreebankPOS)] = {
      val words = text.words("none")
      words.zip(HMMPOSTagger.getDefault.tag(words))
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
