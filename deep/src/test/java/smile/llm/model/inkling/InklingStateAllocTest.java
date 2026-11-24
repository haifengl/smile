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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;

/**
 * Time to create and free the per-request state of a model (one rank of a sharded Small), by capacity.
 *
 * <p>Opt-in, GPU: {@code INKLING_STATE_CKPT} (a checkpoint directory with a {@code config.json}).
 *
 * @author Haifeng Li
 */
public class InklingStateAllocTest {
    @Test
    public void testGivenCapacitiesWhenStatesAreCreatedThenTimesAreReported() throws IOException {
        String ckpt = System.getenv("INKLING_STATE_CKPT");
        Assumptions.assumeTrue(ckpt != null, "set INKLING_STATE_CKPT");
        InklingModelArgs args = InklingModelArgs.fromCheckpoint(ckpt, 0);
        Device dev = Device.CUDA((byte) 0);
        int tp = args.numKvHeads() >= 8 && args.dim() > 2304 ? 8 : 1;
        for (int capacity : new int[]{16, 16, 1000, 3067, 3067, 30000, 30000}) {
            if (System.getenv("INKLING_STATE_EMPTY") != null) {
                dev.emptyCache();
            }
            long t0 = System.nanoTime();
            InklingState st = new InklingState(args, capacity, dev, tp);
            long t1 = System.nanoTime();
            st.table();
            st.close();
            long t2 = System.nanoTime();
            System.out.printf("capacity %6d: create %.1f ms, close %.1f ms%n", capacity, (t1 - t0) / 1e6, (t2 - t1) / 1e6);
        }
    }
}
