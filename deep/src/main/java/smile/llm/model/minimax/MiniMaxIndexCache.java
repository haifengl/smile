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
package smile.llm.model.minimax;

import smile.deep.tensor.Device;
import smile.deep.tensor.Index;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;

/**
 * Key-only cache of MSA index keys, one {@code [numKeyHeads, indexDim]} vector per token and
 * sparse layer. It is addressed by the physical slots of the main {@link smile.llm.cache.KvCachePool},
 * so allocation, radix prefix reuse and eviction need no separate bookkeeping.
 *
 * @author Haifeng Li
 */
public class MiniMaxIndexCache implements AutoCloseable {
    private final Tensor cache;
    private final int numKeyHeads;
    private final int indexDim;

    /**
     * Constructor.
     *
     * @param numSparseLayers number of MSA layers.
     * @param numSlots        token slots, equal to the KV pool's.
     * @param numKeyHeads     index-key heads per token.
     * @param indexDim        index head dimension.
     * @param device          device of the KV pool.
     * @param dtype           storage dtype.
     */
    public MiniMaxIndexCache(int numSparseLayers, int numSlots, int numKeyHeads, int indexDim,
                             Device device, ScalarType dtype) {
        this.numKeyHeads = numKeyHeads;
        this.indexDim = indexDim;
        var options = new Tensor.Options().device(device).dtype(dtype).requireGradients(false);
        this.cache = Tensor.zeros(options, numSparseLayers, numSlots, numKeyHeads, indexDim);
    }

    /**
     * Writes index keys.
     *
     * @param ordinal sparse-layer ordinal.
     * @param slots   destination slots, length {@code N}.
     * @param keys    index keys, any shape holding {@code N * numKeyHeads * indexDim} elements.
     */
    public void put(int ordinal, long[] slots, Tensor keys) {
        try (var layerIdx = Index.of(ordinal);
             var layer = cache.get(layerIdx);
             var idx = Tensor.of(slots).to(cache.device());
             var flat = keys.reshape(slots.length, numKeyHeads, indexDim);
             var cast = flat.to(cache.dtype())) {
            layer.put_(cast, idx);
        }
    }

    /**
     * Reads index keys.
     *
     * @param ordinal sparse-layer ordinal.
     * @param slots   source slots, length {@code N}.
     * @return keys {@code [N, numKeyHeads, indexDim]}; caller owns the tensor.
     */
    /** {@link #put(int, long[], Tensor)} with the slots already on the device. */
    public void put(int ordinal, Tensor idx, Tensor keys) {
        try (var layerIdx = Index.of(ordinal);
             var layer = cache.get(layerIdx);
             var flat = keys.reshape(idx.shape()[0], numKeyHeads, indexDim);
             var cast = flat.to(cache.dtype())) {
            layer.put_(cast, idx);
        }
    }

    /** {@link #get(int, long[])} with the slots already on the device. */
    public Tensor get(int ordinal, Tensor idx) {
        try (var layerIdx = Index.of(ordinal);
             var layer = cache.get(layerIdx)) {
            return layer.get(idx);
        }
    }

    public Tensor get(int ordinal, long[] slots) {
        try (var layerIdx = Index.of(ordinal);
             var layer = cache.get(layerIdx);
             var idx = Tensor.of(slots).to(cache.device())) {
            return layer.get(idx);
        }
    }

    @Override
    public void close() {
        cache.close();
    }
}
