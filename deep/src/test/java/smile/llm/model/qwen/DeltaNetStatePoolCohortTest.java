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
package smile.llm.model.qwen;

import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.Index;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Cohort isolation of {@link DeltaNetStatePool}: activating one set of requests packs their state
 * into working rows {@code [0, B)}, which are the same storage as other requests' home rows. A
 * forward over one cohort must never disturb the state of live requests outside it, and
 * alternating disjoint cohorts (e.g. a speculating group and a plain-decoding group in the same
 * engine tick, or a batch plus a straggler) must leave every request's state exactly as if it had
 * run alone.
 *
 * @author Haifeng Li
 */
public class DeltaNetStatePoolCohortTest {

    private static DeltaNetStatePool pool() {
        // 2 linear layers, 2 value heads, key/value dim 4, conv 6 channels, kernel 3, up to 8 requests.
        return new DeltaNetStatePool(2, 2, 4, 4, 6, 3, 8, Device.CPU(), ScalarType.Float);
    }

    /** Sets the whole home state of {@code requestId} (all layers, recurrent + conv) to {@code value}. */
    private static void fill(DeltaNetStatePool p, int requestId, float value) {
        p.activateStep(requestId);
        for (int l = 0; l < 2; l++) {
            p.activeRecurrent(l).fill_(value);
            if (p.activeConv(l) != null) {
                p.activeConv(l).fill_(value);
            }
        }
        p.scatterActive();
    }

    /** Reads layer-0 recurrent state of {@code requestId} through a fresh single-request activation. */
    private static float readRecurrent(DeltaNetStatePool p, int requestId, int layer) {
        p.activateStep(requestId);
        try (Tensor t = p.activeRecurrent(layer).reshape(-1); Tensor first = t.get(Index.of(0))) {
            float v = first.floatValue();
            p.scatterActive();
            return v;
        }
    }

    private static float readConv(DeltaNetStatePool p, int requestId, int layer) {
        p.activateStep(requestId);
        try (Tensor t = p.activeConv(layer).reshape(-1); Tensor first = t.get(Index.of(0))) {
            float v = first.floatValue();
            p.scatterActive();
            return v;
        }
    }

    @Test
    public void testGivenDisjointCohortsWhenAlternatedThenEveryRequestKeepsItsOwnState() {
        try (DeltaNetStatePool p = pool()) {
            int[] ids = {101, 102, 103, 104, 105};
            for (int id : ids) {
                p.bindRequest(id);
            }
            // Distinct, recognisable state per request (the value is its own id).
            for (int id : ids) {
                fill(p, id, id);
            }

            // Cohort A = {104, 105} lives in home rows 3, 4 -> packed into working rows 0, 1, which
            // are the home rows of requests 101 and 102 (not in the cohort).
            p.activateStep(104, 105);
            for (int l = 0; l < 2; l++) {
                p.activeRecurrent(l).add_(1000.0);   // simulate A's forward updating its state
                p.activeConv(l).add_(1000.0);
            }
            p.scatterActive();

            // Cohort B = {101, 102, 103}: homes 0, 1, 2 (identity); must see its original state.
            p.activateStep(101, 102, 103);
            for (int l = 0; l < 2; l++) {
                p.activeRecurrent(l).add_(2000.0);
                p.activeConv(l).add_(2000.0);
            }
            p.scatterActive();

            // Every request: untouched id for non-updated, +1000 for A members, +2000 for B members.
            for (int l = 0; l < 2; l++) {
                assertEquals(101 + 2000, readRecurrent(p, 101, l), 1e-3, "101 recurrent L" + l);
                assertEquals(102 + 2000, readRecurrent(p, 102, l), 1e-3, "102 recurrent L" + l);
                assertEquals(103 + 2000, readRecurrent(p, 103, l), 1e-3, "103 recurrent L" + l);
                assertEquals(104 + 1000, readRecurrent(p, 104, l), 1e-3, "104 recurrent L" + l);
                assertEquals(105 + 1000, readRecurrent(p, 105, l), 1e-3, "105 recurrent L" + l);
                assertEquals(104 + 1000, readConv(p, 104, l), 1e-3, "104 conv L" + l);
                assertEquals(101 + 2000, readConv(p, 101, l), 1e-3, "101 conv L" + l);
            }
        }
    }

    @Test
    public void testGivenCohortInReverseHomeOrderWhenActivatedThenPositionsFollowRequestOrder() {
        try (DeltaNetStatePool p = pool()) {
            for (int id : new int[]{1, 2, 3}) {
                p.bindRequest(id);
                fill(p, id, id * 10);
            }
            // Cohort order differs from home order: position 0 -> request 3 (home 2), 1 -> request 1 (home 0).
            p.activateStep(3, 1);
            try (Tensor r = p.activeRecurrent(0)) {
                assertEquals(30f, r.get(Index.of(0)).reshape(-1).get(Index.of(0)).floatValue(), 1e-4);
                assertEquals(10f, r.get(Index.of(1)).reshape(-1).get(Index.of(0)).floatValue(), 1e-4);
            }
            p.activeRecurrent(0).add_(5.0);
            p.scatterActive();
            assertEquals(35f, readRecurrent(p, 3, 0), 1e-4);
            assertEquals(15f, readRecurrent(p, 1, 0), 1e-4);
            assertEquals(20f, readRecurrent(p, 2, 0), 1e-4, "bystander request 2 must be untouched");
        }
    }
}
