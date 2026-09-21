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
 * ONNX Runtime GenAI settings ({@code smile.chat.oga.*}).
 *
 * <p>Serve loads GenAI-ready models only (HF/local {@code genai_config.json},
 * including nested packages, or a prior Olive cache). Olive conversion is
 * offline via {@link Olive#resolveOrConvert}; it is not run at startup.
 *
 * @author Haifeng Li
 */
@ConfigMapping(prefix = "smile.chat.oga")
public interface OgaChatConfig {
    /**
     * When {@code false}, skip the OGA fallback (Torch-only chat).
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * Olive {@code --precision} for offline {@link Olive#resolveOrConvert}
     * and cache-key matching. Empty or {@code auto} uses the cascade default
     * ({@code int4} — {@code optimize} does not accept {@code fp8}).
     */
    Optional<String> precision();

    /**
     * Olive output cache root. Empty uses {@code {SMILE_CACHE}/olive}
     * ({@link smile.io.CacheFiles#dir()}). Serve opens cache hits at startup;
     * conversion writes here when run offline.
     */
    @WithName("cache-dir")
    Optional<String> cacheDir();

    /**
     * Olive CLI executable (default {@code olive}) for offline conversion.
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
