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
package smile.llm.parallel;

/**
 * Per-rank view of a {@link ParallelConfig} mesh.
 *
 * <p>Phase 1: {@code globalRank == tpRank} with {@code ppRank == 0}. Phase 2
 * multi-node PP will set {@code globalRank = ppRank * tpSize + tpRank}.
 *
 * @author Haifeng Li
 */
public final class ParallelState {
    private static final ThreadLocal<ParallelState> CURRENT = new ThreadLocal<>();

    private final ParallelConfig config;
    private final int tpRank;
    private final int ppRank;
    private final int dpRank;
    private final int globalRank;
    private final byte deviceIndex;

    public ParallelState(ParallelConfig config, int tpRank) {
        this(config, tpRank, 0, 0);
    }

    public ParallelState(ParallelConfig config, int tpRank, int ppRank, int dpRank) {
        if (tpRank < 0 || tpRank >= config.tpSize()) {
            throw new IllegalArgumentException("tpRank out of range: " + tpRank);
        }
        if (ppRank < 0 || ppRank >= config.ppSize()) {
            throw new IllegalArgumentException("ppRank out of range: " + ppRank);
        }
        if (dpRank < 0 || dpRank >= config.dpSize()) {
            throw new IllegalArgumentException("dpRank out of range: " + dpRank);
        }
        this.config = config;
        this.tpRank = tpRank;
        this.ppRank = ppRank;
        this.dpRank = dpRank;
        this.globalRank = ((dpRank * config.ppSize()) + ppRank) * config.tpSize() + tpRank;
        this.deviceIndex = config.devices()[tpRank];
    }

    public ParallelConfig config() {
        return config;
    }

    public int tpSize() {
        return config.tpSize();
    }

    public int tpRank() {
        return tpRank;
    }

    public int ppRank() {
        return ppRank;
    }

    public int dpRank() {
        return dpRank;
    }

    public int globalRank() {
        return globalRank;
    }

    public byte deviceIndex() {
        return deviceIndex;
    }

    public boolean isTpRoot() {
        return tpRank == 0;
    }

    public boolean isFirstStage() {
        return ppRank == 0;
    }

    public boolean isLastStage() {
        return ppRank == config.ppSize() - 1;
    }

    /** Binds this state to the calling thread for collective helpers. */
    public static void setCurrent(ParallelState state) {
        CURRENT.set(state);
    }

    public static ParallelState current() {
        return CURRENT.get();
    }

    public static void clearCurrent() {
        CURRENT.remove();
    }
}
