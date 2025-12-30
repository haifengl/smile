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
import org.jboss.logging.Logger;
import smile.io.Read;
import smile.model.Model;

@ApplicationScoped
public class InferenceService {
    private static final Logger logger = Logger.getLogger(InferenceService.class);
    private static final String MODEL_PATH = "SMILE_SERVE_MODEL";
    private final Map<String, Model> models = new TreeMap<>();

    // Load your ML model here upon application start
    // The @ApplicationScoped scope ensures the model is loaded once and reused
    public InferenceService() {
        var env = System.getenv(MODEL_PATH);
        var path = Paths.get(env == null ? ".." : env).toAbsolutePath();
        logger.infof("Loading model from '%s'", path);
        if (Files.isRegularFile(path)) {
            loadModel(path);
        } else if (Files.isDirectory(path)) {
            try (Stream<Path> files = Files.list(path)) {
                files.forEach(file -> {
                    if (Files.isRegularFile(file) && file.toString().endsWith(".sml")) {
                        System.out.println("loading");
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

    private void loadModel(Path path) {
        try {
            var obj = Read.object(path);
            if (obj instanceof Model model) {
                String key = model.getProperty(Model.ID, getFileName(path)) + "-"
                        + model.getProperty(Model.VERSION, "1");
                models.put(key, model);
            } else {
                logger.errorf("'%s' is not a valid model", path);
            }
        } catch (Exception ex) {
            logger.errorf(ex, "Failed to load model '%s'", path);
        }
    }

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

    public List<String> models() {
        return new ArrayList<>(models.keySet());
    }

    /**
     * Performs inference using the generic JSON input.
     * @param request The generic input data as a Map.
     * @return The inference result as a Map or custom object.
     */
    public Map<String, Object> predict(InferenceRequest request) {
        Map<String, Object> inputData = request.data;
        // Placeholder for actual ML inference
        // Use a library like ONNX Runtime Java API to feed the data to your model
        String prediction = "Sample_Prediction";
        double confidence = 0.95;

        // Post-process the model's raw output into a generic JSON-friendly response
        return Map.of(
                "prediction", prediction,
                "confidence", confidence,
                "input_received", inputData
        );
    }
}
