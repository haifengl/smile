/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package smile.serve;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link ModelMetricsBinder} registration lifecycle.
 */
public class ModelMetricsBinderTest {

    @Test
    public void testRegisterBeforeStartIsAppliedAtStart() {
        // Given: a model registered before the application has started.
        var registry = new SimpleMeterRegistry();
        var binder = new ModelMetricsBinder(registry);
        binder.register(ModelMetricsBinder.SMILE_NAMESPACE, "iris-1", new ModelMetrics());

        // Then: nothing is registered yet.
        assertNull(registry.find("serve.smile.requests").functionCounter());

        // When: the application starts.
        binder.start();

        // Then: the buffered registration is applied.
        assertNotNull(registry.find("serve.smile.requests")
                .tag("model_id", "iris-1").functionCounter());
    }

    @Test
    public void testUnregisterBeforeStartDropsPending() {
        // Given
        var registry = new SimpleMeterRegistry();
        var binder = new ModelMetricsBinder(registry);
        binder.register(ModelMetricsBinder.ONNX_NAMESPACE, "squeezenet", new ModelMetrics());

        // When: the model is unloaded before startup.
        binder.unregister(ModelMetricsBinder.ONNX_NAMESPACE, "squeezenet");
        binder.start();

        // Then: it is not registered.
        assertNull(registry.find("serve.onnx.requests").functionCounter());
    }

    @Test
    public void testRegisterAfterStartIsImmediate() {
        // Given
        var registry = new SimpleMeterRegistry();
        var binder = new ModelMetricsBinder(registry);
        binder.start();

        // When
        binder.register(ModelMetricsBinder.SMILE_NAMESPACE, "iris-1", new ModelMetrics());

        // Then
        assertNotNull(registry.find("serve.smile.requests")
                .tag("model_id", "iris-1").functionCounter());
    }

    @Test
    public void testUnregisterRemovesMeters() {
        // Given
        var registry = new SimpleMeterRegistry();
        var binder = new ModelMetricsBinder(registry);
        binder.start();
        binder.register(ModelMetricsBinder.SMILE_NAMESPACE, "iris-1", new ModelMetrics());

        // When
        binder.unregister(ModelMetricsBinder.SMILE_NAMESPACE, "iris-1");

        // Then
        assertNull(registry.find("serve.smile.requests").functionCounter());
    }
}
