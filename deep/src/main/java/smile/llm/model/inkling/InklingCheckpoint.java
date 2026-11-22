/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.model.inkling;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import smile.deep.tensor.Device;
import smile.deep.tensor.SafeTensors;
import smile.deep.tensor.Tensor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads tensors of an Inkling-2 checkpoint directory by their SMILE path (the checkpoint name without
 * {@code model.llm.}), from {@code model.safetensors.index.json} or a single {@code model-*.safetensors}.
 *
 * @author Haifeng Li
 */
final class InklingCheckpoint {
    private final Path dir;
    private final Map<String, String> fileOf = new HashMap<>();

    InklingCheckpoint(String dir) throws IOException {
        this.dir = Path.of(dir);
        Path index = this.dir.resolve("model.safetensors.index.json");
        if (Files.exists(index)) {
            JsonNode map = new ObjectMapper().readTree(index.toFile()).get("weight_map");
            map.properties().forEach(e -> fileOf.put(e.getKey(), e.getValue().asString()));
        } else {
            try (var files = Files.list(this.dir)) {
                for (Path f : (Iterable<Path>) files.filter(p -> p.getFileName().toString().startsWith("model")
                        && p.getFileName().toString().endsWith(".safetensors"))::iterator) {
                    for (String name : SafeTensors.listTensors(f.toString())) {
                        fileOf.put(name, f.getFileName().toString());
                    }
                }
            }
        }
    }

    static String hfName(String path) {
        return "model.llm." + path;
    }

    boolean has(String path) {
        return fileOf.containsKey(hfName(path));
    }

    /**
     * Reads tensors onto a device.
     *
     * @param device the device.
     * @param paths  SMILE paths.
     * @return path to tensor.
     */
    Map<String, Tensor> read(Device device, Set<String> paths) throws IOException {
        Map<String, Set<String>> byFile = new HashMap<>();
        for (String p : paths) {
            String file = fileOf.get(hfName(p));
            if (file == null) {
                throw new IOException("tensor not in the checkpoint: " + hfName(p));
            }
            byFile.computeIfAbsent(file, f -> new java.util.HashSet<>()).add(hfName(p));
        }
        Map<String, Tensor> out = new HashMap<>();
        for (var e : byFile.entrySet()) {
            SafeTensors st = SafeTensors.read(dir.resolve(e.getKey()).toString(), device, e.getValue());
            for (var t : st.tensors().entrySet()) {
                out.put(t.getKey().substring("model.llm.".length()), t.getValue());
            }
        }
        return out;
    }

    Tensor get(Device device, String path) throws IOException {
        return read(device, Set.of(path)).get(path);
    }
}
