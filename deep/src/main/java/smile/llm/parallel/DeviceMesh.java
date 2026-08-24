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

import smile.deep.tensor.Device;

/**
 * Maps logical mesh coordinates to CUDA devices (phase 1) or process ranks
 * (phase 2 multi-node).
 *
 * @author Haifeng Li
 */
public final class DeviceMesh {
    private final ParallelConfig config;

    /**
     * Creates a device mesh for the given parallel configuration.
     *
     * @param config process-mesh configuration.
     */
    public DeviceMesh(ParallelConfig config) {
        this.config = config;
    }

    /**
     * Returns the underlying parallel configuration.
     * @return mesh config.
     */
    public ParallelConfig config() {
        return config;
    }

    /**
     * Device for a TP rank within the local (pp=0) stage.
     *
     * @param tpRank tensor-parallel rank.
     * @return CUDA device for that rank.
     */
    public Device device(int tpRank) {
        return Device.CUDA(config.devices()[tpRank]);
    }

    /**
     * Device for this parallel state.
     *
     * @param state per-rank parallel state.
     * @return CUDA device for {@code state}.
     */
    public Device device(ParallelState state) {
        return Device.CUDA(state.deviceIndex());
    }
}
