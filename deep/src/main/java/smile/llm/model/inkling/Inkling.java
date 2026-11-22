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
package smile.llm.model.inkling;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.deep.tensor.Device;
import smile.deep.tensor.Tensor;
import smile.llm.ChatCompletion;
import smile.llm.ChatOptions;
import smile.llm.FinishReason;
import smile.llm.GenerationListener;
import smile.llm.LanguageModel;
import smile.llm.Message;
import smile.llm.cache.KvCachePool;
import smile.llm.engine.ModelExecutor;
import smile.llm.engine.Sampling;
import smile.llm.template.InklingChatTemplate;

import static smile.llm.model.inkling.Ix.cat;
import static smile.llm.model.inkling.Ix.stepScoped;

/**
 * Inkling-2-Mini behind the continuous-batching engine: a {@link LanguageModel} for chat requests and a
 * {@link ModelExecutor} for {@link smile.llm.engine.InferenceEngine}. Text only for now.
 *
 * <p>Every request owns an {@link InklingState} (caches of the 48 layer applications, sliding-window caches limited to
 * the window plus one chunk). All tensor work runs on one worker thread, which also loads the weights. A decode step
 * runs the requests one after another (each as a one-token forward); batching them is a kernel-level change.
 *
 * @author Haifeng Li
 */
public final class Inkling implements LanguageModel, ModelExecutor, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(Inkling.class);

    private final String name;
    private final InklingModelArgs args;
    private final InklingTokenizer tokenizer;
    private final InklingChatTemplate template = new InklingChatTemplate();
    private final ExecutorService worker;
    private final Device device;
    private final Map<Integer, InklingState> sessions = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);
    private InklingModel model;
    private final InklingPrefixCache prefixCache;
    private final Map<Integer, InklingPrefixCache.Entry> prefixMatches = new ConcurrentHashMap<>();
    private volatile boolean prefixReuse = true;
    /** Prefill takes a snapshot at every multiple of this many tokens (when the cache has room). */
    private static final int SNAPSHOT_EVERY = Integer.getInteger("smile.inkling.prefix.every", 1024);
    /** Shorter prefixes are not worth a snapshot. */
    private static final int SNAPSHOT_MIN = Integer.getInteger("smile.inkling.prefix.min", 256);

    private Inkling(String name, InklingModelArgs args, InklingTokenizer tokenizer, Path dir, Device device) {
        this.name = name;
        this.args = args;
        this.tokenizer = tokenizer;
        this.device = device;
        // a failing load must not leave the JVM waiting on this thread
        long cacheMb = Long.getLong("smile.inkling.prefix.cache.mb", 2048L);
        this.prefixCache = cacheMb > 0 ? new InklingPrefixCache(cacheMb << 20) : null;
        this.worker = Executors.newSingleThreadExecutor(runnable -> {
            Thread t = new Thread(runnable, "inkling-worker");
            t.setDaemon(true);
            return t;
        });
        onWorker(() -> {
            this.model = InklingModel.load(dir.toString(), args, device);
            return null;
        });
    }

    /**
     * Loads Inkling-2-Mini.
     *
     * @param checkpointDir the checkpoint directory.
     * @param layers        a slice of the first {@code layers} layers for tests ({@code 0}: all).
     * @param maxSeqLen     maximum sequence length; {@code <= 0} uses the model's.
     * @param device        the CUDA device index.
     * @return the model.
     * @throws IOException if the checkpoint cannot be read.
     */
    public static Inkling build(String checkpointDir, int layers, int maxSeqLen, byte device) throws IOException {
        InklingModelArgs args = InklingModelArgs.fromCheckpoint(checkpointDir, maxSeqLen);
        if (layers > 0) {
            args = args.withLayers(layers, layers >= args.coreEnd() ? args.numPasses() : 1);
        }
        Path dir = Path.of(checkpointDir);
        return new Inkling(dir.getFileName().toString(), args, InklingTokenizer.of(checkpointDir), dir, Device.CUDA(device));
    }

    private <T> T onWorker(Callable<T> task) {
        try {
            return worker.submit(task).get();
        } catch (ExecutionException e) {
            Throwable c = e.getCause();
            if (c instanceof RuntimeException r) {
                throw r;
            }
            throw new IllegalStateException(c);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private InklingState session(int requestId) {
        InklingState s = sessions.get(requestId);
        if (s == null) {
            throw new IllegalArgumentException("unknown request " + requestId);
        }
        return s;
    }

    /** Hyperparameters. */
    public InklingModelArgs args() {
        return args;
    }

    // ------------------------------------------------------------------ LanguageModel

    @Override
    public String family() {
        return "inkling";
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public int maxSeqLen() {
        return args.maxSeqLen();
    }

    @Override
    public int[] encodeChat(Message... dialog) {
        return encodeChat(dialog, null);
    }

    @Override
    public int[] encodeChat(Message[] dialog, ChatOptions options) {
        if (dialog == null || dialog.length == 0) {
            throw new IllegalArgumentException("No messages provided.");
        }
        for (Message m : dialog) {
            if (m.hasMedia()) {
                throw new IllegalArgumentException("Inkling-2-Mini images are not supported yet (text only)");
            }
        }
        return tokenizer.encodeWithSpecials(template.encode(Arrays.asList(dialog), options));
    }

    @Override
    public ChatCompletion generate(int[] prompt, int maxGenLen, double temperature, double topp, boolean logprobs,
                                   long seed, GenerationListener listener, BooleanSupplier cancelRequested) {
        if (prompt == null || prompt.length == 0) {
            throw new IllegalArgumentException("prompt must not be empty");
        }
        if (logprobs) {
            throw new UnsupportedOperationException("logprobs are not supported yet");
        }
        for (int token : prompt) {
            if (token < 0 || token >= args.unpaddedVocabSize()) {
                throw new IllegalArgumentException("Prompt token id " + token + " out of range for vocab_size "
                        + args.unpaddedVocabSize());
            }
        }
        int promptLen = prompt.length;
        if (promptLen > args.maxSeqLen()) {
            throw new IllegalArgumentException("The prompt length is greater than max_seq_len");
        }
        int maxGen = Math.max(0, Math.min(maxGenLen, args.maxSeqLen() - promptLen));
        int id = bind(prompt, promptLen + maxGen);
        List<Integer> out = new ArrayList<>();
        boolean stopped = false;
        try (var guard = Tensor.noGradGuard()) {
            if (listener != null) {
                listener.onInputTokens(promptLen);
                listener.onCachedInputTokens(0);
            }
            Set<Integer> stops = new HashSet<>();
            for (int s : stopTokens()) {
                stops.add(s);
            }
            if (maxGen > 0) {
                int next = sample(prefill(id, prompt, 0), temperature, topp);
                int pos = promptLen;
                while (true) {
                    if (cancelRequested != null && cancelRequested.getAsBoolean()) {
                        throw new java.util.concurrent.CancellationException("generation cancelled");
                    }
                    if (stops.contains(next)) {
                        stopped = true;
                        break;
                    }
                    out.add(next);
                    if (out.size() >= maxGen) {
                        break;
                    }
                    next = sample(decodeStep(new int[]{id}, new int[]{next}, new int[]{pos++}), temperature, topp);
                }
            }
            int[] completion = out.stream().mapToInt(Integer::intValue).toArray();
            String text = safeDecode(completion);
            if (listener != null) {
                listener.onGeneratedTokens(completion.length);
                if (completion.length > 0) {
                    listener.onText(text);
                }
            }
            return new ChatCompletion(name, text, prompt, completion,
                    stopped ? FinishReason.stop : FinishReason.length, null);
        } finally {
            evict(id);
        }
    }

    @Override
    public ChatCompletion chat(Message[] dialog, int maxGenLen, double temperature, double topp, boolean logprobs,
                               long seed, GenerationListener listener, BooleanSupplier cancelRequested) {
        return generate(encodeChat(dialog), maxGenLen, temperature, topp, logprobs, seed, listener, cancelRequested);
    }

    private static int sample(Tensor logits, double temperature, double topp) {
        try (logits) {
            return Sampling.sampleTokenIds(logits, temperature, topp)[0];
        }
    }

    private String safeDecode(int[] tokens) {
        try {
            return tokenizer.tryDecode(tokens, true);
        } catch (Exception e) {
            return tokenizer.decode(tokens);
        }
    }

    // ------------------------------------------------------------------ ModelExecutor

    @Override
    public LanguageModel model() {
        return this;
    }

    @Override
    public KvCachePool kvCachePool() {
        return null;
    }

    @Override
    public int padToken() {
        return tokenizer.pad();
    }

    @Override
    public int[] stopTokens() {
        return tokenizer.stopTokens();
    }

    @Override
    public String decode(int[] tokens) {
        return tokenizer.decode(tokens);
    }

    @Override
    public String tryDecode(int[] tokens, boolean skipSpecial) throws java.nio.charset.CharacterCodingException {
        return tokenizer.tryDecode(tokens, skipSpecial);
    }

    @Override
    public int bind(int[] prompt, int totalCapacity) {
        if (totalCapacity > args.maxSeqLen()) {
            throw new IllegalArgumentException("capacity " + totalCapacity + " exceeds max_seq_len " + args.maxSeqLen());
        }
        InklingState state = onWorker(() -> model.newState(Math.max(1, totalCapacity)));
        int id = nextId.getAndIncrement();
        sessions.put(id, state);
        if (prefixCache != null && prefixReuse && prompt != null) {
            // the last prompt token is always processed (its logits are sampled)
            InklingPrefixCache.Entry hit;
            synchronized (prefixCache) {
                hit = prefixCache.acquire(prompt, prompt.length - 1);
            }
            if (hit != null) {
                prefixMatches.put(id, hit);
            }
        }
        return id;
    }

    /**
     * Turns prefix reuse on or off.
     *
     * @param enabled whether requests restore stored snapshots and finished requests store theirs.
     */
    public void setPrefixReuseEnabled(boolean enabled) {
        this.prefixReuse = enabled;
    }

    /**
     * Prefix-cache statistics.
     *
     * @return a one-line summary, or an empty string when the cache is off.
     */
    public String prefixCacheStats() {
        if (prefixCache == null) {
            return "";
        }
        synchronized (prefixCache) {
            return prefixCache.stats();
        }
    }

    @Override
    public int prefixLen(int requestId) {
        InklingPrefixCache.Entry hit = prefixMatches.get(requestId);
        return hit == null ? 0 : hit.length();
    }

    /** Loads the matched snapshot into the request's (empty) state; the engine then prefills from its length. */
    @Override
    public void warmPrefix(int requestId, int[] prompt, int prefixLen) {
        InklingPrefixCache.Entry hit = prefixMatches.remove(requestId);
        if (hit == null) {
            return;
        }
        try {
            if (prefixLen != hit.length()) {
                throw new IllegalStateException("request " + requestId + " resumes at " + prefixLen
                        + " but its stored prefix has " + hit.length() + " tokens");
            }
            InklingState st = session(requestId);
            onWorker(() -> {
                st.restore(hit.snapshot);
                return null;
            });
        } finally {
            synchronized (prefixCache) {
                prefixCache.release(hit);
            }
        }
    }

    @Override
    public Tensor prefill(int requestId, int[] prompt, int prefixLen) {
        return prefillChunk(requestId, prompt, prefixLen, prompt.length);
    }

    @Override
    public Tensor prefillChunk(int requestId, int[] prompt, int from, int to) {
        InklingState st = session(requestId);
        if (st.length != from) {
            throw new IllegalStateException("request " + requestId + " is at position " + st.length + ", not " + from);
        }
        boolean last = to == prompt.length;
        return onWorker(() -> {
            try (var guard = Tensor.noGradGuard()) {
                int limit = st.maxChunk(args);
                int pos = from;
                Tensor logits = null;
                while (pos < to) {
                    int end = Math.min(to, pos + limit);
                    // stop at the next position worth a snapshot: the state there is a prefix of later prompts
                    if (prefixCache != null && prefixReuse) {
                        int cut = (pos / SNAPSHOT_EVERY + 1) * SNAPSHOT_EVERY;
                        if (cut < prompt.length && cut < end) {
                            end = cut;
                        }
                    }
                    long[] ids = new long[end - pos];
                    for (int i = 0; i < ids.length; i++) {
                        ids[i] = prompt[pos + i];
                    }
                    boolean wantLogits = last && end == to;
                    Tensor t = model.forward(st, ids, wantLogits ? null : new int[0], null);
                    if (wantLogits) {
                        logits = t;
                    }
                    pos = end;
                    if (prefixCache != null && prefixReuse && pos % SNAPSHOT_EVERY == 0 && pos < prompt.length) {
                        store(st, Arrays.copyOf(prompt, pos));
                    }
                }
                return logits;
            }
        });
    }

    /** Stores a snapshot of {@code st}, which has processed exactly {@code tokens}; on the worker thread. */
    private void store(InklingState st, int[] tokens) {
        if (tokens.length < SNAPSHOT_MIN || st.length != tokens.length) {
            return;
        }
        long bytes = InklingState.snapshotBytes(args, tokens.length);
        synchronized (prefixCache) {
            if (!prefixCache.fits(bytes) || prefixCache.has(tokens, tokens.length)) {
                return;
            }
        }
        InklingState.Snapshot snap = st.snapshot(args);
        synchronized (prefixCache) {
            prefixCache.put(tokens, snap);
        }
    }

    @Override
    public Tensor decodeStep(int[] requestIds, int[] lastTokens, int[] positions) {
        int n = requestIds.length;
        InklingState[] states = new InklingState[n];
        for (int i = 0; i < n; i++) {
            states[i] = session(requestIds[i]);
            if (states[i].length != positions[i]) {
                throw new IllegalStateException("request " + requestIds[i] + " is at position " + states[i].length
                        + ", not " + positions[i]);
            }
        }
        return onWorker(() -> {
            try (var guard = Tensor.noGradGuard()) {
                return stepScoped(() -> {
                    Tensor[] rows = new Tensor[n];
                    for (int i = 0; i < n; i++) {
                        rows[i] = model.forward(states[i], new long[]{lastTokens[i]}, null, null);
                    }
                    return n == 1 ? rows[0] : cat(0, rows);
                });
            }
        });
    }

    @Override
    public void finish(int requestId, int[] sequenceTokens) {
        InklingState st = sessions.get(requestId);
        if (st != null && prefixCache != null && prefixReuse && sequenceTokens != null
                && st.length >= SNAPSHOT_MIN && st.length <= sequenceTokens.length) {
            // the whole conversation so far is the likely prefix of the next turn
            int[] tokens = Arrays.copyOf(sequenceTokens, st.length);
            onWorker(() -> {
                try (var guard = Tensor.noGradGuard()) {
                    store(st, tokens);
                }
                return null;
            });
        }
        evict(requestId);
    }

    @Override
    public void evict(int requestId) {
        InklingPrefixCache.Entry hit = prefixMatches.remove(requestId);
        if (hit != null) {
            synchronized (prefixCache) {
                prefixCache.release(hit);
            }
        }
        InklingState s = sessions.remove(requestId);
        if (s != null) {
            onWorker(() -> {
                s.close();
                return null;
            });
        }
    }

    /**
     * Tokens processed so far for a request.
     *
     * @param requestId the request id from {@link #bind}.
     * @return the position of its next token.
     */
    public int positionOf(int requestId) {
        return session(requestId).length;
    }

    @Override
    public void close() {
        for (int id : new ArrayList<>(sessions.keySet())) {
            evict(id);
        }
        if (prefixCache != null) {
            onWorker(() -> {
                synchronized (prefixCache) {
                    prefixCache.clear();
                }
                return null;
            });
        }
        worker.shutdown();
    }
}
