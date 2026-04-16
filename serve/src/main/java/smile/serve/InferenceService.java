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
import smile.io.Read;
import smile.model.Model;

/**
 * Application-scoped service that loads and manages serialized SMILE
 * models ({@code *.sml}) and delegates inference requests to them.
 *
 * <p>Models are discovered once at startup from the path configured by
 * {@code smile.serve.model}. The path may point to a single {@code .sml}
 * file or to a directory; in the latter case every {@code .sml} file in
 * the directory is loaded.
 *
 * @author Haifeng Li
 */
@Startup
@ApplicationScoped
public class InferenceService {
    private static final Logger logger = Logger.getLogger(InferenceService.class);
    /** Loaded models, keyed by {@code <id>-<version>}. Sorted for stable list order. */
    private final Map<String, InferenceModel> models = Collections.synchronizedSortedMap(new TreeMap<>());

    /**
     * Loads ML models upon application start.
     * The {@code @ApplicationScoped} scope ensures the models are loaded once and reused.
     *
     * @param config the service configuration.
     */
    @Inject
    public InferenceService(InferenceServiceConfig config) {
        var path = Path.of(config.model()).toAbsolutePath().normalize();
        if (Files.isRegularFile(path)) {
            loadModel(path);
        } else if (Files.isDirectory(path)) {
            try (Stream<Path> files = Files.list(path)) {
                files.filter(file -> Files.isRegularFile(file) && file.toString().endsWith(".sml"))
                     .forEach(this::loadModel);
            } catch (IOException ex) {
                logger.errorf(ex, "Failed to list model directory '%s'", path);
            }
        } else {
            logger.errorf("'%s' is not a regular file or directory", path);
        }
    }

    /**
     * Loads a single model file and registers it by its ID.
     *
     * @param path the model file path.
     */
    private void loadModel(Path path) {
        try {
            logger.infof("Loading model from '%s'", path);
            var obj = Read.object(path);
            if (obj instanceof Model m) {
                var model = new InferenceModel(m, path);
                models.put(model.id(), model);
                logger.infof("Model '%s' loaded successfully", model.id());
            } else {
                logger.errorf("'%s' does not contain a valid SMILE model (got %s)",
                        path, obj == null ? "null" : obj.getClass().getName());
            }
        } catch (Exception ex) {
            logger.errorf(ex, "Failed to load model from '%s'", path);
        }
    }

    /**
     * Returns the list of loaded model IDs in alphabetical order.
     *
     * @return the list of model IDs.
     */
    public List<String> models() {
        return new ArrayList<>(models.keySet());
    }

    /**
     * Returns the model with the given ID.
     *
     * @param id the model ID.
     * @return the model instance.
     * @throws NotFoundException if no model with that ID has been loaded.
     */
    public InferenceModel getModel(String id) throws NotFoundException {
        var model = models.get(id);
        if (model == null) throw new NotFoundException("Model not found: " + id);
        return model;
    }

    /**
     * Performs inference using JSON-encoded input.
     *
     * @param modelId the model ID.
     * @param request the feature values as a JSON object.
     * @return the inference result.
     * @throws BadRequestException if the request body is malformed.
     * @throws NotFoundException   if the model ID is unknown.
     */
    public InferenceResponse predict(String modelId, JsonObject request)
            throws BadRequestException, NotFoundException {
        return getModel(modelId).predict(request);
    }
}
