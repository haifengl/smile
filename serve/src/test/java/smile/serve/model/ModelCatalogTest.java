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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ModelCatalog}.
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
}
