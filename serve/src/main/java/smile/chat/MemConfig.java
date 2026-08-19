/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Serve is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.chat;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * GPU memory budgeting for the inference engine.
 * Properties are prefixed with {@code smile.mem}.
 *
 * @author Haifeng Li
 */
@ConfigMapping(prefix = "smile.mem")
public interface MemConfig {
    /**
     * Upper bound on the fraction of free GPU memory (after model weights and
     * DeltaNet state are loaded) that may be used for the KV cache pool.
     * Defaults to {@code 0.85} (same convention as vLLM / SGLang
     * {@code --mem-fraction-static}).
     *
     * <p>The pool is also capped at {@code maxBatchSize × maxSeqLen} slots so
     * unused context capacity is not allocated.
     */
    @WithName("fraction.static")
    @WithDefault("0.85")
    double fractionStatic();
}
