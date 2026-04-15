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
package smile.serve;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import io.quarkus.runtime.Startup;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.jboss.logging.Logger;
import smile.onnx.InferenceSession;
import smile.io.Paths;

/**
 * Application-scoped service that discovers, loads, and manages ONNX models
 * ({@code *.onnx}) at startup.
 *
 * <p>The model path is configured via {@code smile.onnx.model}. It may point
 * to a single {@code .onnx} file or to a directory; in the latter case every
 * {@code .onnx} file in the directory is loaded.
 *
 * <p>If the configured path does not exist or contains no ONNX models this
 * service starts empty and all list/predict requests return 404.
 *
 * @author Haifeng Li
 */
@Startup
@ApplicationScoped
public class OnnxService {
    private static final Logger logger = Logger.getLogger(OnnxService.class);
    /** Loaded models, keyed by model ID. Sorted for stable list order. */
    private final Map<String, OnnxModel> models = Collections.synchronizedSortedMap(new TreeMap<>());

    /**
     * Loads ONNX models upon application start.
     *
     * @param config the ONNX service configuration.
     */
    @Inject
    public OnnxService(OnnxServiceConfig config) {
        var path = Path.of(config.model()).toAbsolutePath().normalize();
        if (Files.isRegularFile(path) && path.toString().endsWith(".onnx")) {
            loadModel(path);
        } else if (Files.isDirectory(path)) {
            try (Stream<Path> files = Files.list(path)) {
                files.filter(f -> Files.isRegularFile(f) && f.toString().endsWith(".onnx"))
                     .forEach(this::loadModel);
            } catch (IOException ex) {
                logger.errorf(ex, "Failed to list ONNX model directory '%s'", path);
            }
        } else {
            logger.infof("ONNX model path '%s' not found or not a .onnx file — ONNX service starting empty.", path);
        }
    }

    /**
     * Loads a single ONNX model file and registers it.
     *
     * @param path the {@code .onnx} file path.
     */
    private void loadModel(Path path) {
        try {
            logger.infof("Loading ONNX model from '%s'", path);
            String id = Paths.getFileName(path);
            var session = InferenceSession.create(path.toString());
            var model = new OnnxModel(id, path, session);
            models.put(id, model);
            logger.infof("ONNX model '%s' loaded successfully (inputs=%s, outputs=%s)",
                    id, session.inputNames(), session.outputNames());
        } catch (Exception ex) {
            logger.errorf(ex, "Failed to load ONNX model from '%s'", path);
        }
    }

    /**
     * Returns the IDs of all loaded ONNX models in alphabetical order.
     *
     * @return the list of model IDs.
     */
    public List<String> models() {
        return new ArrayList<>(models.keySet());
    }

    /**
     * Returns the ONNX model with the given ID.
     *
     * @param id the model ID.
     * @return the model instance.
     * @throws NotFoundException if no model with that ID has been loaded.
     */
    public OnnxModel getModel(String id) throws NotFoundException {
        var model = models.get(id);
        if (model == null) throw new NotFoundException("ONNX model not found: " + id);
        return model;
    }

    /**
     * Runs ONNX inference with JSON-encoded inputs.
     *
     * @param modelId the model ID.
     * @param request JSON object mapping input names to flat numeric arrays.
     * @return JSON object mapping output names to flat numeric arrays.
     * @throws BadRequestException if the request is malformed.
     * @throws NotFoundException   if the model ID is unknown.
     */
    public JsonObject predict(String modelId, JsonObject request)
            throws BadRequestException, NotFoundException {
        return getModel(modelId).predict(request);
    }
}

