/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package smile.chat;

import java.util.Optional;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * ONNX Runtime GenAI / Olive fallback settings ({@code smile.chat.oga.*}).
 *
 * @author Haifeng Li
 */
@ConfigMapping(prefix = "smile.chat.oga")
public interface OgaChatConfig {
    /**
     * When {@code false}, skip OGA / Olive fallback (Torch-only chat).
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * Olive {@code --precision} override. Empty or {@code auto} means FP8 when
     * the selected EP supports it, else {@code int4}.
     */
    Optional<String> precision();

    /**
     * Olive output cache root. Empty uses {@code {SMILE_CACHE}/olive}
     * ({@link smile.io.CacheFiles#dir()}).
     */
    @WithName("cache-dir")
    Optional<String> cacheDir();

    /**
     * Olive CLI executable (default {@code olive}).
     */
    @WithName("olive-command")
    @WithDefault("olive")
    String oliveCommand();

    /**
     * Optional Olive {@code --device} override (empty = GenAI cascade).
     */
    Optional<String> device();

    /**
     * Optional Olive {@code --provider} override (empty = GenAI cascade).
     */
    Optional<String> provider();
}
