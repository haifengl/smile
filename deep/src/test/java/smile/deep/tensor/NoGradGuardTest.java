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
package smile.deep.tensor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link NoGradGuard} behavior.
 *
 * @author Haifeng Li
 */
public class NoGradGuardTest {

    @Test
    public void testGivenRequireGradProbeWhenNoGuardThenOutputRequiresGrad() {
        try (var probe = Tensor.ones(1).setRequireGrad(true);
             var output = probe.add(1.0)) {
            assertTrue(output.getRequireGrad(), "Autograd should be enabled without NoGradGuard");
        }
    }

    @Test
    public void testGivenRequireGradProbeWhenGuardScopeThenGradDisabledAndRestoredAfterClose() {
        try (var guard = Tensor.noGradGuard();
             var guardedProbe = Tensor.ones(1).setRequireGrad(true);
             var guardedOutput = guardedProbe.add(1.0)) {
            assertFalse(guardedOutput.getRequireGrad(), "NoGradGuard should disable grad tracking in scope");
        }

        try (var probe = Tensor.ones(1).setRequireGrad(true);
             var output = probe.add(1.0)) {
            assertTrue(output.getRequireGrad(), "Autograd should be restored after NoGradGuard closes");
        }
    }
}

