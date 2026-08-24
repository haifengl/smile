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
package smile.llm.cache;

import smile.deep.tensor.Tensor;

/**
 * FlashInfer CSR page-table metadata for a bound KV request window.
 *
 * <p>All tensors are {@code int32}. Layout matches FlashInfer / vLLM:
 * <ul>
 *   <li>{@code pagedKvIndptr} — {@code [batch + 1]} cumulative page counts</li>
 *   <li>{@code pagedKvIndices} — flattened physical page ids</li>
 *   <li>{@code pagedKvLastPageLen} — {@code [batch]} tokens in the last page
 *       ({@code 1..pageSize})</li>
 * </ul>
 *
 * @param pagedKvIndptr       CSR indptr over pages.
 * @param pagedKvIndices      page ids.
 * @param pagedKvLastPageLen  last-page lengths.
 * @param pageSize            tokens per page.
 *
 * @author Haifeng Li
 */
public record FlashInferKvMetadata(
        Tensor pagedKvIndptr,
        Tensor pagedKvIndices,
        Tensor pagedKvLastPageLen,
        int pageSize) implements AutoCloseable {

    @Override
    public void close() {
        if (pagedKvIndptr != null) pagedKvIndptr.close();
        if (pagedKvIndices != null) pagedKvIndices.close();
        if (pagedKvLastPageLen != null) pagedKvLastPageLen.close();
    }
}
