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
import smile.data.Tuple;
import smile.data.type.StructType;
import smile.model.*;

/**
 * The inference service provider.
 *
 * @author Haifeng Li
 */
@ApplicationScoped
public class InferenceService {
    private static final Logger logger = Logger.getLogger(InferenceService.class);
    private static final String MODEL_PATH = "SMILE_SERVE_MODEL";
    private final Map<String, Model> models = new TreeMap<>();

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
            if (obj instanceof Model model) {
                String key = model.getTag(Model.ID, getFileName(path)) + "-"
                           + model.getTag(Model.VERSION, "1");
                models.put(key, model);
            } else {
                logger.errorf("'%s' is not a valid model", path);
            }
        } catch (Exception ex) {
            logger.errorf(ex, "Failed to load model '%s'", path);
        }
    }

    /**
     * Returns the file name without extension.
     * @param path the file path.
     * @return the file name without extension.
     */
    private static String getFileName(Path path) {
        Path file = path.getFileName();
        if (file == null) {
            return ""; // Handle cases where the path doesn't have a filename component
        }

        String name = file.toString();
        int lastDotIndex = name.lastIndexOf('.');
        if (lastDotIndex > 0) { // Ensures the file is not a hidden file like ".gitignore" (where pos=0)
            return name.substring(0, lastDotIndex);
        } else {
            return name; // Returns the original name if no extension is found
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
    public Model getModel(String id) throws NotFoundException {
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
        return predict(getModel(model), request);
    }

    /**
     * Performs inference using the generic JSON input.
     * @param model the model.
     * @param request The generic input data as a Map.
     * @return The inference result.
     * @throws BadRequestException if invalid request body.
     */
    public InferenceResponse predict(Model model, Map<String, Object> request) throws BadRequestException {
        var x = json(model.schema(), request);
        Number y = switch (model) {
            case ClassificationModel m -> m.predict(x);
            case RegressionModel m -> m.predict(x);
            default -> 0;
        };
        return new InferenceResponse(y);
    }

    /**
     * Converts a JSON object to a SMILE tuple.
     * @param schema the tuple schema.
     * @param values the JSON object.
     * @return the tuple.
     * @throws BadRequestException if invalid request body.
     */
    public Tuple json(StructType schema, Map<String, Object> values) throws BadRequestException {
        if (values.size() < schema.length()) throw new BadRequestException();
        var row = new Object[schema.length()];
        for (int i = 0; i < row.length; i++) {
            row[i] = values.get(schema.field(i).name());
        }
        return Tuple.of(schema, row);
    }

    /**
     * Converts a CSV line to a SMILE tuple.
     * @param schema the tuple schema.
     * @param values the CSV fields.
     * @return the tuple.
     * @throws BadRequestException if invalid request body.
     */
    public Tuple csv(StructType schema, List<String> values) throws BadRequestException {
        if (values.size() < schema.length()) throw new BadRequestException();

        try {
            var row = new Object[schema.length()];
            for (int i = 0; i < row.length; i++) {
                row[i] = schema.field(i).valueOf(values.get(i));
            }
            return Tuple.of(schema, row);
        } catch (Exception ex) {
            throw new BadRequestException(ex.getMessage());
        }
    }
}
