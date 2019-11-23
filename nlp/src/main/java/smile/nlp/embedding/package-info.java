/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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
 *******************************************************************************/

/**
 * Word embedding. Word embedding is the collective name for a set
 * of language modeling and feature learning techniques in natural
 * language processing where words or phrases from the vocabulary
 * are mapped to vectors of real numbers. Conceptually it involves
 * a mathematical embedding from a space with many dimensions per
 * word to a continuous vector space with a much lower dimension.
 *
 * Methods to generate this mapping include neural networks,
 * dimensionality reduction on the word co-occurrence matrix,
 * probabilistic models, explainable knowledge base method,
 * and explicit representation in terms of the context in
 * which words appear.
 *
 * Word and phrase embeddings, when used as the underlying input
 * representation, have been shown to boost the performance in
 * NLP tasks such as syntactic parsing and sentiment analysis.
 *
 * @author Haifeng Li
 */
package smile.nlp.embedding;
