/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */

/**
 * A Transformer is a deep learning architecture, serves as the foundational
 * engine for modern Large Language Models (LLMs).
 * <p>
 * <h3>1. Tokenization and Embeddings</h3>
 * Raw text is first broken down into "tokens" (often subwords), which are
 * then translated into high-dimensional embedding vectors.
 * <h3>2. Positional Encoding</h3>
 * Because the transformer processes all tokens in parallel, it needs a way
 * to know the sequence of the words. Positional encoding solves this by
 * adding unique, position-dependent signals to token embeddings.
 * <h3>3. Self-Attention Mechanism</h3>
 * This is the heart of the Transformer, which dynamically weighs the
 * importance of different elements in a sequence. By computing how every
 * token relates to all other tokens, it assigns context-aware representations
 * for every token.
 * <h3>4. Feed-Forward Networks and Stacking</h3>
 * After passing through self-attention layers, the vector representations go
 * through a Feed-Forward Neural Network (MLP).
 * <p>
 * Multiple layers of these transformer blocks are stacked on top of each
 * other, allowing the LLM to build a progressively deeper, more abstract
 * understanding of the text. Depending on the task, Transformers are
 * typically structured in one of three ways:
 * <dl>
 * <dt>Encoder-Only</dt>
 * <dd>Models like BERT process input text to understand its meaning,
 * intent, or sentiment. They are ideal for text classification and extractive
 * search.</dd>
 * <dt>Decoder-Only</dt>
 * <dd>Models like GPT specialize in generating new text token by token
 * based on previous tokens. They are the driving force behind generative AI.</dd>
 * <dt>Encoder-Decoder</dt>
 * <dd>The original architecture combined both; the encoder processes
 * the input sequence (e.g., in English) and the decoder generates the output
 * sequence (e.g., translating it into French).</dd>
 * </dl>
 *
 * @author Haifeng Li
 */
package smile.llm.transformer;
