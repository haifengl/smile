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
 * Alibaba Qwen3 model family. The Qwen3 family architecture uses a flexible
 * design combining dense and sparse Mixture-of-Experts (MoE) layouts,
 * a unified dual "thinking/non-thinking" mode, and hybrid linear-attention
 * blocks (such as Gated DeltaNet mixed with full attention) scaling from
 * 0.6B to over 2T parameters.
 *
 * Hybrid Attention Core: Integrates efficient linear attention (Gated DeltaNet)
 * for most layers to eliminate explosive KV-cache memory growth, while
 * strategically retaining full attention layers (e.g., a 3:1 ratio) to
 * preserve broad context recall.
 * 
 * Dense &amp; Sparse MoE Options: Ships with both dense parameter setups and
 * ultra-sparse Mixture-of-Experts variants where only a small active
 * fraction of total parameters fire per token.
 * 
 * Unified Thinking Modes: Built-in dynamic framework handles rapid
 * conversational responses as well as deep, multi-step explicit reasoning
 * without switching models.
 * 
 * Native Multimodality: Early-fusion architectures designed natively from
 * the base level to process cross-lingual text, vision (images/video), and
 * audio tokens.
 *
 * @author Haifeng Li
 */
package smile.llm.qwen;