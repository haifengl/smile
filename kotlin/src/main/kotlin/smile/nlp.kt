/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.nlp

import smile.math.MathEx
import smile.nlp.collocation.Bigram
import smile.nlp.collocation.NGram
import smile.nlp.dictionary.EnglishStopWords
import smile.nlp.dictionary.StopWords
import smile.nlp.pos.HMMPOSTagger
import smile.nlp.pos.PennTreebankPOS
import smile.nlp.stemmer.Stemmer

/** English word tokenizer, splitting adjoining words. */
private val tokenizer = smile.nlp.tokenizer.SimpleTokenizer(true)
/** Porter stemmer. */
private val porter = smile.nlp.stemmer.PorterStemmer()
/** Lancaster stemmer with default rules. */
private val lancaster = smile.nlp.stemmer.LancasterStemmer()

/**
 * Porter's stemming algorithm. The stemmer is based on the idea that the
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
fun porter(word: String): String = porter.stem(word)

/**
 * The Paice/Husk Lancaster stemming algorithm. The stemmer is a conflation
 * based iterative stemmer. The stemmer, although remaining efficient and
 * easily implemented, is known to be very strong and aggressive. The stemmer
 * utilizes a single table of rules, each of which may specify
 * the removal or replacement of an ending.
 */
fun lancaster(word: String): String = lancaster.stem(word)

/**
 * Creates an in-memory text corpus.
 *
 * @param text a set of text.
 */
fun corpus(text: List<String>): SimpleCorpus {
    return SimpleCorpus().apply {
        text.forEach { s -> add(Text(s)) }
    }
}

/**
 * Identify bigram collocations (words that often appear consecutively) within
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
fun bigram(k: Int, minFreq: Int, text: List<String>): Array<Bigram> {
    return Bigram.of(corpus(text), k, minFreq)
}

/**
 * Identify bigram collocations whose p-value is less than
 * the given threshold.
 *
 * @param p the p-value threshold
 * @param minFreq the minimum frequency of collocation.
 * @param text input text.
 * @return significant bigram collocations in descending order
 *         of likelihood ratio.
 */
fun bigram(p: Double, minFreq: Int, text: List<String>): Array<Bigram> {
    return Bigram.of(corpus(text), p, minFreq)
}

/**
 * An Apiori-like algorithm to extract n-gram phrases.
 *
 * @param maxNGramSize The maximum length of n-gram
 * @param minFreq The minimum frequency of n-gram in the sentences.
 * @param text input text.
 * @return An array of sets of n-grams. The i-th entry is the set of i-grams.
 */
fun ngram(maxNGramSize: Int, minFreq: Int, text: List<String>): Array<Array<NGram>> {
    val sentences = text.flatMap { doc ->
        doc.sentences().map { sentence ->
            sentence.words("none")
                    .map { word -> porter.stripPluralParticiple(word).toLowerCase() }
                    .toTypedArray()
        }
    }

    return NGram.of(sentences, maxNGramSize, minFreq)
}

/**
 * Part-of-speech taggers.
 *
 * @param sentence a sentence that is already segmented to words.
 * @return the pos tags.
 */
fun postag(sentence: Array<String>): Array<PennTreebankPOS> {
    return smile.nlp.pos.HMMPOSTagger.getDefault().tag(sentence)
}

/**
 * Converts a bag of words to a feature vector.
 *
 * @param terms the token list used as features.
 * @param bag the bag of words.
 * @return a vector of frequency of feature tokens in the bag.
 */
fun vectorize(terms: Array<String>, bag: Map<String, Int>): DoubleArray {
    return terms
            .map { bag.getOrDefault(it, 0).toDouble() }
            .toDoubleArray()
}

/**
 * Converts a binary bag of words to a sparse feature vector.
 *
 * @param terms the token list used as features.
 * @param bag the bag of words.
 * @return an integer vector, which elements are the indices of presented
 *         feature tokens in ascending order.
 */
fun vectorize(terms: List<String>, bag: Set<String>): IntArray {
    return terms
            .mapIndexed { index, term -> index to term }
            .filter { (_, term) -> bag.contains(term) }
            .map { (index, _) -> index }
            .toIntArray()
}

/**
 * Returns the document frequencies, i.e. the number of documents that contain term.
 *
 * @param terms the token list used as features.
 * @param corpus the training corpus.
 * @return the array of document frequencies.
 */
fun df(terms: List<String>, corpus: List<Map<String, Int>>): IntArray {
    return terms
            .map { term -> corpus.filter { it.contains(term) }.size }
            .toIntArray()
}

/**
 * TF-IDF relevance score between a term and a document based on a corpus.
 *
 * @param tf    the frequency of searching term in the document to rank.
 * @param maxtf the maximum frequency over all terms in the document.
 * @param n     the number of documents in the corpus.
 * @param df    the number of documents containing the given term in the corpus.
 */
fun tfidf(tf: Double, maxtf: Double, n: Int, df: Int): Double {
    return (tf / Math.max(1.0, maxtf)) * Math.log10((1.0 + n) / (1.0 + df))
}

/**
 * Converts a corpus to TF-IDF feature vectors, which
 * are normalized to L2 norm 1.
 *
 * @param corpus the corpus of documents in bag-of-words representation.
 * @return a matrix of which each row is the TF-IDF feature vector.
 */
fun tfidf(corpus: List<DoubleArray>): List<DoubleArray> {
    val n = corpus.size
    val df = IntArray(corpus[0].size)
    corpus.forEach { bag ->
        for (i in df.indices) {
            if (bag[i] > 0) df[i] = df[i] + 1
        }
    }
    return corpus.map { bag -> tfidf(bag, n, df) }
}

/**
 * Converts a bag of words to a feature vector by TF-IDF, which
 * is normalized to L2 norm 1.
 *
 * @param bag the bag-of-words feature vector of a document.
 * @param n the number of documents in training corpus.
 * @param df the number of documents containing the given term in the corpus.
 * @return TF-IDF feature vector
 */
fun tfidf(bag: DoubleArray, n: Int, df: IntArray): DoubleArray {
    val maxtf = bag.maxOrNull() ?: 0.0
    val features = DoubleArray(bag.size)
    for (i in features.indices) {
        features[i] = tfidf(bag[i], maxtf, n, df[i])
    }

    MathEx.unitize(features)
    return features
}

/**
 * Normalizes Unicode text.
 * <ul>
 * <li>Apply Unicode normalization form NFKC.</li>
 * <li>Strip, trim, normalize, and compress whitespace.</li>
 * <li>Remove control and formatting characters.</li>
 * <li>Normalize double and single quotes.</li>
 * </ul>
 */
fun String.normalize(): String {
    return smile.nlp.normalizer.SimpleNormalizer.getInstance().normalize(this)
}

/**
 * Splits English text into sentences. Given an English text,
 * it returns a list of strings, where each element is an
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
fun String.sentences(): Array<String> {
    return smile.nlp.tokenizer.SimpleSentenceSplitter.getInstance().split(this)
}

/** Tokenizes English sentences with some differences from
 * TreebankWordTokenizer, notably on handling not-contractions. If a period
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
fun String.words(filter: String = "default"): Array<String> {
    val tokens = tokenizer.split(this)

    if (filter == "none") return tokens

    val dict = when (filter) {
        "default" -> EnglishStopWords.DEFAULT
        "comprehensive" -> EnglishStopWords.COMPREHENSIVE
        "google" -> EnglishStopWords.GOOGLE
        "mysql" -> EnglishStopWords.MYSQL
        else -> object : StopWords {
            val dict = filter.split(",").toSet()
            override fun contains(word: String): Boolean = dict.contains(word)
            override fun size(): Int = dict.size
            override fun iterator(): MutableIterator<String> = dict.iterator() as MutableIterator<String>
        }
    }

    val punctuations = smile.nlp.dictionary.EnglishPunctuations.getInstance()
    return tokens
            .filter { word -> !(dict.contains(word.toLowerCase()) || punctuations.contains(word)) }
            .toTypedArray()
}

/**
 * Returns the bag of words. The bag-of-words model is a simple
 * representation of text as the bag of its words, disregarding
 * grammar and word order but keeping multiplicity.
 *
 * @param filter stop list for filtering.
 * @param stemmer stemmer to transform a word into its root form.
 */
fun String.bag(filter: String = "default", stemmer: Stemmer? = porter): Map<String, Int> {
    var words = this.normalize().sentences().toList().flatMap { it.words(filter).toList() }
    if (stemmer != null) {
        words = words.map(stemmer::stem)
    }

    return words
            .map(String::toLowerCase)
            .groupBy { it }
            .mapValues { (_, v) -> v.size }
            .withDefault { 0 }
}

/**
 * Returns the binary bag of words. Presence/absence is used instead
 * of frequencies.
 */
fun String.bag2(filter: String = "default", stemmer: Stemmer? = porter): Set<String> {
    var words = this.normalize().sentences().toList().flatMap { it.words(filter).toList() }
    if (stemmer != null) {
        words = words.map(stemmer::stem)
    }

    return words.map(String::toLowerCase).toSet()
}

/**
 * Returns the (word, part-of-speech) pairs.
 * The text should be a single sentence.
 */
fun String.postag(): Array<PennTreebankPOS> {
    val words = this.words("none")
    return HMMPOSTagger.getDefault().tag(words)
}

/**
 * Keyword extraction from a single document using word co-occurrence
 * statistical information.
 *
 * @param k the number of top keywords to return.
 * @return the top keywords.
 */
fun String.keywords(k: Int = 10): Array<NGram> {
    return smile.nlp.keyword.CooccurrenceKeywords.of(this, k)
}
