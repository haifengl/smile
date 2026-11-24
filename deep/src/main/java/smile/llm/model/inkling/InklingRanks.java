/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.model.inkling;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import smile.deep.tensor.Device;
import smile.deep.tensor.Tensor;
import smile.llm.parallel.ParallelConfig;
import smile.llm.parallel.TensorParallelGroup;

/**
 * An Inkling-2 model split over several GPUs of one process (reference path, eager): one {@link InklingModel} per rank,
 * each on its own single-thread executor, run in lockstep (the ranks meet in the NCCL all-reduces after attention and
 * after the MoE). Daemon threads, so that a failing rank cannot keep the JVM alive.
 *
 * @author Haifeng Li
 */
public final class InklingRanks implements AutoCloseable {
    private final TensorParallelGroup group;
    private final InklingModel[] models;
    private final ExecutorService[] workers;

    private InklingRanks(TensorParallelGroup group, InklingModel[] models, ExecutorService[] workers) {
        this.group = group;
        this.models = models;
        this.workers = workers;
    }

    /**
     * Loads every rank's share concurrently.
     *
     * @param dir     checkpoint directory.
     * @param args    hyperparameters (possibly a slice).
     * @param devices CUDA device index of each rank.
     * @return the ranks.
     * @throws IOException if a rank fails to load.
     */
    public static InklingRanks load(String dir, InklingModelArgs args, byte... devices) throws IOException {
        int n = devices.length;
        TensorParallelGroup group = new TensorParallelGroup(ParallelConfig.tensorParallel(devices));
        ExecutorService[] workers = new ExecutorService[n];
        for (int r = 0; r < n; r++) {
            int rank = r;
            byte device = devices[r];
            workers[r] = Executors.newSingleThreadExecutor(runnable -> {
                // the fused kernels launch on the thread's current device
                Thread t = new Thread(() -> {
                    smile.torch.InklingNative.setDevice(device);
                    runnable.run();
                }, "inkling-rank-" + rank);
                t.setDaemon(true);
                return t;
            });
        }
        if (n > 1 && !Boolean.getBoolean("smile.inkling.nccl.only")) {
            // one-shot peer-to-peer all-reduce for the small float32 messages of a decode step (up to 1 MB staging)
            System.setProperty("smile.allreduce.custom.max.kb", System.getProperty("smile.allreduce.custom.max.kb", "1024"));
            group.enableCustomAllReduce();
        }
        InklingRanks ranks = new InklingRanks(group, new InklingModel[n], workers);
        try {
            ranks.onRanks(r -> {
                try {
                    ranks.models[r] = InklingModel.load(dir, args, Device.CUDA(devices[r]), new InklingShard(r, n, group));
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
                return null;
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException io) {
                throw io;
            }
            throw e;
        }
        return ranks;
    }

    /** The hyperparameters. */
    public InklingModelArgs args() {
        return models[0].args();
    }

    /** The model of one rank (use it on that rank's thread, see {@link #onRanks}). */
    InklingModel model(int rank) {
        return models[rank];
    }

    /** Number of ranks. */
    public int size() {
        return models.length;
    }

    /**
     * Runs {@code task} on every rank's thread concurrently and waits for all; a failure on any rank is rethrown.
     *
     * @return the per-rank results, in rank order.
     */
    <T> List<T> onRanks(IntFunction<T> task) {
        List<Future<T>> futures = new ArrayList<>();
        for (int r = 0; r < workers.length; r++) {
            int rank = r;
            futures.add(workers[r].submit(() -> task.apply(rank)));
        }
        List<T> out = new ArrayList<>();
        RuntimeException failure = null;
        for (Future<T> f : futures) {
            try {
                out.add(f.get());
            } catch (ExecutionException e) {
                failure = e.getCause() instanceof RuntimeException re ? re : new RuntimeException(e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        if (failure != null) {
            throw failure;
        }
        return out;
    }

    /** Runs {@code task} on one rank's thread. */
    <T> T onRank(int rank, java.util.function.Supplier<T> task) {
        try {
            return workers[rank].submit(task::get).get();
        } catch (ExecutionException e) {
            throw e.getCause() instanceof RuntimeException re ? re : new RuntimeException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates the sequence state on every rank.
     *
     * @param capacity maximum tokens.
     * @return one state per rank.
     */
    public InklingState[] newStates(int capacity) {
        return onRanks(r -> models[r].newState(capacity)).toArray(new InklingState[0]);
    }

    /**
     * The sharded counterpart of {@link InklingModel#forward(InklingState, long[], int[], Consumer)}: every rank runs the
     * chunk on its own state; the logits (identical on all ranks) of rank 0 are returned.
     *
     * @param st    the per-rank states (from {@link #newStates}).
     * @param trace receives the output of every layer application from rank 0, or {@code null}.
     * @return float32 logits {@code [rows, unpadded vocab]} on rank 0's device, or {@code null} when no rows are wanted.
     */
    public Tensor forward(InklingState[] st, long[] tokens, int[] rows, Consumer<Tensor> trace) {
        return forward(st, tokens, rows, trace, null);
    }

    /**
     * As {@link #forward(InklingState[], long[], int[], Consumer)}, also delivering the draft's target features to a
     * per-rank sink (bf16 {@code [T, taps, D]}, valid during the call only).
     *
     * @param tapSinks the sink of each rank, or {@code null}.
     */
    Tensor forward(InklingState[] st, long[] tokens, int[] rows, Consumer<Tensor> trace, IntFunction<Consumer<Tensor>> tapSinks) {
        return forward(st, tokens, rows, trace, tapSinks, null, null);
    }

    /**
     * As above, with image embeddings replacing the embeddings of some of the tokens.
     *
     * @param imageRows per rank, bf16 {@code [K, D]} embeddings (valid during the call), or {@code null}.
     * @param imagePos  the K positions within {@code tokens} they replace.
     */
    Tensor forward(InklingState[] st, long[] tokens, int[] rows, Consumer<Tensor> trace, IntFunction<Consumer<Tensor>> tapSinks,
                   IntFunction<Tensor> imageRows, long[] imagePos) {
        List<Tensor> out = onRanks(r -> {
            try (var guard = Tensor.noGradGuard()) {
                Consumer<Tensor> sink = tapSinks == null ? null : tapSinks.apply(r);
                Tensor img = imageRows == null ? null : imageRows.apply(r);
                return models[r].forward(st[r], tokens, rows, r == 0 ? trace : null, img, imagePos, sink);
            }
        });
        for (int r = 1; r < out.size(); r++) {
            if (out.get(r) != null) {
                out.get(r).close();
            }
        }
        return out.get(0);
    }

    @Override
    public void close() {
        for (ExecutorService w : workers) {
            w.shutdownNow();
        }
        group.close();
    }
}
