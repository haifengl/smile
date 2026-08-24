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
package smile.llm.attention;

import smile.deep.tensor.Tensor;

/**
 * Attention compute kernel (contiguous SDPA or paged FlashInfer).
 *
 * @author Haifeng Li
 */
public interface AttentionKernel {
    /**
     * Runs attention.
     *
     * <p>For {@link AttentionBackend#TORCH_NATIVE}, {@code query}/{@code key}/{@code value}
     * are contiguous {@code [B,H,S,D]} / {@code [B,H,L,D]} and {@code mask} is optional.
     *
     * <p>For {@link AttentionBackend#FLASHINFER}, {@code query} is {@code [B,H,S,D]}
     * (or {@code [B,S,H,D]} — see kernel docs); {@code key}/{@code value} may be
     * {@code null} when {@link AttentionContext#isPaged()} (KV read from the pool).
     *
     * @param query query tensor.
     * @param key   key tensor, or {@code null} when paged.
     * @param value value tensor, or {@code null} when paged.
     * @param mask  attention mask, or {@code null}.
     * @param ctx   scale / causal / paged metadata.
     * @return attention output matching query layout expected by the caller
     *         ({@code [B,H,S,D]} for both backends in v1).
     */
    Tensor forward(Tensor query, Tensor key, Tensor value, Tensor mask, AttentionContext ctx);

    /** @return backend this kernel implements. */
    AttentionBackend backend();
}
