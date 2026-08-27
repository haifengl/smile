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
package smile.llm.quant;

import java.lang.foreign.MemorySegment;
import smile.deep.layer.LinearLayer;
import smile.torch.Native;

/**
 * Releases placeholder dense {@link LinearLayer} shells after quantized ops
 * are installed, so empty GPU weight storage does not inflate the KV budget.
 */
public final class DenseLinearRelease {
    private DenseLinearRelease() {}

    /**
     * Unregisters {@code name} from {@code parentModule} (if present) and closes
     * the dense layer so LibTorch can free its parameter storage.
     */
    public static void unregisterAndClose(MemorySegment parentModule, String name, LinearOp op) {
        if (!(op instanceof LinearLayer ll)) {
            return;
        }
        if (parentModule != null && name != null) {
            try {
                Native.unregisterModule(parentModule, name);
            } catch (RuntimeException ignored) {
                // Tests may skip parent registration.
            }
        }
        ll.close();
    }
}
