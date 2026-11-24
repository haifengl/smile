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

import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.deep.tensor.Tensor;

/**
 * A synchronizing section profiler for the reference (prefill) path, enabled with {@code -Dsmile.inkling.prof=true}: each
 * {@link #lap} waits for the device and adds the time since the previous lap to a label. Only rank 0 records; the other ranks
 * run unmodified (they wait for rank 0 in the all-reduces). It slows the forward down: use it to find the shares, not the speed.
 *
 * @author Haifeng Li
 */
final class InklingProf {
    private static final Logger logger = LoggerFactory.getLogger(InklingProf.class);
    static final boolean ON = Boolean.getBoolean("smile.inkling.prof");
    private static final Map<String, Double> TOTAL = new TreeMap<>();
    private static long mark;

    private InklingProf() { }

    /** Set between {@link #begin} and {@link #flush}: laps outside a profiled forward (graph capture!) must not synchronize. */
    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);

    private static boolean recording() {
        return ON && ACTIVE.get() && Thread.currentThread().getName().endsWith("rank-0");
    }

    /** Starts a section: waits for the device and takes the time. */
    static void begin(Tensor sync) {
        if (ON) {
            ACTIVE.set(true);
        }
        if (recording()) {
            wait(sync);
            mark = System.nanoTime();
        }
    }

    /** Ends a section: the time since the last lap or begin is added to {@code label}. */
    static void lap(String label, Tensor sync) {
        if (recording()) {                  // only rank 0 gets here: no lock may be held while waiting for the device
            wait(sync);
            long now = System.nanoTime();
            TOTAL.merge(label, (now - mark) / 1e6, Double::sum);
            mark = now;
        }
    }

    private static void wait(Tensor t) {
        if (t != null) {
            smile.torch.Aten.one("aten::sum", t.dtype() == smile.deep.tensor.ScalarType.Float ? t
                    : t.to(smile.deep.tensor.ScalarType.Float)).doubleValue();
        }
    }

    /** Prints and clears the totals of one forward. */
    static void flush(int tokens) {
        boolean was = recording();
        ACTIVE.set(false);
        if (was && !TOTAL.isEmpty()) {
            double sum = TOTAL.values().stream().mapToDouble(Double::doubleValue).sum();
            StringBuilder sb = new StringBuilder(String.format("prefill profile, %d tokens, %.0f ms in laps:", tokens, sum));
            TOTAL.entrySet().stream().sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                    .forEach(e -> sb.append(String.format("  %s %.0f (%.0f%%)", e.getKey(), e.getValue(), 100 * e.getValue() / sum)));
            logger.info(sb.toString());
            TOTAL.clear();
        }
    }
}
