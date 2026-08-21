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
package smile.llm.checkpoint;

/**
 * Resolves the safetensors loader thread-pool size.
 *
 * <p>Peak host RAM during load scales roughly as
 * {@code threads × in-flight shard footprint} (each worker holds at most one
 * shard on CPU at a time).
 *
 * @author Haifeng Li
 */
public final class SafeTensorsLoaderThreads {
    /** Default auto cap when {@code configured == 0}. */
    public static final int AUTO_CAP = 8;

    private SafeTensorsLoaderThreads() {
    }

    /**
     * Resolves loader concurrency.
     *
     * <pre>
     * auto     = min({@link #AUTO_CAP}, availableProcessors)
     * threads  = configured &gt; 0 ? configured : auto
     * result   = min(threads, numShards)   // at least 1 when numShards &gt;= 1
     * </pre>
     *
     * @param configured {@code 0} means auto; must not be negative.
     * @param numShards  number of safetensors shard files ({@code >= 0}).
     * @return pool size; {@code 0} when there are no shards.
     */
    public static int resolve(int configured, int numShards) {
        if (configured < 0) {
            throw new IllegalArgumentException(
                    "model-loader-threads must be >= 0 (0 = auto), got " + configured);
        }
        if (numShards < 0) {
            throw new IllegalArgumentException("numShards must be >= 0, got " + numShards);
        }
        if (numShards == 0) {
            return 0;
        }
        int auto = Math.min(AUTO_CAP, Math.max(1, Runtime.getRuntime().availableProcessors()));
        int threads = configured > 0 ? configured : auto;
        return Math.max(1, Math.min(threads, numShards));
    }
}
