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
 * Meta Llama 3 model family. Meta Llama 3 uses a standard decoder-only
 * dense transformer architecture. Key design features include Grouped-Query
 * Attention (GQA) for faster inference, a large 128k token vocabulary
 * tokenizer, Rotary Position Embeddings (RoPE), and training scales
 * spanning dense models from 1B up to a 405B parameter flagship.
 * Trained with sequence lengths up to 8k tokens initially, scaling
 * to 128k in later 3.1 updates, utilizing explicit masks to block
 * cross-document attention.
 *
 * @author Haifeng Li
 */
package smile.llm.model.llama;