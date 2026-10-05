/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package smile.serve.model;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import smile.serve.InferenceServiceConfig;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ModelCatalog}.
 *
 * @author Haifeng Li
 */
@QuarkusTest
public class ModelCatalogTest {

    @Inject
    ModelCatalog modelCatalog;

    @Test
    public void testGivenLoadedModelsWhenCheckedThenNotEmpty() {
        assertFalse(modelCatalog.isEmpty());
        assertFalse(modelCatalog.list().isEmpty());
    }

    @Test
    public void testGivenNoContributorsWhenCheckedThenIsEmpty() {
        ModelCatalog catalog = new ModelCatalog();
        catalog.contributors = instanceOf(List.of());
        // allow-empty keeps onStart from calling Quarkus.asyncExit() in test mode.
        catalog.config = configOf(true);

        assertTrue(catalog.isEmpty());
        assertTrue(catalog.list().isEmpty());
        // Verify onStart executes without exception when catalog is empty
        catalog.onStart(new StartupEvent());
    }

    @Test
    public void testGivenContributorWithModelWhenCheckedThenNotEmpty() {
        ModelCatalog catalog = new ModelCatalog();
        ModelObject model = ModelObject.of("test-model", 1000L, "Acme", "test-kind");
        OpenAiModelContributor contributor = new OpenAiModelContributor() {
            @Override
            public List<ModelObject> listOpenAiModels() {
                return List.of(model);
            }

            @Override
            public Optional<ModelObject> findOpenAiModel(String id, boolean detailed) {
                return id.equals("test-model") ? Optional.of(model) : Optional.empty();
            }
        };

        catalog.contributors = instanceOf(List.of(contributor));

        assertFalse(catalog.isEmpty());
        assertEquals(1, catalog.list().size());
        assertEquals("test-model", catalog.list().getFirst().id());
        assertTrue(catalog.find("test-model", false).isPresent());
        assertFalse(catalog.find("other", false).isPresent());
    }

    // ------------------------------------------------------------------
    // Empty-catalog startup policy (smile.serve.allow-empty)
    //
    // The policy is tested directly rather than through the HTTP surface:
    // Quarkus.asyncExit() does not tear down the server in test mode, so an
    // integration test cannot observe the exit and would pass either way.
    // ------------------------------------------------------------------

    @Test
    public void testExitsWhenEmptyAndNotAllowed() {
        // Then: a standalone serve with no model fails fast.
        assertTrue(ModelCatalog.shouldExitOnEmptyStartup(true, false));
    }

    @Test
    public void testStaysUpWhenEmptyButAllowed() {
        // Then: dynamic-load mode keeps the service alive for later loads.
        assertFalse(ModelCatalog.shouldExitOnEmptyStartup(true, true));
    }

    @Test
    public void testStaysUpWhenModelLoaded() {
        // Then: a loaded model never triggers the exit, regardless of the flag.
        assertFalse(ModelCatalog.shouldExitOnEmptyStartup(false, false));
        assertFalse(ModelCatalog.shouldExitOnEmptyStartup(false, true));
    }

    @SuppressWarnings("unchecked")
    private static Instance<OpenAiModelContributor> instanceOf(List<OpenAiModelContributor> items) {
        return (Instance<OpenAiModelContributor>) Proxy.newProxyInstance(
                Instance.class.getClassLoader(),
                new Class<?>[]{Instance.class},
                (proxy, method, args) -> {
                    if ("iterator".equals(method.getName())) {
                        return items.iterator();
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static InferenceServiceConfig configOf(boolean allowEmpty) {
        return new InferenceServiceConfig() {
            @Override
            public String model() {
                return "";
            }

            @Override
            public boolean allowEmpty() {
                return allowEmpty;
            }
        };
    }
}
