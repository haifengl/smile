/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package smile.chat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import smile.llm.FinishReason;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link LlmMetrics} meter registration and recording.
 */
public class LlmMetricsTest {

    @Test
    public void testBindAndUnbind() {
        // Given
        var registry = new SimpleMeterRegistry();
        var metrics = new LlmMetrics(registry);
        metrics.start();

        // When
        metrics.bind("Qwen/Qwen3.8-27B", null);

        // Then
        assertEquals("Qwen/Qwen3.8-27B", metrics.modelId());

        // When
        metrics.unbind();

        // Then
        assertEquals(LlmMetrics.UNKNOWN_MODEL, metrics.modelId());
    }

    @Test
    public void testBindBeforeStartIsAppliedAtStart() {
        // Given: a model bound before the application has started.
        var registry = new SimpleMeterRegistry();
        var metrics = new LlmMetrics(registry);
        metrics.bind("Qwen/Qwen3.8-27B", null);

        // Then: nothing is registered yet.
        assertNull(registry.find("serve.llm.prompt_tokens_total").counter());

        // When: the application starts.
        metrics.start();

        // Then: the binding is applied with the bound model id.
        var prompt = registry.find("serve.llm.prompt_tokens_total")
                .tag("model_id", "Qwen/Qwen3.8-27B").counter();
        assertNotNull(prompt);
    }

    @Test
    public void testRecordTokensAndDurations() {
        // Given
        var registry = new SimpleMeterRegistry();
        var metrics = new LlmMetrics(registry);
        metrics.start();
        metrics.bind("test-model", null);

        // When
        metrics.recordPromptTokens(128);
        metrics.recordGenerationTokens(64);
        metrics.recordSuccess(FinishReason.stop);
        metrics.recordDuration("time_to_first_token", 5_000_000L);

        // Then
        var prompt = registry.find("serve.llm.prompt_tokens_total").counter();
        assertNotNull(prompt);
        assertEquals(128.0, prompt.count());

        var generation = registry.find("serve.llm.generation_tokens_total").counter();
        assertNotNull(generation);
        assertEquals(64.0, generation.count());

        var success = registry.find("serve.llm.request_success_total")
                .tag("finished_reason", "stop").counter();
        assertNotNull(success);
        assertEquals(1.0, success.count());

        var ttft = registry.find("serve.llm.time_to_first_token").timer();
        assertNotNull(ttft);
        assertEquals(1, ttft.count());
    }

    @Test
    public void testUnbindRemovesModelMeters() {
        // Given
        var registry = new SimpleMeterRegistry();
        var metrics = new LlmMetrics(registry);
        metrics.start();
        metrics.bind("test-model", null);
        metrics.recordPromptTokens(10);

        // When
        metrics.unbind();

        // Then: the model's series are gone, and the default series is restored.
        assertNull(registry.find("serve.llm.prompt_tokens_total")
                .tag("model_id", "test-model").counter());
        assertNotNull(registry.find("serve.llm.prompt_tokens_total")
                .tag("model_id", LlmMetrics.UNKNOWN_MODEL).counter());
    }
}
