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
 * LLM Tokenization. Tokens are the fundamental unit, the "atom" of LLMs.
 * Tokenization is the process of translating text and converting them
 * into sequences of tokens and vice versa. A token is not necessarily
 * a word. It could be a smaller unit, like a part of a word, or a larger
 * one like a character or a whole phrase. The size of the tokens vary from
 * one tokenization approach to another.
 * <p>
 * Byte pair encoding (BPE, also known as digram coding) is a widely used
 * LLM tokenizer with an ability to combine both tokens that encode single
 * characters (including single digits or single punctuation marks) and those
 * that encode whole words (even the longest compound words). The algorithm,
 * in the first step, assumes all unique characters to be an initial set of
 * 1-character long n-grams (i.e. initial "tokens"). Then, successively the
 * most frequent pair of adjacent characters is merged into a new, 2-character
 * long n-gram and all instances of the pair are replaced by this new token.
 * This is repeated until a vocabulary of prescribed size is obtained.
 * Note that new words can always be constructed from final vocabulary tokens
 * and initial-set characters.
 *
 * @author Haifeng Li
 */
package smile.llm.tokenizer;
