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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
 * {@link #get} using a per-request slot map established by
 * {@link #bindRequests} or {@link #bindWithPrefix}.
 *
 * <p>The pool is static: bind claims free pages only and never grows the
 * underlying buffers. When the requested capacity exceeds free pages (after
 * radix eviction), bind clamps to what is available. Generation should stop
 * at {@link #requestCapacity()} and return partial results rather than
 * allocating more KV.
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
    /**
     * When {@link #dtype} is FP8, per-pool scales for K/V (running absmax).
     * BF16/FP16 pools leave these at {@code 1.0}.
     */
    private float kScale = 1.0f;
    private float vScale = 1.0f;
    /**
     * Dtype used when dequantizing FP8 KV for torch_native / FlashInfer compute
     * (BF16 on Ampere+, else FP16).
     */
    private final ScalarType computeDtype;

    /** Key buffer shaped {@code [numLayers, numSlots, numKvHeads, headDim]}. */
    final Tensor kCache;
    /** Value buffer shaped {@code [numLayers, numSlots, numKvHeads, headDim]}. */
    final Tensor vCache;

    /** Free page indices (each page covers {@link #pageSize} consecutive slots). */
    final ArrayDeque<Integer> freePages = new ArrayDeque<>();

    /** Radix tree mapping token prefixes to pool slot indices. */
    final RadixCache radix;

    /**
     * Per-batch-item slot map (SGLang {@code req_to_token}):
     * {@code requestSlots[b][pos]} is the pool slot for token position {@code pos}.
     * {@code null} when no request is bound.
     *
     * <p>In multi-request mode this is the active step batch set by
     * {@link #activateStep}; in exclusive {@link #bindRequests}/
     * {@link #bindWithPrefix} mode it holds the single legacy binding.
     */
    private long[][] requestSlots;
    /**
     * Capacity (slots) reserved for the active step: min row length when
     * multi-request capacities differ; exact capacity for legacy bind.
     */
    private int requestCapacity;
    /**
     * Page-aligned matched prefix length for legacy {@link #bindWithPrefix}.
     * Zero for contiguous {@link #bindRequests}. Unused in multi-request mode
     * (see {@link RequestBinding#matchedPrefixLen}).
     */
    private int matchedPrefixLen;
    /**
     * Slots allocated privately for the legacy exclusive request (suffix +
     * decode headroom). Not owned by the radix tree until
     * {@link #finishRequest} inserts them. Unused in multi-request mode.
     */
    private long[] privateSlots;
    /** Radix node locked for the legacy in-flight prefix match; {@code null} if none. */
    private RadixTreeNode lockedNode;
    /** When {@code false}, {@link #bindWithPrefix} falls back to contiguous bind. */
    private volatile boolean prefixReuseEnabled = true;

    /**
     * Multi-request bindings for continuous batching Instant Eviction.
     * Exclusive {@link #bindRequests}/{@link #bindWithPrefix} clear this map.
     */
    private final Map<Integer, RequestBinding> bindings = new LinkedHashMap<>();
    /** Next id returned by {@link #bindRequest}; starts at 1. */
    private final AtomicInteger nextRequestId = new AtomicInteger(1);
    /**
     * Request ids currently copied into {@link #requestSlots} by
     * {@link #activateStep}; {@code null} in exclusive legacy mode.
     */
    private int[] activeRequestIds;

    /**
     * Per-request binding state for multi-request continuous batching.
     *
     * @param slots             slot map ({@code slots[pos]} → pool index).
     * @param capacity          reserved slots for this request.
     * @param matchedPrefixLen  page-aligned matched prefix length.
     * @param privateSlots      privately allocated suffix/decode slots.
     * @param lockedNode        locked radix node, or {@code null}.
     */
    private record RequestBinding(
            long[] slots,
            int capacity,
            int matchedPrefixLen,
            long[] privateSlots,
            RadixTreeNode lockedNode) {}

    /** Lazily allocated FlashInfer workspace for this pool's device. */
    private smile.llm.attention.FlashInferWorkspace flashInferWorkspace;

    /** Cumulative prompt tokens seen by {@link #bindWithPrefix} (full length). */
    private final AtomicLong prefixPromptTokens = new AtomicLong();
    /** Cumulative matched prefix tokens from {@link #bindWithPrefix}. */
    private final AtomicLong prefixMatchTokens = new AtomicLong();
    /** Cumulative tokens inserted into the radix tree. */
    private final AtomicLong prefixInsertTokens = new AtomicLong();
    /** Cumulative tokens reclaimed by radix eviction. */
    private final AtomicLong prefixEvictTokens = new AtomicLong();

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
        this.computeDtype = resolveComputeDtype(device);
        if (smile.llm.quant.Fp8KvCodec.isFp8(dtype) && !device.isCUDA()) {
            throw new IllegalArgumentException("FP8 KV cache requires a CUDA device");
        }
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
                        + "dtype={}, computeDtype={}, device={}, kCache.device={}, footprintMiB={}",
                numLayers, numSlots, numKvHeads, headDim, pageSize, dtype, computeDtype, device,
                kCache.device(), (kBytes + vBytes) / (1024 * 1024));
        if (smile.llm.quant.Fp8KvCodec.isFp8(dtype)) {
            logger.info("KvCachePool: FP8 KV enabled (~2× capacity vs BF16 for same pool bytes); "
                    + "attention dequants to {}", computeDtype);
        }
    }

    private static ScalarType resolveComputeDtype(Device device) {
        if (device != null && device.isCUDA() && Tensor.isBF16Supported()) {
            return ScalarType.BFloat16;
        }
        return ScalarType.Half;
    }

    /** @return K-scale used for FP8 store (1.0 when not FP8). */
    public float kScale() {
        return kScale;
    }

    /** @return V-scale used for FP8 store (1.0 when not FP8). */
    public float vScale() {
        return vScale;
    }

    /** @return dtype used when dequantizing FP8 KV for attention. */
    public ScalarType computeDtype() {
        return computeDtype;
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
            logger.info("KV static budget: total={}, used={}, staticBudget={} "
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

        KvCachePool pool = new KvCachePool(layout.numLayers(), numSlots, layout.numKvHeads(),
                layout.headDim(), pageSize, device, dtype);
        if (device.isCUDA()) {
            try {
                long[] mem = smile.torch.Native.cudaMemGetInfo(device.index());
                logger.info("KV allocate done on {}: freeMiB={}, totalMiB={}",
                        device, mem[0] / (1024 * 1024), mem[1] / (1024 * 1024));
            } catch (RuntimeException e) {
                logger.debug("Post-KV cudaMemGetInfo failed: {}", e.toString());
            }
        }
        return pool;
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
        // Soft margin beyond (1−y): keep a little extra free for activation spikes
        // (Qwen DeltaNet / logits). Idle ~6GiB on 40GB/y=0.85 stays the main reserve.
        long softMargin = Math.min(1L << 30, Math.max(512L << 20, total / 40));
        long kvBudget = staticBudget - used - softMargin;
        if (kvBudget < bytesPerToken * (long) pageSize) {
            // Fall back to pure SGLang budget if soft margin does not fit.
            kvBudget = staticBudget - used;
        }
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
     * Returns the embedded radix tree used for prefix sharing.
     * @return the radix tree.
     */
    public RadixCache radix() {
        return radix;
    }

    /**
     * Returns the number of transformer layers covered by this pool.
     * @return layer count.
     */
    public int numLayers() {
        return numLayers;
    }

    /**
     * Returns the total number of token slots.
     * @return slot count.
     */
    public int numSlots() {
        return numSlots;
    }

    /**
     * Returns the number of free pages.
     * @return free page count.
     */
    public int freePages() {
        return freePages.size();
    }

    /**
     * Returns the number of free token slots ({@code freePages × pageSize}).
     * @return free slot count.
     */
    public int freeSlots() {
        return freePages.size() * pageSize;
    }

    /**
     * Returns the page size in tokens.
     * @return tokens per page.
     */
    public int pageSize() {
        return pageSize;
    }

    /**
     * Capacity reserved for the currently bound request, or {@code 0} if none.
     * Generation must not access positions {@code >=} this value.
     * @return bound request capacity in tokens.
     */
    public int requestCapacity() {
        return requestCapacity;
    }

    /**
     * Enables or disables SGLang-style prefix reuse via {@link #bindWithPrefix}.
     * When disabled, {@link #bindWithPrefix} behaves like contiguous
     * {@link #bindRequests}({@code 1}, {@code totalCapacity}).
     *
     * @param enabled {@code true} to match/insert against the radix tree.
     */
    public void setPrefixReuseEnabled(boolean enabled) {
        this.prefixReuseEnabled = enabled;
    }

    /**
     * Returns whether prefix reuse is enabled.
     * @return {@code true} if prefix reuse is enabled.
     */
    public boolean isPrefixReuseEnabled() {
        return prefixReuseEnabled;
    }

    /**
     * Cumulative prompt tokens seen by prefix bind (full length, not page-floored).
     * @return cumulative prompt token count.
     */
    public long prefixPromptTokens() {
        return prefixPromptTokens.get();
    }

    /**
     * Cumulative matched prefix tokens.
     * @return cumulative matched token count.
     */
    public long prefixMatchTokens() {
        return prefixMatchTokens.get();
    }

    /**
     * Cumulative tokens inserted into the radix tree.
     * @return cumulative inserted token count.
     */
    public long prefixInsertTokens() {
        return prefixInsertTokens.get();
    }

    /**
     * Reserves a page-aligned slot map for each item in a batch. Must be called
     * before {@link #put}/{@link #get} for a request. Previously bound slots
     * (including multi-request bindings) are released without radix insert.
     * Physical pages need not be contiguous (paged attention).
     *
     * <p>Capacity is page-aligned and <em>clamped</em> to free pages in the
     * static pool (after radix eviction). The pool never grows. Callers should
     * read {@link #requestCapacity()} and stop generation at that limit.
     *
     * @param batchSize number of parallel requests.
     * @param capacity  desired slots reserved per request (prompt + generation).
     * @throws IllegalStateException if no page can be reserved per request.
     */
    public void bindRequests(int batchSize, int capacity) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be >= 1");
        }
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1");
        }
        releaseAllBindings();
        int desired = pageAlignUp(capacity);
        tryEvictFor(batchSize * (long) desired);
        int maxPerRequest = (freeSlots() / batchSize / pageSize) * pageSize;
        int aligned = Math.min(desired, maxPerRequest);
        if (aligned < pageSize) {
            throw new IllegalStateException(String.format(
                    "KV cache exhausted: need at least %d slots per request for batch %d, have %d free",
                    pageSize, batchSize, freeSlots()));
        }
        if (aligned < desired) {
            logger.info("KV bind clamped: requested={}, bound={} (free slots before alloc={})",
                    desired, aligned, freeSlots());
        }
        requestSlots = new long[batchSize][];
        requestCapacity = aligned;
        matchedPrefixLen = 0;
        lockedNode = null;
        long[] allPrivate = new long[batchSize * aligned];
        int privateOffset = 0;
        for (int b = 0; b < batchSize; b++) {
            long[] slots = alloc(aligned);
            requestSlots[b] = slots;
            System.arraycopy(slots, 0, allPrivate, privateOffset, aligned);
            privateOffset += aligned;
        }
        privateSlots = allPrivate;
    }

    /**
     * Match a prompt against the radix tree, lock the matched node, allocate
     * suffix/decode slots, and bind a slot map for batch size 1.
     *
     * <p>Clears any multi-request bindings first so
     * {@link smile.llm.LanguageModel#generate} remains exclusive.
     *
     * <p>Desired {@code totalCapacity} is clamped to matched prefix plus free
     * private pages. The static pool never grows. If the prompt itself cannot
     * fit after clamping, this method throws.
     *
     * @param promptTokens  prompt token ids.
     * @param totalCapacity desired slots (prompt + generation headroom).
     * @return page-aligned matched prefix length (0 if miss or reuse disabled).
     * @throws IllegalArgumentException if the prompt cannot fit in free KV slots.
     */
    public int bindWithPrefix(int[] promptTokens, int totalCapacity) {
        if (promptTokens == null) {
            throw new IllegalArgumentException("promptTokens must not be null");
        }
        if (totalCapacity < 1) {
            throw new IllegalArgumentException("totalCapacity must be >= 1");
        }
        if (!prefixReuseEnabled) {
            bindRequests(1, totalCapacity);
            if (requestCapacity < promptTokens.length) {
                int bound = requestCapacity;
                releaseAllBindings();
                throw new IllegalArgumentException(String.format(
                        "Prompt length %d exceeds free KV capacity %d",
                        promptTokens.length, bound));
            }
            return 0;
        }

        releaseAllBindings();
        RequestBinding binding = allocatePrefixBinding(promptTokens, totalCapacity, false);
        requestSlots = new long[][]{binding.slots()};
        requestCapacity = binding.capacity();
        matchedPrefixLen = binding.matchedPrefixLen();
        privateSlots = binding.privateSlots();
        lockedNode = binding.lockedNode();
        return binding.matchedPrefixLen();
    }

    /**
     * Allocates slots for one request without clearing other multi-request
     * bindings. If the multi-request map was empty, activates this request.
     *
     * <p>Requires the full {@code totalCapacity} (page-aligned). When free KV
     * is insufficient, throws {@link KvCacheExhaustedException} so the caller
     * can leave the job queued until Instant Eviction frees pages.
     *
     * @param promptTokens  prompt token ids.
     * @param totalCapacity desired slots (prompt + generation headroom).
     * @return request id ({@code > 0}).
     * @throws KvCacheExhaustedException if the pool cannot reserve {@code totalCapacity}.
     */
    public int bindRequest(int[] promptTokens, int totalCapacity) {
        if (promptTokens == null) {
            throw new IllegalArgumentException("promptTokens must not be null");
        }
        if (totalCapacity < 1) {
            throw new IllegalArgumentException("totalCapacity must be >= 1");
        }

        boolean mapWasEmpty = bindings.isEmpty();
        // Exclusive legacy bind (LanguageModel.generate) yields to multi-request.
        if (mapWasEmpty && (requestSlots != null || privateSlots != null || lockedNode != null)) {
            releaseLegacyBinding();
        }

        RequestBinding binding;
        if (!prefixReuseEnabled) {
            binding = allocateContiguousBinding(totalCapacity, promptTokens.length, true);
        } else {
            binding = allocatePrefixBinding(promptTokens, totalCapacity, true);
        }

        int id = nextRequestId.getAndIncrement();
        bindings.put(id, binding);
        if (mapWasEmpty) {
            activateStep(id);
        }
        return id;
    }

    /**
     * Page-aligned matched prefix length for a multi-request binding.
     *
     * @param requestId id returned by {@link #bindRequest}.
     * @return matched prefix length, or {@code 0} if unknown.
     */
    public int matchedPrefixLen(int requestId) {
        RequestBinding binding = bindings.get(requestId);
        return binding == null ? 0 : binding.matchedPrefixLen();
    }

    /**
     * Instant Eviction: free this request's private pages; unlock its radix node.
     *
     * @param requestId id returned by {@link #bindRequest}.
     */
    public void unbindRequest(int requestId) {
        RequestBinding binding = bindings.remove(requestId);
        if (binding == null) {
            return;
        }
        freeBindingResources(binding, true);
        clearActivationIfPresent(requestId);
        if (bindings.isEmpty()) {
            // Drop activateStep leftovers so emptyCache is not blocked.
            requestSlots = null;
            requestCapacity = 0;
            activeRequestIds = null;
        }
    }

    /**
     * Inserts the sequence into the radix tree (prefix reuse) then
     * {@link #unbindRequest}.
     *
     * @param requestId      id returned by {@link #bindRequest}.
     * @param sequenceTokens prompt and generated token ids (no pad).
     */
    public void finishRequest(int requestId, int[] sequenceTokens) {
        RequestBinding binding = bindings.get(requestId);
        if (binding == null) {
            return;
        }
        if (!prefixReuseEnabled || sequenceTokens == null) {
            unbindRequest(requestId);
            return;
        }
        insertAndFreePrivate(binding, sequenceTokens);
        bindings.remove(requestId);
        freeBindingResources(binding, false);
        clearActivationIfPresent(requestId);
        if (bindings.isEmpty()) {
            requestSlots = null;
            requestCapacity = 0;
            activeRequestIds = null;
        }
    }

    /**
     * Sets the active step batch used by {@link #put}/{@link #get}/
     * {@link #buildFlashInferMetadata}/{@link #ensureRequest} to the given
     * request ids (in order). Capacities may differ per request;
     * {@link #ensureRequest} verifies each {@code requestSlots[b].length >= endPos}.
     *
     * @param requestIds bound request ids from {@link #bindRequest}.
     */
    public void activateStep(int... requestIds) {
        if (requestIds == null || requestIds.length == 0) {
            throw new IllegalArgumentException("requestIds must be non-empty");
        }
        long[][] slots = new long[requestIds.length][];
        int minCap = Integer.MAX_VALUE;
        for (int i = 0; i < requestIds.length; i++) {
            RequestBinding binding = bindings.get(requestIds[i]);
            if (binding == null) {
                throw new IllegalArgumentException("Unknown request id: " + requestIds[i]);
            }
            slots[i] = binding.slots();
            minCap = Math.min(minCap, binding.capacity());
        }
        requestSlots = slots;
        requestCapacity = minCap;
        // Multi-request private/lock state stays in RequestBinding only.
        matchedPrefixLen = 0;
        privateSlots = null;
        lockedNode = null;
        activeRequestIds = Arrays.copyOf(requestIds, requestIds.length);
    }

    /**
     * Number of currently bound requests (multi-request map size, or legacy
     * exclusive batch size when the map is empty).
     *
     * @return bound request count.
     */
    public int boundRequestCount() {
        if (!bindings.isEmpty()) {
            return bindings.size();
        }
        // Legacy exclusive bind: only count when activateStep is live.
        // Stale requestSlots with null activeRequestIds must not block emptyCache.
        if (activeRequestIds != null && requestSlots != null) {
            return requestSlots.length;
        }
        return 0;
    }

    /**
     * Inserts prompt+completion into the radix tree, frees duplicate/private
     * pages not retained by the tree, and unlocks the matched node.
     *
     * <p>If exactly one multi-request binding exists, finishes that binding.
     * Otherwise finishes the sole active legacy binding (LanguageModel.generate).
     *
     * @param sequenceTokens prompt and generated token ids (no pad).
     */
    public void finishRequest(int[] sequenceTokens) {
        if (bindings.size() == 1) {
            finishRequest(bindings.keySet().iterator().next(), sequenceTokens);
            return;
        }
        if (requestSlots == null) {
            return;
        }
        if (!prefixReuseEnabled || sequenceTokens == null) {
            releaseAllBindings();
            return;
        }

        int aligned = (sequenceTokens.length / pageSize) * pageSize;
        if (aligned > 0 && requestSlots.length == 1) {
            if (aligned > requestCapacity) {
                aligned = (requestCapacity / pageSize) * pageSize;
            }
            int[] tokens = Arrays.copyOf(sequenceTokens, aligned);
            long[] slots = Arrays.copyOf(requestSlots[0], aligned);
            try (Tensor kvIndices = Tensor.of(slots)) {
                InsertResult inserted = radix.insert(tokens, kvIndices);
                int keepFrom = inserted.prefixLen();
                // Newly stored tokens are [keepFrom, aligned); retain those pages.
                // Free private pages outside that retained range.
                freePrivateOutside(matchedPrefixLen, privateSlots, keepFrom, aligned);
                privateSlots = null;
                prefixInsertTokens.addAndGet(Math.max(0, aligned - keepFrom));
            }
        } else {
            // Nothing page-aligned to insert; free all private pages.
            freePrivateOutside(matchedPrefixLen, privateSlots, Integer.MAX_VALUE, 0);
            privateSlots = null;
        }

        unlockMatched();
        requestSlots = null;
        requestCapacity = 0;
        matchedPrefixLen = 0;
    }

    /**
     * Releases slots reserved by {@link #bindRequests} / {@link #bindWithPrefix}
     * (and any multi-request bindings) without inserting into the radix tree.
     * Tree-owned matched pages are not freed.
     */
    public void unbindRequests() {
        releaseAllBindings();
    }

    /**
     * Ensures a request binding exists and is large enough for {@code endPos}.
     * Never grows the static pool: if capacity is insufficient, throws
     * {@link KvCacheExhaustedException}. Callers must bind up front and stop
     * generation at {@link #requestCapacity()}.
     *
     * <p>When capacities differ across the active step, each
     * {@code requestSlots[b].length} must be {@code >= endPos}.
     *
     * @param batchSize number of parallel requests.
     * @param endPos    exclusive end position that will be accessed.
     * @throws KvCacheExhaustedException if unbound or {@code endPos} exceeds capacity.
     */
    public void ensureRequest(int batchSize, int endPos) {
        if (requestSlots != null && requestSlots.length == batchSize) {
            boolean ok = true;
            for (long[] row : requestSlots) {
                if (row.length < endPos) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                return;
            }
            int minLen = requestSlots[0].length;
            for (int b = 1; b < requestSlots.length; b++) {
                minLen = Math.min(minLen, requestSlots[b].length);
            }
            throw new KvCacheExhaustedException(String.format(
                    "KV cache exhausted: need endPos=%d, bound capacity=%d (pool does not grow)",
                    endPos, minLen));
        }
        if (requestSlots == null) {
            throw new KvCacheExhaustedException(
                    "No request bound; call bindRequests() or bindWithPrefix() first");
        }
        throw new KvCacheExhaustedException(String.format(
                "KV cache exhausted: need endPos=%d, bound capacity=%d (pool does not grow)",
                endPos, requestCapacity));
    }

    private void ensureRequestRow(int row, int endPos) {
        ensureBound();
        if (row < 0 || row >= requestSlots.length) {
            throw new IllegalArgumentException("KV row out of range: " + row);
        }
        if (endPos > requestSlots[row].length) {
            throw new KvCacheExhaustedException(String.format(
                    "KV cache exhausted: need endPos=%d, bound capacity=%d (pool does not grow)",
                    endPos, requestSlots[row].length));
        }
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
        int[] starts = new int[batch];
        Arrays.fill(starts, startPos);
        put(layer, starts, k, v);
    }

    /**
     * Writes key/value activations at a per-row start position (typically
     * decode {@code seqlen == 1} with unequal positions across the batch).
     *
     * @param layer     layer index.
     * @param startPos  write start position per batch row ({@code length == batch}).
     * @param k         keys shaped {@code [batch, seqlen, numKvHeads, headDim]}.
     * @param v         values shaped {@code [batch, seqlen, numKvHeads, headDim]}.
     */
    public void put(int layer, int[] startPos, Tensor k, Tensor v) {
        long[] shape = k.shape();
        int batch = (int) shape[0];
        int seqlen = (int) shape[1];
        if (startPos == null || startPos.length != batch) {
            throw new IllegalArgumentException("startPos length must equal batch size");
        }
        for (int b = 0; b < batch; b++) {
            ensureRequestRow(b, startPos[b] + seqlen);
        }

        long[] indices = new long[batch * seqlen];
        for (int b = 0; b < batch; b++) {
            long[] slots = requestSlots[b];
            int start = startPos[b];
            for (int t = 0; t < seqlen; t++) {
                indices[b * seqlen + t] = slots[start + t];
            }
        }

        try (var idx = Tensor.of(indices);
             var layerIdx = Index.of(layer);
             var layerK = kCache.get(layerIdx);
             var layerV = vCache.get(layerIdx)) {
            Tensor kf = k.reshape(batch * (long) seqlen, numKvHeads, headDim);
            Tensor vf = v.reshape(batch * (long) seqlen, numKvHeads, headDim);
            if (smile.llm.quant.Fp8KvCodec.isFp8(dtype)) {
                float ks = smile.llm.quant.Fp8KvCodec.computeScale(kf, smile.llm.quant.Fp8KvCodec.E4M3_MAX);
                float vs = smile.llm.quant.Fp8KvCodec.computeScale(vf, smile.llm.quant.Fp8KvCodec.E4M3_MAX);
                // Running max scale so older tokens stay in range.
                kScale = Math.max(kScale, ks);
                vScale = Math.max(vScale, vs);
                Tensor kq = smile.llm.quant.Fp8KvCodec.quantize(kf, kScale, dtype);
                Tensor vq = smile.llm.quant.Fp8KvCodec.quantize(vf, vScale, dtype);
                layerK.put_(kq, idx);
                layerV.put_(vq, idx);
                kq.close();
                vq.close();
            } else {
                layerK.put_(kf, idx);
                layerV.put_(vf, idx);
            }
            kf.close();
            vf.close();
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
        int batch = requestSlots.length;
        for (int b = 0; b < batch; b++) {
            if (length > requestSlots[b].length) {
                throw new IllegalArgumentException("KV read exceeds bound request capacity");
            }
        }
        long[] indices = new long[batch * length];
        for (int b = 0; b < batch; b++) {
            long[] slots = requestSlots[b];
            for (int t = 0; t < length; t++) {
                indices[b * length + t] = slots[t];
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
            if (smile.llm.quant.Fp8KvCodec.isFp8(dtype)) {
                Tensor kd = smile.llm.quant.Fp8KvCodec.dequantize(keys, kScale, computeDtype);
                Tensor vd = smile.llm.quant.Fp8KvCodec.dequantize(values, vScale, computeDtype);
                keys.close();
                values.close();
                return new Tuple2<>(kd, vd);
            }
            return new Tuple2<>(keys, values);
        }
    }

    /**
     * Builds FlashInfer CSR page-table metadata for positions {@code [0, length)}.
     *
     * <p>Assumes tokens pack densely into pages (vLLM-style): virtual page
     * {@code p} starts at request position {@code p * pageSize}, and the
     * physical page id is taken from {@code requestSlots[b][p * pageSize]}.
     *
     * @param length number of cached positions (inclusive of the current step).
     * @return CSR metadata; caller owns and must close.
     */
    public FlashInferKvMetadata buildFlashInferMetadata(int length) {
        ensureBound();
        if (length < 0) {
            throw new IllegalArgumentException("KV FlashInfer length out of range: " + length);
        }
        int batch = requestSlots.length;
        for (int b = 0; b < batch; b++) {
            if (length > requestSlots[b].length) {
                throw new IllegalArgumentException("KV FlashInfer length out of range: " + length);
            }
        }
        if (length == 0) {
            Tensor indptr = Tensor.of(new int[batch + 1]);
            Tensor indices = Tensor.of(new int[0]);
            Tensor last = Tensor.of(new int[batch]);
            indptr.detachFromScopes();
            indices.detachFromScopes();
            last.detachFromScopes();
            return new FlashInferKvMetadata(indptr, indices, last, pageSize);
        }

        int[] indptrArr = new int[batch + 1];
        int totalPages = 0;
        int[] lastPageLen = new int[batch];
        int[] pagesPerBatch = new int[batch];
        for (int b = 0; b < batch; b++) {
            int nPages = (length + pageSize - 1) / pageSize;
            pagesPerBatch[b] = nPages;
            int rem = length % pageSize;
            lastPageLen[b] = (rem == 0) ? pageSize : rem;
            indptrArr[b] = totalPages;
            totalPages += nPages;
        }
        indptrArr[batch] = totalPages;

        int[] flatIndices = new int[totalPages];
        int cursor = 0;
        for (int b = 0; b < batch; b++) {
            long[] slots = requestSlots[b];
            int nPages = pagesPerBatch[b];
            for (int p = 0; p < nPages; p++) {
                int pos = p * pageSize;
                flatIndices[cursor++] = (int) (slots[pos] / pageSize);
            }
        }

        Tensor indptrT;
        Tensor indicesT;
        Tensor lastT;
        if (device.isCUDA()) {
            try (Tensor iCpu = Tensor.of(indptrArr);
                 Tensor nCpu = Tensor.of(flatIndices);
                 Tensor lCpu = Tensor.of(lastPageLen)) {
                indptrT = iCpu.to(device);
                indicesT = nCpu.to(device);
                lastT = lCpu.to(device);
            }
        } else {
            indptrT = Tensor.of(indptrArr);
            indicesT = Tensor.of(flatIndices);
            lastT = Tensor.of(lastPageLen);
        }
        indptrT.detachFromScopes();
        indicesT.detachFromScopes();
        lastT.detachFromScopes();
        return new FlashInferKvMetadata(indptrT, indicesT, lastT, pageSize);
    }

    /**
     * Builds FlashInfer CSR page-table metadata with a per-row cached length
     * (ragged decode batches).
     *
     * @param lengths cached length per active request ({@code length[b] == cacheLen}).
     * @return CSR metadata; caller owns and must close.
     */
    public FlashInferKvMetadata buildFlashInferMetadata(int[] lengths) {
        ensureBound();
        if (lengths == null || lengths.length != requestSlots.length) {
            throw new IllegalArgumentException("lengths length must equal active batch size");
        }
        int batch = requestSlots.length;
        for (int b = 0; b < batch; b++) {
            if (lengths[b] < 0) {
                throw new IllegalArgumentException("KV FlashInfer length out of range: " + lengths[b]);
            }
            if (lengths[b] > requestSlots[b].length) {
                throw new IllegalArgumentException("KV FlashInfer length out of range: " + lengths[b]);
            }
        }
        boolean uniform = true;
        for (int b = 1; b < batch; b++) {
            if (lengths[b] != lengths[0]) {
                uniform = false;
                break;
            }
        }
        if (uniform) {
            return buildFlashInferMetadata(lengths[0]);
        }

        int[] indptrArr = new int[batch + 1];
        int totalPages = 0;
        int[] lastPageLen = new int[batch];
        int[] pagesPerBatch = new int[batch];
        for (int b = 0; b < batch; b++) {
            int length = lengths[b];
            if (length == 0) {
                pagesPerBatch[b] = 0;
                lastPageLen[b] = 0;
            } else {
                int nPages = (length + pageSize - 1) / pageSize;
                pagesPerBatch[b] = nPages;
                int rem = length % pageSize;
                lastPageLen[b] = (rem == 0) ? pageSize : rem;
            }
            indptrArr[b] = totalPages;
            totalPages += pagesPerBatch[b];
        }
        indptrArr[batch] = totalPages;

        int[] flatIndices = new int[totalPages];
        int cursor = 0;
        for (int b = 0; b < batch; b++) {
            long[] slots = requestSlots[b];
            int nPages = pagesPerBatch[b];
            for (int p = 0; p < nPages; p++) {
                int pos = p * pageSize;
                flatIndices[cursor++] = (int) (slots[pos] / pageSize);
            }
        }

        Tensor indptrT;
        Tensor indicesT;
        Tensor lastT;
        if (device.isCUDA()) {
            try (Tensor iCpu = Tensor.of(indptrArr);
                 Tensor nCpu = Tensor.of(flatIndices);
                 Tensor lCpu = Tensor.of(lastPageLen)) {
                indptrT = iCpu.to(device);
                indicesT = nCpu.to(device);
                lastT = lCpu.to(device);
            }
        } else {
            indptrT = Tensor.of(indptrArr);
            indicesT = Tensor.of(flatIndices);
            lastT = Tensor.of(lastPageLen);
        }
        indptrT.detachFromScopes();
        indicesT.detachFromScopes();
        lastT.detachFromScopes();
        return new FlashInferKvMetadata(indptrT, indicesT, lastT, pageSize);
    }

    /**
     * Returns the key cache buffer {@code [numLayers, numSlots, numKvHeads, headDim]}.
     * @return key cache.
     */
    public Tensor keyCache() {
        return kCache;
    }

    /**
     * Returns the value cache buffer {@code [numLayers, numSlots, numKvHeads, headDim]}.
     * @return value cache.
     */
    public Tensor valueCache() {
        return vCache;
    }

    /** @return number of KV heads stored in the pool. */
    public int numKvHeads() {
        return numKvHeads;
    }

    /** @return head dimension. */
    public int headDim() {
        return headDim;
    }

    /** @return hosting device. */
    public Device device() {
        return device;
    }

    /**
     * Returns (and lazily creates) the FlashInfer workspace for this pool.
     * @return workspace, or {@code null} when FlashInfer is unavailable.
     */
    public synchronized smile.llm.attention.FlashInferWorkspace flashInferWorkspace() {
        if (flashInferWorkspace == null
                && smile.llm.attention.AttentionBackends.current()
                    == smile.llm.attention.AttentionBackend.FLASHINFER) {
            flashInferWorkspace = smile.llm.attention.FlashInferWorkspace.create(
                    device.isCUDA() ? Byte.toUnsignedInt(device.index()) : 0, 0L);
        }
        return flashInferWorkspace;
    }

    /**
     * Allocates {@code numTokens} slots (page-aligned) and returns their indices.
     * Used by the inference engine when inserting into the radix tree.
     *
     * <p>Pages are packed from the free list and need not be physically contiguous.
     * Within each page, slot indices remain {@code pageId * pageSize + offset}
     * so FlashInfer / gather paths can treat the result as a standard page table.
     *
     * @param numTokens number of tokens to allocate.
     * @return slot indices of length {@code alignedLen}.
     */
    public long[] alloc(int numTokens) {
        int pagesNeeded = (numTokens + pageSize - 1) / pageSize;
        int aligned = pagesNeeded * pageSize;
        ensureFreePages(pagesNeeded);
        long[] slots = new long[aligned];
        for (int p = 0; p < pagesNeeded; p++) {
            int page = freePages.removeFirst();
            long base = (long) page * pageSize;
            int offset = p * pageSize;
            for (int i = 0; i < pageSize; i++) {
                slots[offset + i] = base + i;
            }
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
        releaseAllBindings();
        radix.reset();
        if (flashInferWorkspace != null) {
            flashInferWorkspace.close();
            flashInferWorkspace = null;
        }
        kCache.close();
        vCache.close();
        freePages.clear();
    }

    // ===== Internal helpers =====

    private void ensureBound() {
        if (requestSlots == null) {
            throw new IllegalStateException("No request bound; call bindRequests() or bindWithPrefix() first");
        }
    }

    /**
     * Full-capacity bind for one multi-request entry (like {@link #bindRequests}(1, …)
     * without releasing other bindings). Physical pages may be non-contiguous.
     *
     * @param requireFull when {@code true}, fail instead of clamping below
     *                    {@code totalCapacity} (continuous-batching admission).
     */
    private RequestBinding allocateContiguousBinding(int totalCapacity, int promptLen,
                                                     boolean requireFull) {
        int desired = pageAlignUp(totalCapacity);
        tryEvictFor(desired);
        int maxPerRequest = (freeSlots() / pageSize) * pageSize;
        int aligned = Math.min(desired, maxPerRequest);
        if (aligned < pageSize) {
            throw new KvCacheExhaustedException(String.format(
                    "KV cache exhausted: need at least %d slots, have %d free",
                    pageSize, freeSlots()));
        }
        if (aligned < desired) {
            if (requireFull) {
                throw new KvCacheExhaustedException(String.format(
                        "KV cache exhausted: need %d slots, have %d free",
                        desired, freeSlots()));
            }
            logger.info("KV bind clamped: requested={}, bound={} (free slots before alloc={})",
                    desired, aligned, freeSlots());
        }
        if (aligned < promptLen) {
            throw new IllegalArgumentException(String.format(
                    "Prompt length %d exceeds free KV capacity %d", promptLen, aligned));
        }
        long[] slots = alloc(aligned);
        return new RequestBinding(slots, aligned, 0, slots, null);
    }

    /**
     * Prefix-match bind for one request without releasing other bindings.
     * Also used by exclusive {@link #bindWithPrefix} after {@link #releaseAllBindings}.
     *
     * @param requireFull when {@code true}, fail instead of clamping below
     *                    {@code totalCapacity} (continuous-batching admission).
     */
    private RequestBinding allocatePrefixBinding(int[] promptTokens, int totalCapacity,
                                                 boolean requireFull) {
        int desired = pageAlignUp(totalCapacity);

        long[] matchedSlots;
        RadixTreeNode matchNode;
        try (MatchResult match = radix.matchPrefix(promptTokens)) {
            // Empty int64 tensors have no data_ptr; avoid longArray() on miss.
            matchedSlots = match.length() == 0 ? new long[0] : match.indices().longArray();
            matchNode = match.lastNode();
        }
        int prefixLen = matchedSlots.length;
        if (prefixLen > desired) {
            throw new IllegalStateException("matched prefix longer than totalCapacity");
        }

        RadixTreeNode locked = null;
        if (matchNode != null && matchNode != radix.root) {
            radix.incLockRef(matchNode);
            locked = matchNode;
        }

        int suffixDesired = desired - prefixLen;
        tryEvictFor(suffixDesired);
        int suffixAvail = freeSlots();
        int alignedCapacity = prefixLen + Math.min(suffixDesired, suffixAvail);
        if (alignedCapacity < desired) {
            if (requireFull) {
                if (locked != null) {
                    radix.decLockRef(locked);
                }
                throw new KvCacheExhaustedException(String.format(
                        "KV cache exhausted: need %d slots (prefix=%d), have %d free suffix",
                        desired, prefixLen, suffixAvail));
            }
            logger.info("KV bind clamped: requested={}, bound={} (prefix={}, free suffix slots={})",
                    desired, alignedCapacity, prefixLen, suffixAvail);
        }

        int promptLen = promptTokens.length;
        if (alignedCapacity < promptLen) {
            if (locked != null) {
                radix.decLockRef(locked);
            }
            throw new IllegalArgumentException(String.format(
                    "Prompt length %d exceeds free KV capacity %d (prefix=%d)",
                    promptLen, alignedCapacity, prefixLen));
        }

        int suffixLen = alignedCapacity - prefixLen;
        long[] suffix = suffixLen > 0 ? alloc(suffixLen) : new long[0];

        long[] map = new long[alignedCapacity];
        if (prefixLen > 0) {
            System.arraycopy(matchedSlots, 0, map, 0, prefixLen);
        }
        if (suffixLen > 0) {
            System.arraycopy(suffix, 0, map, prefixLen, suffixLen);
        }

        // SGLang-style split: cached = matched prefix, new = still-to-prefill.
        int newTokens = Math.max(0, promptLen - prefixLen);
        prefixPromptTokens.addAndGet(promptLen);
        prefixMatchTokens.addAndGet(prefixLen);
        double hitRate = promptLen > 0 ? 100.0 * prefixLen / promptLen : 0.0;
        long cumMatch = prefixMatchTokens.get();
        long cumPrompt = prefixPromptTokens.get();
        long cumNew = cumPrompt - cumMatch;
        double cumHit = cumPrompt > 0 ? 100.0 * cumMatch / cumPrompt : 0.0;
        logger.info(
                "KV prefix hit: #cached-token: {}, #new-token: {}, hitRate={} | cumulative #cached-token: {}, #new-token: {}, hitRate={}",
                prefixLen, newTokens, String.format("%.1f%%", hitRate),
                cumMatch, cumNew, String.format("%.1f%%", cumHit));

        return new RequestBinding(map, alignedCapacity, prefixLen, suffix, locked);
    }

    private void insertAndFreePrivate(RequestBinding binding, int[] sequenceTokens) {
        int aligned = (sequenceTokens.length / pageSize) * pageSize;
        if (aligned > 0) {
            if (aligned > binding.capacity()) {
                aligned = (binding.capacity() / pageSize) * pageSize;
            }
            int[] tokens = Arrays.copyOf(sequenceTokens, aligned);
            long[] slots = Arrays.copyOf(binding.slots(), aligned);
            try (Tensor kvIndices = Tensor.of(slots)) {
                InsertResult inserted = radix.insert(tokens, kvIndices);
                int keepFrom = inserted.prefixLen();
                freePrivateOutside(binding.matchedPrefixLen(), binding.privateSlots(),
                        keepFrom, aligned);
                prefixInsertTokens.addAndGet(Math.max(0, aligned - keepFrom));
            }
        } else {
            freePrivateOutside(binding.matchedPrefixLen(), binding.privateSlots(),
                    Integer.MAX_VALUE, 0);
        }
    }

    /**
     * Frees private pages and/or unlocks a multi-request binding.
     *
     * @param freePrivate {@code true} to return private slots to the free list
     *                    (Instant Eviction); {@code false} after radix insert
     *                    already freed unretained private pages.
     */
    private void freeBindingResources(RequestBinding binding, boolean freePrivate) {
        if (freePrivate && binding.privateSlots() != null && binding.privateSlots().length > 0) {
            free(binding.privateSlots());
        }
        if (binding.lockedNode() != null) {
            radix.decLockRef(binding.lockedNode());
        }
    }

    private void clearActivationIfPresent(int requestId) {
        if (activeRequestIds == null) {
            return;
        }
        for (int id : activeRequestIds) {
            if (id == requestId) {
                requestSlots = null;
                requestCapacity = 0;
                activeRequestIds = null;
                return;
            }
        }
    }

    /** Clears multi-request map and exclusive legacy binding. */
    private void releaseAllBindings() {
        for (RequestBinding binding : bindings.values()) {
            freeBindingResources(binding, true);
        }
        bindings.clear();
        activeRequestIds = null;
        releaseLegacyBinding();
    }

    /**
     * Drops the exclusive legacy binding: frees all {@link #privateSlots} and
     * unlocks without radix insert. Tree-owned matched slots are never freed.
     */
    private void releaseLegacyBinding() {
        if (requestSlots == null && privateSlots == null && lockedNode == null) {
            return;
        }
        if (privateSlots != null && privateSlots.length > 0) {
            free(privateSlots);
        }
        unlockMatched();
        requestSlots = null;
        requestCapacity = 0;
        matchedPrefixLen = 0;
        privateSlots = null;
    }

    private void unlockMatched() {
        if (lockedNode != null) {
            radix.decLockRef(lockedNode);
            lockedNode = null;
        }
    }

    private int pageAlignUp(int tokens) {
        int pages = (tokens + pageSize - 1) / pageSize;
        return pages * pageSize;
    }

    /**
     * Evicts radix entries until at least {@code slotsNeeded} free slots exist,
     * or no further eviction is possible.
     */
    private void tryEvictFor(long slotsNeeded) {
        if (slotsNeeded <= 0) {
            return;
        }
        long pagesNeeded = (slotsNeeded + pageSize - 1) / pageSize;
        if (freePages.size() >= pagesNeeded) {
            return;
        }
        int tokensNeeded = (int) Math.min(Integer.MAX_VALUE,
                (pagesNeeded - freePages.size()) * (long) pageSize);
        int evicted = radix.evict(tokensNeeded, value -> {
            long[] slots = value.longArray();
            prefixEvictTokens.addAndGet(slots.length);
            free(slots);
            value.close();
        });
        if (evicted > 0) {
            logger.debug("KV radix evicted {} tokens", evicted);
        }
    }

    /**
     * Frees private slots whose request position is not in
     * {@code [retainStart, retainEnd)}.
     */
    private void freePrivateOutside(int matchedPrefix, long[] privateSlotsArr,
                                    int retainStart, int retainEnd) {
        if (privateSlotsArr == null || privateSlotsArr.length == 0) {
            return;
        }
        // privateSlots maps to request positions [matchedPrefix, capacity)
        Set<Integer> pagesToFree = new HashSet<>();
        for (int i = 0; i < privateSlotsArr.length; i++) {
            int pos = matchedPrefix + i;
            if (pos < retainStart || pos >= retainEnd) {
                pagesToFree.add((int) (privateSlotsArr[i] / pageSize));
            }
        }
        for (int page : pagesToFree) {
            freePages.addLast(page);
        }
    }

    /**
     * Ensures at least {@code pagesNeeded} pages are on the free list, evicting
     * from the radix tree when necessary.
     */
    private void ensureFreePages(int pagesNeeded) {
        if (freePages.size() >= pagesNeeded) {
            return;
        }
        int tokensNeeded = (pagesNeeded - freePages.size()) * pageSize;
        int evicted = radix.evict(tokensNeeded, value -> {
            long[] slots = value.longArray();
            prefixEvictTokens.addAndGet(slots.length);
            free(slots);
            value.close();
        });
        if (evicted > 0) {
            logger.debug("KV radix evicted {} tokens", evicted);
        }
        if (freePages.size() < pagesNeeded) {
            throw new IllegalStateException(String.format(
                    "KV cache OOM: need %d pages, have %d free", pagesNeeded, freePages.size()));
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
