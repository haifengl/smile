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

import java.util.ArrayDeque;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.deep.CUDA;
import smile.deep.tensor.Device;
import smile.deep.tensor.Index;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.util.Tuple2;

/**
 * Physical KV-cache memory pool backed by a {@link RadixCache} for
 * prefix-sharing across requests.
 *
 * <p>On CUDA, sizing follows SGLang {@code --mem-fraction-static} ({@code y}):
 * <pre>
 *   staticBudget   = totalGpuMemory × y          // weights + static pools + KV
 *   dynamicReserve = totalGpuMemory × (1 − y)    // activations / temps (left free)
 *   kvBudget       = staticBudget − memoryAlreadyUsed
 *   numSlots       = min(kvBudget / bytesPerToken, maxBatchSize × maxSeqLen)
 * </pre>
 * Call {@link #allocate} only after model weights (and any other static pools
 * such as DeltaNet state) are on device so {@code memoryAlreadyUsed} is correct.
 *
 * <p>Slot indices are managed as fixed-size pages. The embedded
 * {@link RadixCache} maps token prefixes to those indices so that shared
 * system prompts and conversation history can be reused without recomputation.
 * Attention layers write and read activations through {@link #put} /
 * {@link #get} using request-scoped contiguous slot ranges established by
 * {@link #bindRequests}.
 *
 * @author Haifeng Li
 * @see RadixCache
 */
public class KvCachePool implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(KvCachePool.class);

    /** Default page size (tokens per allocation unit). */
    public static final int DEFAULT_PAGE_SIZE = 16;

    /** Number of transformer layers. */
    final int numLayers;
    /** Total number of token slots in the pool. */
    final int numSlots;
    /** Number of key/value attention heads. */
    final int numKvHeads;
    /** Dimension of each attention head. */
    final int headDim;
    /** Tokens per page. */
    final int pageSize;
    /** Element dtype of the cache buffers. */
    final ScalarType dtype;
    /** Device hosting the buffers. */
    final Device device;

    /** Key buffer shaped {@code [numLayers, numSlots, numKvHeads, headDim]}. */
    final Tensor kCache;
    /** Value buffer shaped {@code [numLayers, numSlots, numKvHeads, headDim]}. */
    final Tensor vCache;

    /** Free page indices (each page covers {@link #pageSize} consecutive slots). */
    final ArrayDeque<Integer> freePages = new ArrayDeque<>();

    /** Radix tree mapping token prefixes to pool slot indices. */
    final RadixCache radix;

    /**
     * Per-batch-item base slot for the currently bound request.
     * {@code null} when no request is bound.
     */
    private int[] requestBases;
    /** Contiguous capacity (slots) reserved for each bound request. */
    private int requestCapacity;

    /**
     * Result of SGLang-style static KV budget arithmetic (testable without CUDA).
     *
     * @param total          device total memory bytes.
     * @param used           bytes already held (typically weights + DeltaNet).
     * @param staticBudget   {@code total × memFraction}.
     * @param kvBudget       bytes available for KV ({@code staticBudget − used}).
     * @param dynamicReserve {@code total − staticBudget}.
     * @param numSlots       page-aligned slot count after context cap.
     * @param maxUsefulSlots {@code maxBatchSize × maxSeqLen} page-aligned.
     */
    public record StaticKvBudget(
            long total,
            long used,
            long staticBudget,
            long kvBudget,
            long dynamicReserve,
            int numSlots,
            int maxUsefulSlots) {}

    /**
     * Constructor.
     *
     * @param numLayers  number of transformer layers.
     * @param numSlots   total token slots (must be a multiple of {@code pageSize}).
     * @param numKvHeads number of KV heads.
     * @param headDim    head embedding dimension.
     * @param pageSize   tokens per page.
     * @param device     compute device.
     * @param dtype      element dtype.
     */
    public KvCachePool(int numLayers, int numSlots, int numKvHeads, int headDim,
                       int pageSize, Device device, ScalarType dtype) {
        if (numLayers < 1) throw new IllegalArgumentException("numLayers must be >= 1");
        if (numSlots < 1) throw new IllegalArgumentException("numSlots must be >= 1");
        if (numKvHeads < 1) throw new IllegalArgumentException("numKvHeads must be >= 1");
        if (headDim < 1) throw new IllegalArgumentException("headDim must be >= 1");
        if (pageSize < 1) throw new IllegalArgumentException("pageSize must be >= 1");
        if (numSlots % pageSize != 0) {
            throw new IllegalArgumentException("numSlots must be a multiple of pageSize");
        }

        this.numLayers = numLayers;
        this.numSlots = numSlots;
        this.numKvHeads = numKvHeads;
        this.headDim = headDim;
        this.pageSize = pageSize;
        this.device = device;
        this.dtype = dtype;
        this.radix = new RadixCache(pageSize);

        // Allocate K/V directly on the target device (zeros have no content to copy).
        var options = new Tensor.Options().device(device).dtype(dtype).requireGradients(false);
        Tensor k = Tensor.zeros(options, numLayers, numSlots, numKvHeads, headDim);
        Tensor v = Tensor.zeros(options, numLayers, numSlots, numKvHeads, headDim);
        // Long-lived pool buffers must not be owned by a transient AutoScope.
        k.detachFromScopes();
        v.detachFromScopes();

        if (device.isCUDA() && (!k.device().isCUDA() || !v.device().isCUDA())) {
            String msg = String.format(
                    "KvCachePool failed to allocate on CUDA (requested=%s, k=%s, v=%s)",
                    device, k.device(), v.device());
            k.close();
            v.close();
            throw new IllegalStateException(msg);
        }
        this.kCache = k;
        this.vCache = v;

        int numPages = numSlots / pageSize;
        for (int p = 0; p < numPages; p++) {
            freePages.addLast(p);
        }

        long kBytes = smile.torch.Native.nbytes(kCache);
        long vBytes = smile.torch.Native.nbytes(vCache);
        logger.info("KvCachePool: layers={}, slots={}, kvHeads={}, headDim={}, pageSize={}, "
                        + "dtype={}, device={}, kCache.device={}, footprintMiB={}",
                numLayers, numSlots, numKvHeads, headDim, pageSize, dtype, device,
                kCache.device(), (kBytes + vBytes) / (1024 * 1024));
    }

    /**
     * Allocates a pool using SGLang {@code mem-fraction-static} semantics.
     *
     * <p>Call this <em>after</em> model weights (and other static buffers) are
     * loaded. On CUDA, {@code memFraction} is applied to <em>total</em> device
     * memory; KV receives whatever remains inside that static region.
     *
     * @param layout      family-agnostic cache layout.
     * @param device      compute device.
     * @param dtype       cache element dtype (typically the model weight dtype).
     * @param memFraction static-region fraction of total GPU memory ({@code (0, 1]}).
     * @param pageSize    tokens per page.
     * @return the allocated pool.
     */
    public static KvCachePool allocate(KvCacheLayout layout, Device device, ScalarType dtype,
                                       double memFraction, int pageSize) {
        if (memFraction <= 0 || memFraction > 1) {
            throw new IllegalArgumentException("memFraction must be in (0, 1]: " + memFraction);
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be >= 1");
        }

        int dtypeBytes = elementSize(dtype);
        long bytesPerToken = 2L * layout.numLayers() * layout.numKvHeads()
                * layout.headDim() * dtypeBytes;

        final int numSlots;
        if (device.isCUDA()) {
            device.emptyCache();
            int index = device.index();
            // Use one cudaMemGetInfo snapshot for both free and total so the
            // SGLang used = total - free arithmetic is internally consistent.
            long[] mem = smile.torch.Native.cudaMemGetInfo(index);
            long free = mem[0];
            long total = mem[1] > 0 ? mem[1] : CUDA.totalMemory(index);
            if (total <= 0) {
                throw new IllegalStateException("CUDA totalMemory returned " + total
                        + " for device " + index);
            }
            if (free < 0 || free > total) {
                throw new IllegalStateException(String.format(
                        "CUDA memory info inconsistent: free=%d total=%d device=%d",
                        free, total, index));
            }
            StaticKvBudget budget = computeStaticKvBudget(
                    total, free, memFraction, bytesPerToken, pageSize,
                    layout.maxBatchSize(), layout.maxSeqLen());
            logger.info("KV static budget (SGLang-style): total={}, used={}, staticBudget={} "
                            + "(fraction={}), kvBudget={}, dynamicReserve={}, slots={}, "
                            + "maxUsefulSlots={}",
                    budget.total(), budget.used(), budget.staticBudget(), memFraction,
                    budget.kvBudget(), budget.dynamicReserve(), budget.numSlots(),
                    budget.maxUsefulSlots());
            if (budget.numSlots() < budget.maxUsefulSlots()) {
                logger.warn("KV cache budget yields {} slots < configured maxBatchSize*maxSeqLen={}; "
                                + "long contexts may fail at bind. Lower smile.chat.max-seq-len "
                                + "or raise smile.chat.mem-fraction-static.",
                        budget.numSlots(), budget.maxUsefulSlots());
            } else if (budget.kvBudget() > (long) budget.maxUsefulSlots() * bytesPerToken) {
                logger.info("KV cache slots capped at maxBatchSize*maxSeqLen={} "
                                + "({} bytes of static KV budget unused)",
                        budget.maxUsefulSlots(),
                        budget.kvBudget() - (long) budget.maxUsefulSlots() * bytesPerToken);
            }
            numSlots = budget.numSlots();
        } else {
            // CPU fallback: size to maxBatchSize × maxSeqLen (tests / CPU inference).
            long budget = bytesPerToken * layout.maxBatchSize() * layout.maxSeqLen();
            numSlots = slotsFromBudget(budget, bytesPerToken, pageSize,
                    layout.maxBatchSize(), layout.maxSeqLen()).numSlots();
        }

        return new KvCachePool(layout.numLayers(), numSlots, layout.numKvHeads(),
                layout.headDim(), pageSize, device, dtype);
    }

    /**
     * Computes SGLang-style KV slot budget from device memory readings.
     *
     * @param total         total device memory bytes.
     * @param free          free device memory bytes (after {@code emptyCache}).
     * @param memFraction   static-region fraction {@code y}.
     * @param bytesPerToken bytes for one token of K+V across all layers.
     * @param pageSize      tokens per page.
     * @param maxBatchSize  configured max batch.
     * @param maxSeqLen     configured max sequence length.
     * @return budget breakdown and slot count.
     */
    public static StaticKvBudget computeStaticKvBudget(
            long total, long free, double memFraction, long bytesPerToken, int pageSize,
            int maxBatchSize, int maxSeqLen) {
        if (total <= 0) throw new IllegalArgumentException("total must be > 0");
        if (free < 0 || free > total) {
            throw new IllegalArgumentException("free must be in [0, total]");
        }
        if (memFraction <= 0 || memFraction > 1) {
            throw new IllegalArgumentException("memFraction must be in (0, 1]");
        }
        if (bytesPerToken < 1) throw new IllegalArgumentException("bytesPerToken must be >= 1");
        if (pageSize < 1) throw new IllegalArgumentException("pageSize must be >= 1");

        long used = total - free;
        long staticBudget = (long) (total * memFraction);
        long dynamicReserve = total - staticBudget;
        long kvBudget = staticBudget - used;
        if (kvBudget < bytesPerToken * (long) pageSize) {
            throw new IllegalStateException(String.format(
                    "KV static budget too small: staticBudget=%d used=%d kvBudget=%d "
                            + "(need at least one page = %d bytes). Lower weights/static pools "
                            + "or raise smile.chat.mem-fraction-static.",
                    staticBudget, used, kvBudget, bytesPerToken * pageSize));
        }

        var slots = slotsFromBudget(kvBudget, bytesPerToken, pageSize, maxBatchSize, maxSeqLen);
        return new StaticKvBudget(total, used, staticBudget, kvBudget, dynamicReserve,
                slots.numSlots(), slots.maxUsefulSlots());
    }

    /**
     * Converts a byte budget into page-aligned slots, capped at
     * {@code maxBatchSize × maxSeqLen}.
     *
     * @param kvBudgetBytes bytes available for KV buffers.
     * @param bytesPerToken bytes for one token of K+V across all layers.
     * @param pageSize      tokens per page.
     * @param maxBatchSize  configured max batch.
     * @param maxSeqLen     configured max sequence length.
     * @return page-aligned slot count and the context cap.
     */
    static SlotCount slotsFromBudget(long kvBudgetBytes, long bytesPerToken, int pageSize,
                                     int maxBatchSize, int maxSeqLen) {
        int fromBudget = (int) Math.min(Integer.MAX_VALUE,
                Math.max(pageSize, kvBudgetBytes / bytesPerToken));
        fromBudget = (fromBudget / pageSize) * pageSize;
        if (fromBudget < pageSize) {
            fromBudget = pageSize;
        }

        long maxUsefulLong = (long) maxBatchSize * (long) maxSeqLen;
        int maxUsefulSlots = (int) Math.min(Integer.MAX_VALUE, Math.max(pageSize, maxUsefulLong));
        maxUsefulSlots = ((maxUsefulSlots + pageSize - 1) / pageSize) * pageSize;

        return new SlotCount(Math.min(fromBudget, maxUsefulSlots), maxUsefulSlots);
    }

    /**
     * Page-aligned slot count after applying the context cap.
     *
     * @param numSlots       slots to allocate.
     * @param maxUsefulSlots {@code maxBatchSize × maxSeqLen} page-aligned.
     */
    record SlotCount(int numSlots, int maxUsefulSlots) {}

    /**
     * Allocates a pool with {@link #DEFAULT_PAGE_SIZE}.
     *
     * @param layout      family-agnostic cache layout.
     * @param device      compute device.
     * @param dtype       cache element dtype.
     * @param memFraction static-region fraction of total GPU memory.
     * @return the allocated pool.
     */
    public static KvCachePool allocate(KvCacheLayout layout, Device device, ScalarType dtype,
                                       double memFraction) {
        return allocate(layout, device, dtype, memFraction, DEFAULT_PAGE_SIZE);
    }

    /**
     * Creates a small pool sized to {@code maxBatchSize × maxSeqLen} for unit tests.
     *
     * @param layout cache layout.
     * @param device compute device.
     * @return the test pool.
     */
    public static KvCachePool forTesting(KvCacheLayout layout, Device device) {
        int pageSize = 1;
        int numSlots = layout.maxBatchSize() * layout.maxSeqLen();
        return new KvCachePool(layout.numLayers(), numSlots, layout.numKvHeads(), layout.headDim(),
                pageSize, device, ScalarType.Float);
    }

    /**
     * Creates a tiny CPU-side placeholder pool used while model weights are
     * loaded. The inference engine replaces it with a sized GPU pool afterward
     * (see {@code smile.chat.mem-fraction-static}).
     *
     * @param layout cache layout.
     * @return a minimal CPU pool (one page).
     */
    public static KvCachePool bootstrap(KvCacheLayout layout) {
        int pageSize = DEFAULT_PAGE_SIZE;
        return new KvCachePool(layout.numLayers(), pageSize, layout.numKvHeads(), layout.headDim(),
                pageSize, Device.CPU(), ScalarType.Float);
    }

    /** Returns the embedded radix tree used for prefix sharing. */
    public RadixCache radix() {
        return radix;
    }

    /** Returns the number of transformer layers covered by this pool. */
    public int numLayers() {
        return numLayers;
    }

    /** Returns the total number of token slots. */
    public int numSlots() {
        return numSlots;
    }

    /** Returns the number of free pages. */
    public int freePages() {
        return freePages.size();
    }

    /** Returns the page size in tokens. */
    public int pageSize() {
        return pageSize;
    }

    /**
     * Reserves a contiguous slot range of {@code capacity} tokens for each
     * item in a batch. Must be called before {@link #put}/{@link #get} for a
     * request. Previously bound slots are released (not inserted into the
     * radix tree).
     *
     * @param batchSize number of parallel requests.
     * @param capacity  slots reserved per request (typically {@code maxSeqLen}).
     * @throws IllegalStateException if the pool lacks free pages.
     */
    public void bindRequests(int batchSize, int capacity) {
        unbindRequests();
        int pagesNeeded = (capacity + pageSize - 1) / pageSize;
        int aligned = pagesNeeded * pageSize;
        requestBases = new int[batchSize];
        requestCapacity = aligned;
        for (int b = 0; b < batchSize; b++) {
            requestBases[b] = allocContiguous(aligned);
        }
    }

    /**
     * Releases slots reserved by {@link #bindRequests} back to the free list.
     * Does not touch radix-tree entries.
     */
    public void unbindRequests() {
        if (requestBases == null) return;
        for (int base : requestBases) {
            freeContiguous(base, requestCapacity);
        }
        requestBases = null;
        requestCapacity = 0;
    }

    /**
     * Ensures a request binding large enough for {@code endPos} tokens exists.
     * Lazily binds when none is active (convenient for unit tests); grows by
     * rebinding when the current capacity is insufficient.
     *
     * @param batchSize number of parallel requests.
     * @param endPos    exclusive end position that will be accessed.
     */
    public void ensureRequest(int batchSize, int endPos) {
        if (requestBases != null
                && requestBases.length == batchSize
                && requestCapacity >= endPos) {
            return;
        }
        bindRequests(batchSize, Math.max(endPos, pageSize));
    }

    /**
     * Writes key/value activations for the bound batch at positions
     * {@code [startPos, startPos + seqlen)}.
     *
     * @param layer    layer index.
     * @param startPos starting position within each request's reserved range.
     * @param k        keys shaped {@code [batch, seqlen, numKvHeads, headDim]}.
     * @param v        values shaped {@code [batch, seqlen, numKvHeads, headDim]}.
     */
    public void put(int layer, int startPos, Tensor k, Tensor v) {
        long[] shape = k.shape();
        int batch = (int) shape[0];
        int seqlen = (int) shape[1];
        ensureRequest(batch, startPos + seqlen);

        long[] indices = new long[batch * seqlen];
        for (int b = 0; b < batch; b++) {
            int base = requestBases[b];
            for (int t = 0; t < seqlen; t++) {
                indices[b * seqlen + t] = base + startPos + t;
            }
        }

        try (var idx = Tensor.of(indices);
             var kf = k.reshape(batch * (long) seqlen, numKvHeads, headDim);
             var vf = v.reshape(batch * (long) seqlen, numKvHeads, headDim);
             var layerIdx = Index.of(layer);
             var layerK = kCache.get(layerIdx);
             var layerV = vCache.get(layerIdx)) {
            layerK.put_(kf, idx);
            layerV.put_(vf, idx);
        }
    }

    /**
     * Reads key/value activations for positions {@code [0, length)} of the
     * bound batch.
     *
     * @param layer  layer index.
     * @param length number of positions to read.
     * @return {@code (keys, values)} each shaped
     *         {@code [batch, length, numKvHeads, headDim]}.
     */
    public Tuple2<Tensor, Tensor> get(int layer, int length) {
        ensureBound();
        if (length > requestCapacity) {
            throw new IllegalArgumentException("KV read exceeds bound request capacity");
        }
        int batch = requestBases.length;
        long[] indices = new long[batch * length];
        for (int b = 0; b < batch; b++) {
            int base = requestBases[b];
            for (int t = 0; t < length; t++) {
                indices[b * length + t] = base + t;
            }
        }

        try (var idx = Tensor.of(indices);
             var layerIdx = Index.of(layer);
             var layerK = kCache.get(layerIdx);
             var layerV = vCache.get(layerIdx)) {
            // index_select materializes new storage; reshape is a view of that
            // storage. The flat tensors stay on the caller's Tensor.push scope
            // (not closed here) so LibTorch refcounting keeps storage alive.
            Tensor keys = layerK.get(idx).reshape(batch, length, numKvHeads, headDim);
            Tensor values = layerV.get(idx).reshape(batch, length, numKvHeads, headDim);
            return new Tuple2<>(keys, values);
        }
    }

    /**
     * Allocates {@code numTokens} slots (page-aligned) and returns their indices.
     * Used by the inference engine when inserting into the radix tree.
     *
     * @param numTokens number of tokens to allocate.
     * @return slot indices of length {@code alignedLen}.
     */
    public long[] alloc(int numTokens) {
        int pagesNeeded = (numTokens + pageSize - 1) / pageSize;
        int aligned = pagesNeeded * pageSize;
        int base = allocContiguous(aligned);
        long[] slots = new long[aligned];
        for (int i = 0; i < aligned; i++) {
            slots[i] = base + i;
        }
        return slots;
    }

    /**
     * Returns pages covering the given slot indices to the free list.
     * Slot indices should be page-aligned groups.
     *
     * @param slots slot indices to free.
     */
    public void free(long[] slots) {
        if (slots.length == 0) return;
        if (slots.length % pageSize != 0) {
            throw new IllegalArgumentException("free() requires page-aligned slot count");
        }
        for (int i = 0; i < slots.length; i += pageSize) {
            int page = (int) (slots[i] / pageSize);
            freePages.addLast(page);
        }
    }

    @Override
    public void close() {
        unbindRequests();
        radix.reset();
        kCache.close();
        vCache.close();
        freePages.clear();
    }

    // ===== Internal helpers =====

    private void ensureBound() {
        if (requestBases == null) {
            throw new IllegalStateException("No request bound; call bindRequests() first");
        }
    }

    private int allocContiguous(int numSlotsNeeded) {
        int pagesNeeded = numSlotsNeeded / pageSize;
        if (freePages.size() < pagesNeeded) {
            // Try to reclaim from the radix tree.
            int tokensNeeded = (pagesNeeded - freePages.size()) * pageSize;
            radix.evict(tokensNeeded, value -> {
                free(value.longArray());
                value.close();
            });
            if (freePages.size() < pagesNeeded) {
                throw new IllegalStateException(String.format(
                        "KV cache OOM: need %d pages, have %d free", pagesNeeded, freePages.size()));
            }
        }

        // Prefer contiguous pages when possible; otherwise pack from free list
        // into a freshly coalesced range by sorting.
        int[] pages = new int[pagesNeeded];
        for (int i = 0; i < pagesNeeded; i++) {
            pages[i] = freePages.removeFirst();
        }
        Arrays.sort(pages);

        // If pages are contiguous, use them as-is; otherwise we still use the
        // first page as "base" only when contiguous — for non-contiguous we
        // require eviction/compaction. For simplicity require contiguous run.
        boolean contiguous = true;
        for (int i = 1; i < pages.length; i++) {
            if (pages[i] != pages[0] + i) {
                contiguous = false;
                break;
            }
        }
        if (!contiguous) {
            // Put pages back and try to find a contiguous run in the free list.
            for (int p : pages) freePages.addLast(p);
            int basePage = findContiguousPages(pagesNeeded);
            if (basePage < 0) {
                throw new IllegalStateException("KV cache fragmented: cannot allocate "
                        + pagesNeeded + " contiguous pages");
            }
            return basePage * pageSize;
        }
        return pages[0] * pageSize;
    }

    private int findContiguousPages(int pagesNeeded) {
        int numPages = numSlots / pageSize;
        boolean[] free = new boolean[numPages];
        for (int p : freePages) free[p] = true;
        for (int start = 0; start <= numPages - pagesNeeded; start++) {
            boolean ok = true;
            for (int i = 0; i < pagesNeeded; i++) {
                if (!free[start + i]) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                for (int i = 0; i < pagesNeeded; i++) {
                    freePages.remove(Integer.valueOf(start + i));
                }
                return start;
            }
        }
        return -1;
    }

    private void freeContiguous(int baseSlot, int length) {
        int basePage = baseSlot / pageSize;
        int pages = length / pageSize;
        for (int i = 0; i < pages; i++) {
            freePages.addLast(basePage + i);
        }
    }

    /** Returns the element size in bytes for common floating dtypes. */
    static int elementSize(ScalarType dtype) {
        return switch (dtype) {
            case Float -> 4;
            case Double -> 8;
            case Half, BFloat16 -> 2;
            case Float8e4m3fn, Float8e5m2, Float8e4m3fnuz, Float8e5m2fnuz -> 1;
            default -> 2; // conservative default for quantized / uncommon types
        };
    }
}
