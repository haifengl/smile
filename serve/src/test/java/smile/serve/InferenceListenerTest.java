/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * SMILE Serve is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with SMILE Serve. If not, see
 * <https://www.gnu.org/licenses/>.
 */
package smile.serve;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the {@link InferenceListener} SPI across {@link InferenceService}
 * and {@link OnnxService}.
 *
 * @author Haifeng Li
 */
@QuarkusTest
public class InferenceListenerTest {

    @ApplicationScoped
    public static class RecordingListener implements InferenceListener {
        private final List<InferenceEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public void onEvent(InferenceEvent event) {
            events.add(event);
        }

        public List<InferenceEvent> events() {
            return events;
        }
    }

    @Inject
    InferenceService inferenceService;

    @Inject
    RecordingListener recordingListener;

    @Test
    public void testGivenRegisteredListenerWhenPredictionRunsThenReceivesInferenceEvent() {
        // Given a clean event buffer
        int before = recordingListener.events().size();

        // When running a SMILE prediction with explanations
        var req = new JsonObject()
                .put("petallength", 5.1)
                .put("petalwidth", 3.5)
                .put("sepallength", 1.4)
                .put("sepalwidth", 0.2);
        var pred = inferenceService.predict("iris_random_forest-1", req, true);
        assertNotNull(pred);

        // Then RecordingListener received the PREDICT event with explanations
        assertTrue(recordingListener.events().size() > before);
        InferenceEvent last = recordingListener.events().getLast();
        assertEquals(ModelMetricsBinder.SMILE_NAMESPACE, last.namespace());
        assertEquals("iris_random_forest-1", last.modelId());
        assertEquals(InferenceEvent.Action.PREDICT, last.action());
        assertTrue(last.explain());
        assertTrue(last.success());
        assertNotNull(last.prediction());
    }
}
