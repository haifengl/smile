/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile Shell is free software: you can redistribute it and/or modify
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile Shell is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.serve;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.jboss.logging.Logger;
import smile.io.Read;
import smile.model.Model;

/**
 * The inference service provider.
 *
 * @author Haifeng Li
 */
@ApplicationScoped
public class InferenceService {
    private static final Logger logger = Logger.getLogger(InferenceService.class);
    /** The environment variable to locate models. */
    private static final String MODEL_PATH = "SMILE_SERVE_MODEL";
    /** The ML models. */
    private final Map<String, InferenceModel> models = new TreeMap<>();

    /**
     * Load ML models upon application start.
     * The @ApplicationScoped scope ensures the models are loaded once and reused
     */
    public InferenceService() {
        var env = System.getenv(MODEL_PATH);
        var path = Paths.get(env == null ? ".." : env).toAbsolutePath().normalize();
        if (Files.isRegularFile(path)) {
            loadModel(path);
        } else if (Files.isDirectory(path)) {
            try (Stream<Path> files = Files.list(path)) {
                files.forEach(file -> {
                    if (Files.isRegularFile(file) && file.toString().endsWith(".sml")) {
                        loadModel(file);
                    }
                });
            } catch (IOException ex) {
                logger.error(ex);
            }
        } else {
            logger.errorf("'%s' is not a regular file", path);
        }
    }

    /**
     * Loads a model.
     * @param path the model file path.
     */
    private void loadModel(Path path) {
        try {
            logger.infof("Loading model from '%s'", path);
            var obj = Read.object(path);
            if (obj instanceof Model dummy) {
                var model = new InferenceModel(dummy, path);
                models.put(model.id(), model);
            } else {
                logger.errorf("'%s' is not a valid model", path);
            }
        } catch (Exception ex) {
            logger.errorf(ex, "Failed to load model '%s'", path);
        }
    }

    /**
     * Returns the list of models in id-version format.
     * @return the list of models.
     */
    public List<String> models() {
        return new ArrayList<>(models.keySet());
    }

    /**
     * Returns the model instance.
     * @param id the model id.
     * @return the model instance.
     * @throws NotFoundException if model doesn't exist.
     */
    public InferenceModel getModel(String id) throws NotFoundException {
        var model = models.get(id);
        if (model == null) throw new NotFoundException(id);
        return model;
    }

    /**
     * Performs inference using the generic JSON input.
     * @param model the model id.
     * @param request The generic input data as a Map.
     * @return The inference result.
     * @throws BadRequestException if invalid request body.
     */
    public InferenceResponse predict(String model, Map<String, Object> request) throws BadRequestException, NotFoundException {
        return getModel(model).predict(request);
    }
}
