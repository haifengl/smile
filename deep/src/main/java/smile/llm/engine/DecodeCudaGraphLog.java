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
package smile.llm.engine;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * INFO logging for decode CUDA graph bucket lifecycle (once per bucket event).
 *
 * <p>Used to compare on-demand capture hitches vs pre-captured bucket promotion.
 */
public final class DecodeCudaGraphLog {
    private static final Logger logger = LoggerFactory.getLogger(DecodeCudaGraphLog.class);
    private static final Set<Long> REPLAY_LOGGED = ConcurrentHashMap.newKeySet();
    private static final Set<Long> WARMUP_LOGGED = ConcurrentHashMap.newKeySet();
    private static final Set<Long> CAPTURE_LOGGED = ConcurrentHashMap.newKeySet();
    private static final Set<Long> PREFETCH_START_LOGGED = ConcurrentHashMap.newKeySet();
    private static final Set<Long> PREFETCH_READY_LOGGED = ConcurrentHashMap.newKeySet();
    private static final Set<Long> PREFETCH_HIT_LOGGED = ConcurrentHashMap.newKeySet();

    private DecodeCudaGraphLog() {}

    /** Logs the first eager warmup step for a bucket. */
    public static void bucketWarmup(int tpRank, int batch, int numPages, int step, int total) {
        if (!logger.isInfoEnabled()) {
            return;
        }
        long key = bucketKey(batch, numPages);
        if (step == 1 && WARMUP_LOGGED.add(key)) {
            logger.info("tpRank={}: decode CUDA graph bucket warmup batch={} numPages={} "
                    + "({} eager steps before capture)", tpRank, batch, numPages, total);
        }
    }

    /** Logs successful graph capture for a bucket. */
    public static void bucketCapture(int tpRank, int batch, int numPages, long captureMs,
                                     boolean prefetched) {
        if (!logger.isInfoEnabled()) {
            return;
        }
        if (!CAPTURE_LOGGED.add(bucketKey(batch, numPages))) {
            return;
        }
        if (prefetched) {
            logger.info("tpRank={}: decode CUDA graph bucket capture batch={} numPages={} "
                    + "ms={} (prefetch)", tpRank, batch, numPages, captureMs);
        } else {
            logger.info("tpRank={}: decode CUDA graph bucket capture batch={} numPages={} ms={}",
                    tpRank, batch, numPages, captureMs);
        }
    }

    /** Logs the first replay for a bucket in this process. */
    public static void bucketReplay(int tpRank, int batch, int numPages) {
        if (!logger.isInfoEnabled()) {
            return;
        }
        if (REPLAY_LOGGED.add(bucketKey(batch, numPages))) {
            logger.info("tpRank={}: decode CUDA graph bucket replay batch={} numPages={}",
                    tpRank, batch, numPages);
        }
    }

    /** Logs when background prefetch starts for the next page bucket. */
    public static void prefetchStart(int tpRank, int batch, int numPages) {
        if (!logger.isInfoEnabled()) {
            return;
        }
        if (PREFETCH_START_LOGGED.add(bucketKey(batch, numPages))) {
            logger.info("tpRank={}: decode CUDA graph prefetch start batch={} numPages={}",
                    tpRank, batch, numPages);
        }
    }

    /** Logs when a prefetched bucket becomes replay-ready. */
    public static void prefetchReady(int tpRank, int batch, int numPages, long captureMs) {
        if (!logger.isInfoEnabled()) {
            return;
        }
        if (PREFETCH_READY_LOGGED.add(bucketKey(batch, numPages))) {
            logger.info("tpRank={}: decode CUDA graph prefetch ready batch={} numPages={} ms={}",
                    tpRank, batch, numPages, captureMs);
        }
    }

    /** Logs promotion of a prefetched graph at a page boundary (no capture hitch). */
    public static void prefetchHit(int tpRank, int batch, int numPages) {
        if (!logger.isInfoEnabled()) {
            return;
        }
        if (PREFETCH_HIT_LOGGED.add(bucketKey(batch, numPages))) {
            logger.info("tpRank={}: decode CUDA graph prefetch hit batch={} numPages={}",
                    tpRank, batch, numPages);
        }
    }

    private static long bucketKey(int batch, int numPages) {
        return ((long) batch << 32) | (numPages & 0xffffffffL);
    }
}
