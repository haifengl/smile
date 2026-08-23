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

import java.util.Optional;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * KV-cache storage settings for the chat inference engine.
 * Properties are prefixed with {@code smile.chat.kv-cache}.
 *
 * <p>Pool size is fixed at load time from
 * {@link ChatServiceConfig#memFractionStatic()}; requests never allocate more
 * KV than free pages in that static pool. Exhaustion truncates generation
 * with partial results ({@code finish_reason=length}).
 *
 * @author Haifeng Li
 */
@ConfigMapping(prefix = "smile.chat.kv-cache")
public interface KvCacheConfig {
    /**
     * Element dtype for key/value activations in the shared KV cache pool
     * (e.g. {@code bfloat16}, {@code float16}, {@code fp8_e4m3}, {@code fp8_e5m2}).
     *
     * <p>When unset, the engine uses {@code torch_dtype} from the model's
     * {@code config.json}, falling back to the CUDA compute dtype
     * ({@code bfloat16} when supported, otherwise {@code float16}).
     */
    Optional<String> dtype();

    /**
     * Tokens per radix / KV pool page (SGLang-style page granularity).
     * Matching and insert round down to multiples of this size. Defaults to
     * {@link smile.llm.cache.KvCachePool#DEFAULT_PAGE_SIZE} ({@code 16}).
     */
    @WithDefault("16")
    int pageSize();

    /**
     * When {@code true}, batch-1 generate matches prompts against the radix KV
     * tree and skips recomputing cached prefixes (SGLang-style). Defaults to
     * {@code true}. Intended mainly for debugging when set to {@code false}.
     *
     * <p>Hybrid Qwen also requires {@link #hybridPrefixReplay()} so DeltaNet
     * state is restored on a hit; otherwise prefix reuse is forced off.
     */
    @WithDefault("true")
    boolean prefixReuse();

    /**
     * When {@code true}, hybrid Qwen may use radix KV prefix hits and rebuilds
     * DeltaNet state by replaying the matched prefix ({@code warmPrefix}).
     * Defaults to {@code true}. Set {@code false} to force-disable hybrid
     * prefix reuse (legacy safe mode).
     */
    @WithDefault("true")
    boolean hybridPrefixReplay();
}
